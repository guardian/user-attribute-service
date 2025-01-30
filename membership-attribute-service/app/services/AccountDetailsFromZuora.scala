package services

import com.gu.memsub.Product
import com.gu.memsub.Subscription.SubscriptionNumber
import com.gu.memsub.subsv2.{Catalog, Subscription}
import com.gu.memsub.subsv2.services.SubscriptionService
import com.gu.monitoring.SafeLogger.LogPrefix
import com.gu.salesforce.Contact
import com.gu.services.model.PaymentDetails
import controllers.AccountController
import controllers.AccountHelpers.{FilterByProductType, FilterBySubName, NoFilter, OptionalSubscriptionsFilter}
import models.{AccountDetails, ContactAndSubscription, DeliveryAddress}
import monitoring.CreateMetrics
import scalaz.ListT
import scalaz.std.scalaFuture._
import services.PaymentFailureAlerter.{accountHasMissedPayments, alertText, safeToAllowPaymentUpdate}
import services.salesforce.ContactRepository
import services.stripe.ChooseStripe
import services.zuora.rest.ZuoraRestService
import services.zuora.rest.ZuoraRestService.PaymentMethodId
import utils.ListTEither.ListTEither
import utils.SimpleEitherT.SimpleEitherT
import utils.{ListTEither, SimpleEitherT}

import scala.concurrent.{ExecutionContext, Future}

