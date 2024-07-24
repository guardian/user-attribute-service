package models
import com.gu.i18n.Country
import com.gu.memsub.ProductRatePlanChargeProductType.PaperDay
import com.gu.memsub._
import com.gu.memsub.subsv2._
import com.gu.monitoring.SafeLogger.LogPrefix
import com.gu.monitoring.SafeLogging
import com.gu.services.model.PaymentDetails
import json.localDateWrites
import org.joda.time.LocalDate
import org.joda.time.LocalDate.now
import play.api.libs.json._

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import scala.annotation.tailrec

case class AccountDetails(
    contactId: String,
    regNumber: Option[String],
    email: Option[String],
    deliveryAddress: Option[DeliveryAddress],
    subscription: Subscription,
    paymentDetails: PaymentDetails,
    billingCountry: Option[Country],
    stripePublicKey: String,
    accountHasMissedRecentPayments: Boolean,
    safeToUpdatePaymentMethod: Boolean,
    isAutoRenew: Boolean,
    alertText: Option[String],
    accountId: String,
    cancellationEffectiveDate: Option[String],
)

object AccountDetails {

  implicit class ResultLike(accountDetails: AccountDetails) extends SafeLogging {

    import accountDetails._

    def toJson(catalog: Catalog)(implicit logPrefix: LogPrefix): JsObject = {

      val product = accountDetails.subscription.plan(catalog).product(catalog)

      val mmaCategory = mmaCategoryFrom(product)

      val paymentMethod = paymentDetails.paymentMethod match {
        case Some(payPal: PayPalMethod) =>
          Json.obj(
            "paymentMethod" -> "PayPal",
            "payPalEmail" -> payPal.email,
          )
        case Some(card: PaymentCard) =>
          Json.obj(
            "paymentMethod" -> "Card",
            "card" -> {
              Json.obj(
                "last4" -> card.paymentCardDetails.map(_.lastFourDigits).getOrElse[String]("••••"),
                "expiry" -> card.paymentCardDetails.map(cardDetails =>
                  Json.obj(
                    "month" -> cardDetails.expiryMonth,
                    "year" -> cardDetails.expiryYear,
                  ),
                ),
                "type" -> card.cardType.getOrElse[String]("unknown"),
                "stripePublicKeyForUpdate" -> stripePublicKey,
                "email" -> email,
              )
            },
          )
        case Some(dd: GoCardless) =>
          Json.obj(
            "paymentMethod" -> "DirectDebit",
            "account" -> Json.obj( // DEPRECATED
              "accountName" -> dd.accountName,
            ),
            "mandate" -> Json.obj(
              "accountName" -> dd.accountName,
              "accountNumber" -> dd.accountNumber,
              "sortCode" -> dd.sortCode,
            ),
          )
        case Some(sepa: Sepa) =>
          Json.obj(
            "paymentMethod" -> "Sepa",
            "sepaMandate" -> Json.obj(
              "accountName" -> sepa.accountName,
              "iban" -> sepa.accountNumber,
            ),
          )
        case _ if accountHasMissedRecentPayments && safeToUpdatePaymentMethod =>
          Json.obj(
            "paymentMethod" -> "ResetRequired",
            "stripePublicKeyForCardAddition" -> stripePublicKey,
          )
        case _ => Json.obj()
      }

      def externalisePlanName(plan: RatePlan): Option[String] = plan.product(catalog) match {
        case _: Product.Weekly => if (plan.name(catalog).contains("Six for Six")) Some("currently on '6 for 6'") else None
        case _: Product.Paper => Some(plan.name(catalog).replace("+", " plus Digital Subscription"))
        case _ => None
      }

      def maybePaperDaysOfWeek(plan: RatePlan) = {
        val dayIndexes = for {
          charge <- plan.ratePlanCharges.list.toList
            .filterNot(_.pricing.isFree) // note 'Echo Legacy' rate plan has all days of week but some are zero price, this filters those out
          catalogZuoraPlan <- catalog.productRatePlans.get(plan.productRatePlanId)
          dayName <- catalogZuoraPlan.productRatePlanCharges
            .get(charge.productRatePlanChargeId)
            .collect { case benefit: PaperDay => benefit.dayOfTheWeekIndex }
        } yield dayName

        val dayNames = dayIndexes.sorted.map(DayOfWeek.of(_).getDisplayName(TextStyle.FULL, Locale.ENGLISH))

        if (dayNames.nonEmpty) Json.obj("daysOfWeek" -> dayNames) else Json.obj()
      }

      def jsonifyPlan(plan: RatePlan) = Json.obj(
        "name" -> externalisePlanName(plan),
        "start" -> plan.start,
        "end" -> plan.end,
        // if the customer acceptance date is future dated (e.g. 6for6) then always display, otherwise only show if starting less than 30 days from today
        "shouldBeVisible" -> (subscription.acceptanceDate.isAfter(now) || plan.start.isBefore(now.plusDays(30))),
        "chargedThrough" -> plan.chargedThroughDate,
        "price" -> (plan.chargesPrice.prices.head.amount * 100).toInt,
        "currency" -> plan.chargesPrice.prices.head.currency.glyph,
        "currencyISO" -> plan.chargesPrice.prices.head.currency.iso,
        "billingPeriod" -> (plan.billingPeriod
          .leftMap(e => logger.warn("unknown billing period: " + e))
          .map(_.noun)
          .getOrElse("unknown_billing_period"): String),
        "features" -> plan.features.map(_.featureCode).mkString(","),
      ) ++ maybePaperDaysOfWeek(plan)

      val sortedPlans = subscription.ratePlans.sortBy(_.start.toDate)
      val currentPlans = sortedPlans.filter(plan => !plan.start.isAfter(now) && plan.end.isAfter(now))
      val futurePlans = sortedPlans.filter(plan => plan.start.isAfter(now))

      val startDate: LocalDate = sortedPlans.headOption.map(_.start).getOrElse(paymentDetails.customerAcceptanceDate)
      val endDate: LocalDate = sortedPlans.headOption.map(_.end).getOrElse(paymentDetails.termEndDate)

      if (currentPlans.length > 1) logger.warn(s"More than one 'current plan' on sub with id: ${subscription.id}")

      val selfServiceCancellation = SelfServiceCancellation(product, billingCountry)

      Json.obj(
        "mmaCategory" -> mmaCategory,
        "tier" -> paymentDetails.plan.name,
        "isPaidTier" -> (paymentDetails.plan.price.amount > 0f),
        "selfServiceCancellation" -> Json.obj(
          "isAllowed" -> selfServiceCancellation.isAllowed,
          "shouldDisplayEmail" -> selfServiceCancellation.shouldDisplayEmail,
          "phoneRegionsToDisplay" -> selfServiceCancellation.phoneRegionsToDisplay,
        ),
      ) ++
        regNumber.fold(Json.obj())({ reg => Json.obj("regNumber" -> reg) }) ++
        billingCountry.fold(Json.obj())({ bc => Json.obj("billingCountry" -> bc.name) }) ++
        Json.obj(
          "joinDate" -> paymentDetails.startDate,
          "optIn" -> !paymentDetails.pendingCancellation,
          "subscription" -> (paymentMethod ++ Json.obj(
            "contactId" -> accountDetails.contactId,
            "deliveryAddress" -> accountDetails.deliveryAddress,
            "safeToUpdatePaymentMethod" -> safeToUpdatePaymentMethod,
            "start" -> startDate,
            "end" -> endDate,
            "nextPaymentPrice" -> paymentDetails.nextPaymentPrice,
            "nextPaymentDate" -> paymentDetails.nextPaymentDate,
            "potentialCancellationDate" -> paymentDetails.nextInvoiceDate,
            "lastPaymentDate" -> paymentDetails.lastPaymentDate,
            "chargedThroughDate" -> paymentDetails.chargedThroughDate,
            "renewalDate" -> paymentDetails.termEndDate,
            "anniversaryDate" -> anniversary(startDate),
            "cancelledAt" -> paymentDetails.pendingCancellation,
            "subscriptionId" -> paymentDetails.subscriberId,
            "trialLength" -> paymentDetails.remainingTrialLength,
            "autoRenew" -> isAutoRenew,
            "plan" -> Json.obj( // TODO remove once nothing is using this key (same time as removing old deprecated endpoints)
              "name" -> paymentDetails.plan.name,
              "price" -> (paymentDetails.plan.price.amount * 100).toInt,
              "currency" -> paymentDetails.plan.price.currency.glyph,
              "currencyISO" -> paymentDetails.plan.price.currency.iso,
              "billingPeriod" -> paymentDetails.plan.interval.mkString,
            ),
            "currentPlans" -> currentPlans.map(jsonifyPlan),
            "futurePlans" -> futurePlans.map(jsonifyPlan),
            "readerType" -> accountDetails.subscription.readerType.value,
            "accountId" -> accountDetails.accountId,
            "cancellationEffectiveDate" -> cancellationEffectiveDate,
          )),
        ) ++ alertText.map(text => Json.obj("alertText" -> text)).getOrElse(Json.obj())

    }
  }

