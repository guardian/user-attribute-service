package models

import com.gu.i18n.Currency.GBP
import com.gu.memsub.Subscription._
import com.gu.memsub.subsv2.ReaderType.Direct
import com.gu.memsub.subsv2.services.TestCatalog
import com.gu.memsub.subsv2.services.TestCatalog.{ProductRatePlanChargeIds, catalog}
import com.gu.memsub.subsv2.{RatePlan, RatePlanCharge, Subscription, SubscriptionEnd, ZMonth}
import com.gu.memsub.{PaymentCard, PaymentCardDetails, Price, PricingSummary}
import com.gu.monitoring.SafeLogging
import com.gu.services.model.PaymentDetails
import com.gu.services.model.PaymentDetails.PersonalPlan
import org.joda.time.LocalDate
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import scalaz.NonEmptyList
import utils.TestLogPrefix.testLogPrefix

class ProductsResponseSpec extends Specification with SafeLogging {

  "ProductResponse Json serialisation" should {
    "return a descriptive name in the current plan name field for supporter plus & Guardian Weekly T3 subscriptions" in {
      val accountDetails = AccountDetails(
        contactId = "003UD000009h9KgYAI",
        regNumber = None,
        email = Some("test@thegulocal.com"),
        deliveryAddress = Some(DeliveryAddress(Some("A"), Some("A"), Some("A"), None, Some("SE1 4PU"), Some("United Kingdom"), None, None)),
        subscription = Subscription(
          id = Id("8ad09be48f7af173018f7bd22da22685"),
          subscriptionNumber = SubscriptionNumber("A-S00889289"),
          accountId = AccountId("8ad09be48f7af173018f7bd22d3e2670"),
          contractEffectiveDate = LocalDate.parse("2024-05-15"),
          customerAcceptanceDate = LocalDate.parse("2024-05-15"),
          termEndDate = LocalDate.parse("2024-05-15"),
          isCancelled = false,
          ratePlans = List(
            RatePlan(
              id = RatePlanId("8ad09be48f7af173018f7bd22db4268e"),
              productRatePlanId = TestCatalog.tierThreePrpId,
              productName = "Supporter Plus",
              lastChangeType = None,
              features = List(),
              ratePlanCharges = NonEmptyList(
                RatePlanCharge(
                  SubscriptionRatePlanChargeId("lklklk"),
                  ProductRatePlanChargeIds.tierThreeDigitalId,
                  PricingSummary(Map(GBP -> Price(10.0f, GBP))),
                  Some(ZMonth),
                  None,
                  SubscriptionEnd,
                  None,
                  None,
                  chargedThroughDate = Some(LocalDate.parse("2024-06-15")),
                  effectiveStartDate = LocalDate.parse("2024-05-15"),
                  effectiveEndDate = LocalDate.parse("2025-05-15"),
                ),
                RatePlanCharge(
                  SubscriptionRatePlanChargeId("sdfff"),
                  ProductRatePlanChargeIds.tierThreeGWId,
                  PricingSummary(Map(GBP -> Price(15.0f, GBP))),
                  Some(ZMonth),
                  None,
                  SubscriptionEnd,
                  None,
                  None,
                  chargedThroughDate = Some(LocalDate.parse("2024-06-15")),
                  effectiveStartDate = LocalDate.parse("2024-05-15"),
                  effectiveEndDate = LocalDate.parse("2025-05-15"),
                ),
              ),
            ),
          ),
          readerType = Direct,
          autoRenew = true,
        ),
        paymentDetails = PaymentDetails(
          subscriberId = "A-S00889289",
          startDate = LocalDate.parse("2024-05-15"),
          customerAcceptanceDate = LocalDate.parse("2024-05-15"),
          chargedThroughDate = Some(LocalDate.parse("2024-06-15")),
          termEndDate = LocalDate.parse("2025-05-15"),
          nextPaymentPrice = Some(2500),
          lastPaymentDate = Some(LocalDate.parse("2024-05-15")),
          nextPaymentDate = Some(LocalDate.parse("2024-06-15")),
          nextInvoiceDate = Some(LocalDate.parse("2024-06-15")),
          remainingTrialLength = -19,
          pendingCancellation = false,
          paymentMethod = Some(
            PaymentCard(
              isReferenceTransaction = true,
              cardType = Some("Visa"),
              paymentCardDetails = Some(PaymentCardDetails("4242", 2, 2029)),
              numConsecutiveFailures = Some(0),
              paymentMethodStatus = Some("Active"),
            ),
          ),
          plan = PersonalPlan("Supporter Plus", Price(25.0f, GBP), "month"),
        ),
        billingCountry = Some(com.gu.i18n.Country.UK),
        stripePublicKey = "pk_test_Qm3CGRdrV4WfGYCpm0sftR0f",
        accountHasMissedRecentPayments = false,
        safeToUpdatePaymentMethod = true,
        isAutoRenew = true,
        alertText = None,
        accountId = "8ad09be48f7af173018f7bd22d3e2670",
        cancellationEffectiveDate = None,
      )
      val expectedResponse = Json.parse("""
        |{
        |  "user" : {
        |    "email" : "test@thegulocal.com"
        |  },
        |  "products" : [ {
        |    "mmaCategory" : "recurringSupport",
        |    "tier" : "Supporter Plus",
        |    "isPaidTier" : true,
        |    "selfServiceCancellation" : {
        |      "isAllowed" : true,
        |      "shouldDisplayEmail" : true,
        |      "phoneRegionsToDisplay" : [ "UK & ROW", "US", "AUS" ]
        |    },
        |    "billingCountry" : "United Kingdom",
        |    "joinDate" : "2024-05-15",
        |    "optIn" : true,
        |    "subscription" : {
        |      "paymentMethod" : "Card",
        |      "card" : {
        |        "last4" : "4242",
        |        "expiry" : {
        |          "month" : 2,
        |          "year" : 2029
        |        },
        |        "type" : "Visa",
        |        "stripePublicKeyForUpdate" : "pk_test_Qm3CGRdrV4WfGYCpm0sftR0f",
        |        "email" : "test@thegulocal.com"
        |      },
        |      "contactId" : "003UD000009h9KgYAI",
        |      "deliveryAddress" : {
        |        "addressLine1" : "A",
        |        "addressLine2" : "A",
        |        "town" : "A",
        |        "postcode" : "SE1 4PU",
        |        "country" : "United Kingdom"
        |      },
        |      "safeToUpdatePaymentMethod" : true,
        |      "start" : "2024-05-15",
        |      "end" : "2025-05-15",
        |      "nextPaymentPrice" : 2500,
        |      "nextPaymentDate" : "2024-06-15",
        |      "potentialCancellationDate" : "2024-06-15",
        |      "lastPaymentDate" : "2024-05-15",
        |      "chargedThroughDate" : "2024-06-15",
        |      "renewalDate" : "2025-05-15",
        |      "anniversaryDate" : "2025-05-15",
        |      "cancelledAt" : false,
        |      "subscriptionId" : "A-S00889289",
        |      "trialLength" : -19,
        |      "autoRenew" : true,
        |      "plan" : {
        |        "name" : "Supporter Plus",
        |        "price" : 2500,
        |        "currency" : "£",
        |        "currencyISO" : "GBP",
        |        "billingPeriod" : "month"
        |      },
        |      "currentPlans" : [ {
        |        "name" : null,
        |        "start" : "2024-05-15",
        |        "end" : "2025-05-15",
        |        "shouldBeVisible" : true,
        |        "chargedThrough" : "2024-06-15",
        |        "price" : 2500,
        |        "currency" : "£",
        |        "currencyISO" : "GBP",
        |        "billingPeriod" : "month",
        |        "features" : ""
        |      } ],
        |      "futurePlans" : [ ],
        |      "readerType" : "Direct",
        |      "accountId" : "8ad09be48f7af173018f7bd22d3e2670",
        |      "cancellationEffectiveDate" : null
        |    }
        |  } ]
        }""".stripMargin)
      val user = UserFromToken("test@thegulocal.com", "12345", None, None, None, None, None)
      val productsResponseWrites = new ProductsResponseWrites(catalog)
      import productsResponseWrites.writes
      val productsResponseJson = Json.toJson(productsResponseWrites.from(user, List(accountDetails)))
      productsResponseJson mustEqual expectedResponse
    }

    "return a descriptive name in the current plan name field for a Guardian Ad-Lite subscription" in {
      val startDate = "2024-05-15"
      val endDate = "2025-05-15"
      val nextInvoiceDate = "2024-06-15"
      val email = "test@thegulocal.com"
      val contactId = "003UD00000LtB8QYAV"
      val productName = "Guardian Ad-Lite"
      val priceInPounds: Float = 2.0f
      val priceInPence: Int = (priceInPounds * 100).toInt
      val accountId = "8ad09e54939569d10193b0556af741ee"
      val ratePlanId = "71a1c43a1e192b28f702b3b47113000a"
      val subId = "8ad09be48f7af173018f7bd22da22685"
      val subNumber = "A-S00943727"
      val trialLength = 14
      val GBPSymbol = "£"
      val month = "month"
      val addressLine1 = "address line 1"
      val addressLine2 = "address line 2"
      val town = "town"
      val postcode = "SE1 4PU"
      val country = "United Kingdom"
      val identityId = "12345"

      val deliveryAddress = Some(
        DeliveryAddress(
          Some(addressLine1),
          Some(addressLine2),
          Some(town),
          None,
          Some(postcode),
          Some(country),
          None,
          None,
        ),
      )

      val subscription = Subscription(
        id = Id(subId),
        subscriptionNumber = SubscriptionNumber(subNumber),
        accountId = AccountId(accountId),
        contractEffectiveDate = LocalDate.parse(startDate),
        customerAcceptanceDate = LocalDate.parse(startDate),
        termEndDate = LocalDate.parse(endDate),
        isCancelled = false,
        ratePlans = List(
          RatePlan(
            id = RatePlanId(ratePlanId),
            productRatePlanId = TestCatalog.guardianAdLitePrpId,
            productName,
            lastChangeType = None,
            features = List(),
            ratePlanCharges = NonEmptyList(
              RatePlanCharge(
                SubscriptionRatePlanChargeId("lklklk"),
                ProductRatePlanChargeIds.guardianAdLiteChargeId,
                PricingSummary(Map(GBP -> Price(priceInPounds, GBP))),
                Some(ZMonth),
                None,
                SubscriptionEnd,
                None,
                None,
                Some(LocalDate.parse(nextInvoiceDate)),
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
              ),
            ),
          ),
        ),
        readerType = Direct,
        autoRenew = true,
      )

      val paymentDetails = PaymentDetails(
        subscriberId = subNumber,
        startDate = LocalDate.parse(startDate),
        customerAcceptanceDate = LocalDate.parse(startDate),
        chargedThroughDate = Some(LocalDate.parse(nextInvoiceDate)),
        termEndDate = LocalDate.parse(endDate),
        nextPaymentPrice = Some(priceInPence),
        lastPaymentDate = Some(LocalDate.parse(startDate)),
        nextPaymentDate = Some(LocalDate.parse(nextInvoiceDate)),
        nextInvoiceDate = Some(LocalDate.parse(nextInvoiceDate)),
        remainingTrialLength = trialLength,
        pendingCancellation = false,
        paymentMethod = Some(
          PaymentCard(
            isReferenceTransaction = true,
            cardType = Some("Visa"),
            paymentCardDetails = Some(PaymentCardDetails("4242", 1, 2027)),
            numConsecutiveFailures = Some(0),
            paymentMethodStatus = Some("Active"),
          ),
        ),
        plan = PersonalPlan(productName, Price(priceInPounds, GBP), month),
      )

      val accountDetails = AccountDetails(
        contactId,
        regNumber = None,
        email = Some(email),
        deliveryAddress,
        subscription,
        paymentDetails,
        billingCountry = Some(com.gu.i18n.Country.UK),
        stripePublicKey = "pk_test_abc123",
        accountHasMissedRecentPayments = false,
        safeToUpdatePaymentMethod = true,
        isAutoRenew = true,
        alertText = None,
        accountId,
        cancellationEffectiveDate = None,
      )

      val expectedResponse = Json.parse(s"""
        |{
        |  "user": {
        |    "email": "$email"
        |  },
        |  "products": [
        |    {
        |      "mmaCategory": "subscriptions",
        |      "tier": "$productName",
        |      "isPaidTier": true,
        |      "selfServiceCancellation": {
        |        "isAllowed": true,
        |        "shouldDisplayEmail": true,
        |        "phoneRegionsToDisplay": [
        |          "UK & ROW",
        |          "US",
        |          "AUS"
        |        ]
        |      },
        |      "billingCountry": "$country",
        |      "joinDate" : "$startDate",
        |      "optIn": true,
        |      "subscription": {
        |        "paymentMethod": "Card",
        |        "card": {
        |          "last4": "4242",
        |          "expiry": {
        |            "month": 1,
        |            "year": 2027
        |          },
        |          "type": "Visa",
        |          "stripePublicKeyForUpdate": "pk_test_abc123",
        |          "email": "$email"
        |        },
        |        "contactId": "003UD00000LtB8QYAV",
        |        "deliveryAddress" : {
        |          "addressLine1" : "$addressLine1",
        |          "addressLine2" : "$addressLine2",
        |          "town" : "$town",
        |          "postcode" : "$postcode",
        |          "country" : "$country"
        |        },
        |        "safeToUpdatePaymentMethod": true,
        |        "start" : "$startDate",
        |        "end" : "$endDate",
        |        "nextPaymentPrice": $priceInPence,
        |        "nextPaymentDate" : "$nextInvoiceDate",
        |        "potentialCancellationDate" : "$nextInvoiceDate",
        |        "lastPaymentDate" : "$startDate",
        |        "chargedThroughDate" : "$nextInvoiceDate",
        |        "renewalDate" : "$endDate",
        |        "anniversaryDate" : "$endDate",
        |        "cancelledAt": false,
        |        "subscriptionId": "$subNumber",
        |        "trialLength": $trialLength,
        |        "autoRenew": true,
        |        "plan": {
        |          "name": "$productName",
        |          "price": $priceInPence,
        |          "currency": "$GBPSymbol",
        |          "currencyISO": "$GBP",
        |          "billingPeriod": "$month"
        |        },
        |        "currentPlans" : [ {
        |          "name" : null,
        |          "start" : "$startDate",
        |          "end" : "$endDate",
        |          "shouldBeVisible" : true,
        |          "chargedThrough" : "$nextInvoiceDate",
        |          "price" : $priceInPence,
        |          "currency" : "$GBPSymbol",
        |          "currencyISO" : "$GBP",
        |          "billingPeriod" : "$month",
        |          "features" : ""
        |        } ],
        |        "futurePlans" : [ ],
        |        "readerType": "Direct",
        |        "accountId": "$accountId",
        |        "cancellationEffectiveDate": null
        |      }
        |    }
        |  ]
        }""".stripMargin)

      val user = UserFromToken(email, identityId, None, None, None, None, None)
      val productsResponseWrites = new ProductsResponseWrites(catalog)
      import productsResponseWrites.writes
      val productsResponseJson = Json.toJson(productsResponseWrites.from(user, List(accountDetails)))
      productsResponseJson mustEqual expectedResponse
    }
  }
}