class AccountDetailsFromZuora(
    createMetrics: CreateMetrics,
    zuoraRestService: ZuoraRestService,
    contactRepository: ContactRepository,
    subscriptionService: SubscriptionService[Future],
    chooseStripe: ChooseStripe,
    paymentDetailsForSubscription: PaymentDetailsForSubscription,
    futureCatalog: LogPrefix => Future[Catalog],
)(implicit executionContext: ExecutionContext) {
  private val metrics = createMetrics.forService(classOf[AccountController])

  def fetch(userId: String, filter: OptionalSubscriptionsFilter)(implicit logPrefix: LogPrefix): SimpleEitherT[List[AccountDetails]] = {
    metrics.measureDurationEither("accountDetailsFromZuora") {
      SimpleEitherT.fromListT(accountDetailsFromZuoraFor(userId, filter))
    }
  }

  private def accountDetailsFromZuoraFor(userId: String, filter: OptionalSubscriptionsFilter)(implicit
      logPrefix: LogPrefix,
  ): ListT[SimpleEitherT, AccountDetails] = {
    for {
      catalog <- ListTEither.singleRightT(futureCatalog(logPrefix))
      contactAndSubscription <- allCurrentSubscriptions(userId, filter)
      detailsResultsTriple <- ListTEither.single(getAccountDetailsParallel(contactAndSubscription))
      (paymentDetails, accountSummary, effectiveCancellationDate) = detailsResultsTriple
      country = accountSummary.billToContact.country
      stripePublicKey = chooseStripe.publicKeyForCountry(country)
      alertText <- ListTEither.singleRightT(alertText(accountSummary, contactAndSubscription.subscription, getPaymentMethod, catalog))
      isAutoRenew = contactAndSubscription.subscription.autoRenew
    } yield {
      AccountDetails(
        contactId = contactAndSubscription.contact.salesforceContactId,
        regNumber = None,
        email = accountSummary.billToContact.email,
        deliveryAddress = Some(DeliveryAddress.fromContact(contactAndSubscription.contact)),
        subscription = contactAndSubscription.subscription,
        paymentDetails = paymentDetails,
        billingCountry = accountSummary.billToContact.country,
        stripePublicKey = stripePublicKey.key,
        accountHasMissedRecentPayments =
          accountHasMissedPayments(contactAndSubscription.subscription.accountId, accountSummary.invoices, accountSummary.payments),
        safeToUpdatePaymentMethod = safeToAllowPaymentUpdate(contactAndSubscription.subscription.accountId, accountSummary.invoices),
        isAutoRenew = isAutoRenew,
        alertText = alertText,
        accountId = accountSummary.id.get,
        cancellationEffectiveDate = effectiveCancellationDate,
      )
    }
  }

  private def getPaymentMethod(id: PaymentMethodId)(implicit logPrefix: LogPrefix): Future[Either[String, ZuoraRestService.PaymentMethodResponse]] =
    zuoraRestService.getPaymentMethod(id.get).map(_.toEither)

  private def nonGiftContactAndSubscriptionsFor(contact: Contact)(implicit logPrefix: LogPrefix): Future[List[ContactAndSubscription]] = {
    subscriptionService
      .current(contact)
      .map(_.map(ContactAndSubscription(contact, _, isGiftRedemption = false)))
  }

  private def applyFilter(
      filter: OptionalSubscriptionsFilter,
      contactAndSubscriptions: List[ContactAndSubscription],
      catalog: Catalog,
  ): List[ContactAndSubscription] = {
    filter match {
      case FilterBySubName(subscriptionName) =>
        contactAndSubscriptions.find(_.subscription.subscriptionNumber == subscriptionName).toList
      case FilterByProductType(productType) =>
        contactAndSubscriptions.filter(contactAndSubscription =>
          productIsInstanceOfProductType(
            contactAndSubscription.subscription.plan(catalog).product(catalog),
            productType,
          ),
        )
      case NoFilter =>
        contactAndSubscriptions
    }
  }

  private def subscriptionsFor(userId: String, contact: Contact, filter: OptionalSubscriptionsFilter)(implicit
      logPrefix: LogPrefix,
  ): SimpleEitherT[List[ContactAndSubscription]] = {
    for {
      nonGiftContactAndSubscriptions <- SimpleEitherT.rightT(nonGiftContactAndSubscriptionsFor(contact))
      contactAndSubscriptions <- checkForGiftSubscription(userId, nonGiftContactAndSubscriptions, contact)
      catalog <- SimpleEitherT.rightT(futureCatalog(logPrefix))
      subsWithRecognisedProducts = contactAndSubscriptions.filter(_.subscription.plan(catalog).product(catalog) match {
        case _: Product.ContentSubscription => true
        case Product.UnknownProduct => false
        case Product.Membership => true
        case Product.GuardianPatron => true
        case Product.Contribution => true
        case Product.Discounts => false
        case Product.GuardianAdLite => true
      })
      filtered = applyFilter(filter, subsWithRecognisedProducts, catalog)
    } yield filtered
  }

  private def allCurrentSubscriptions(
      userId: String,
      filter: OptionalSubscriptionsFilter,
  )(implicit logPrefix: LogPrefix): ListTEither[ContactAndSubscription] = {
    for {
      contact <- ListTEither.fromFutureOption(contactRepository.get(userId))
      subscription <- ListTEither.fromEitherT(subscriptionsFor(userId, contact, filter))
    } yield subscription
  }

  private def getAccountDetailsParallel(
      contactAndSubscription: ContactAndSubscription,
  )(implicit logPrefix: LogPrefix): SimpleEitherT[(PaymentDetails, ZuoraRestService.AccountSummary, Option[String])] = {
    metrics.measureDurationEither("getAccountDetailsParallel") {
      // Run all these api calls in parallel to improve response times
      val paymentDetailsFuture =
        paymentDetailsForSubscription
          .getPaymentDetails(contactAndSubscription)
          .map(Right(_))
          .recover { case x =>
            Left(s"error retrieving payment details for subscription: ${contactAndSubscription.subscription.subscriptionNumber}. Reason: $x")
          }

      val accountSummaryFuture =
        zuoraRestService
          .getAccount(contactAndSubscription.subscription.accountId)
          .map(_.toEither)
          .recover { case x =>
            Left(
              s"error receiving account summary for subscription: ${contactAndSubscription.subscription.subscriptionNumber} " +
                s"with account id ${contactAndSubscription.subscription.accountId}. Reason: $x",
            )
          }

      val effectiveCancellationDateFuture =
        zuoraRestService
          .getCancellationEffectiveDate(contactAndSubscription.subscription.subscriptionNumber)
          .map(_.toEither)
          .recover { case x =>
            Left(
              s"Failed to fetch effective cancellation date: ${contactAndSubscription.subscription.subscriptionNumber} " +
                s"with account id ${contactAndSubscription.subscription.accountId}. Reason: $x",
            )
          }

      for {
        paymentDetails <- SimpleEitherT(paymentDetailsFuture)
        accountSummary <- SimpleEitherT(accountSummaryFuture)
        effectiveCancellationDate <- SimpleEitherT(effectiveCancellationDateFuture)
      } yield (paymentDetails, accountSummary, effectiveCancellationDate)
    }
  }

  private def checkForGiftSubscription(
      userId: String,
      nonGiftSubscription: List[ContactAndSubscription],
      contact: Contact,
  )(implicit logPrefix: LogPrefix): SimpleEitherT[List[ContactAndSubscription]] = {
    metrics.measureDurationEither("checkForGiftSubscription") {
      for {
        records <- SimpleEitherT(zuoraRestService.getGiftSubscriptionRecordsFromIdentityId(userId))
        reused <- reuseAlreadyFetchedSubscriptionIfAvailable(records, nonGiftSubscription)
        contactAndSubscriptions = reused.map(ContactAndSubscription(contact, _, isGiftRedemption = true))
      } yield contactAndSubscriptions ++ nonGiftSubscription
    }
  }

  private def reuseAlreadyFetchedSubscriptionIfAvailable(
      giftRecords: List[ZuoraRestService.GiftSubscriptionsFromIdentityIdRecord],
      nonGiftSubs: List[ContactAndSubscription],
  )(implicit logPrefix: LogPrefix): SimpleEitherT[List[Subscription]] = {
    val all = giftRecords.map { giftRecord =>
      val subscriptionNumber = SubscriptionNumber(giftRecord.Name)
      // If the current user is both the gifter and the giftee we will have already retrieved their
      // subscription so we can reuse it and avoid a call to Zuora
      val matchingSubscription: Option[ContactAndSubscription] = nonGiftSubs.find(_.subscription.subscriptionNumber == subscriptionNumber)
      matchingSubscription
        .map(contactAndSubscription => Future.successful(Some(contactAndSubscription.subscription)))
        .getOrElse(subscriptionService.get(subscriptionNumber, isActiveToday = false))
    }
    val result: Future[List[Subscription]] = Future.sequence(all).map(_.flatten)
    SimpleEitherT.rightT(result) // failures turn to None, and are logged, so just ignore them
  }

  private def productIsInstanceOfProductType(product: Product, requestedProductType: String) = {
    val requestedProductTypeIsContentSubscription: Boolean = requestedProductType == "ContentSubscription"
    product match {
      // this ordering prevents Weekly subs from coming back when Paper is requested (which is different from the type hierarchy where Weekly extends Paper)
      case _: Product.Weekly => requestedProductType == "Weekly" || requestedProductTypeIsContentSubscription
      case Product.Voucher => requestedProductType == "Voucher" || requestedProductType == "Paper" || requestedProductTypeIsContentSubscription
      case Product.DigitalVoucher =>
        requestedProductType == "DigitalVoucher" || requestedProductType == "Paper" || requestedProductTypeIsContentSubscription
      case Product.Delivery =>
        requestedProductType == "HomeDelivery" || requestedProductType == "Paper" || requestedProductTypeIsContentSubscription
      case Product.NationalDelivery =>
        requestedProductType == "HomeDelivery" || requestedProductType == "Paper" || requestedProductTypeIsContentSubscription
      case Product.Contribution => requestedProductType == "Contribution"
      case Product.Membership => requestedProductType == "Membership"
      case Product.Digipack => requestedProductType == "Digipack" || requestedProductTypeIsContentSubscription
      case Product.SupporterPlus => requestedProductType == "SupporterPlus" || requestedProductTypeIsContentSubscription
      case Product.TierThree => requestedProductType == "TierThree" || requestedProductTypeIsContentSubscription
      case Product.GuardianAdLite => requestedProductType == "GuardianAdLite" || requestedProductTypeIsContentSubscription
      case _ => requestedProductType == product.name // fallback
    }
  }
}