  /** Note this is a different concept than termEndDate because termEndDate could be many years in the future. termEndDate models when Zuora will
    * renew the subscription whilst anniversary indicates when another year will have passed since user started their subscription.
    *
    * @param start
    *   beginning of subscription timeline, perhaps customerAcceptanceDate
    * @param today
    *   where we are on the timeline today
    * @return
    *   next anniversary date of the subscription
    */
  def anniversary(
      start: LocalDate,
      today: LocalDate = LocalDate.now(),
  ): LocalDate = {
    @tailrec def loop(current: LocalDate): LocalDate = {
      val next = current.plusYears(1)
      if (today.isBefore(next)) next
      else loop(next)
    }
    loop(start)
  }
  def mmaCategoryFrom(product: Product): String = product match {
    case _: Product.Paper => "subscriptions" // Paper includes GW 🤦‍
    case _: Product.ZDigipack => "subscriptions"
    case _: Product.SupporterPlus => "recurringSupport"
    case _: Product.TierThree => "recurringSupport"
    case _: Product.GuardianPatron => "subscriptions"
    case _: Product.Contribution => "recurringSupport"
    case _: Product.Membership => "membership"
    case _ => product.name // fallback
  }
}

object CancelledSubscription {
  import AccountDetails._
  def apply(subscription: Subscription, catalog: Catalog): JsObject = {
    GetCurrentPlans
      .bestCancelledPlan(subscription)
      .map { plan =>
        Json.obj(
          "mmaCategory" -> mmaCategoryFrom(plan.product(catalog)),
          "tier" -> plan.productName,
          "subscription" -> Json.obj(
            "subscriptionId" -> subscription.name.get,
            "cancellationEffectiveDate" -> subscription.termEndDate,
            "start" -> subscription.acceptanceDate,
            "end" -> Seq(subscription.termEndDate, subscription.acceptanceDate).max,
            "readerType" -> subscription.readerType.value,
            "accountId" -> subscription.accountId.get,
          ),
        )
      }
      .getOrElse(Json.obj())
  }
}
