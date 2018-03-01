package services

import com.gu.memsub.subsv2.Subscription
import com.gu.memsub.subsv2.SubscriptionPlan.AnyPlan
import com.gu.zuora.rest.ZuoraRestService.{AccountSummary, PaymentMethodId, PaymentMethodResponse}
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{Disjunction, \/}

object PaymentFailureAlerter {

  def alertAvailableFor(
    accountSummary: AccountSummary, subscription: Subscription[AnyPlan],
    paymentMethodGetter: PaymentMethodId => Future[String \/ PaymentMethodResponse])
    (implicit ec: ExecutionContext): Future[Boolean] = {

    def creditCard(paymentMethodResponse: PaymentMethodResponse) = paymentMethodResponse.paymentMethodType == "CreditCardReferenceTransaction" || paymentMethodResponse.paymentMethodType == "CreditCard"

    val stillFreshInDays = 27
    def recentEnough(lastTransationDateTime: DateTime) = lastTransationDateTime.plusDays(stillFreshInDays).isAfterNow

    val userInPaymentFailure: Future[\/[String, Boolean]] = {
      if(accountSummary.balance > 0 && accountSummary.defaultPaymentMethod.isDefined) {
        val eventualPaymentMethod: Future[\/[String, PaymentMethodResponse]] = paymentMethodGetter(accountSummary.defaultPaymentMethod.get.id)

        eventualPaymentMethod map { maybePaymentMethod: \/[String, PaymentMethodResponse] =>
          maybePaymentMethod.map { pm: PaymentMethodResponse =>
            creditCard(pm) &&
              pm.numConsecutiveFailures > 0 &&
              recentEnough(pm.lastTransactionDateTime)
          }
        }
      }
      else Future.successful(\/.right(false))
    }

    userInPaymentFailure map (_.getOrElse(false))
  }

  def membershipAlert(accountSummary: AccountSummary, subscription: Subscription[AnyPlan],
                      paymentMethodGetter: PaymentMethodId => Future[String \/ PaymentMethodResponse])(implicit ec: ExecutionContext) : Future[Option[String]] = {

//    val paymentMethod: Future[Disjunction[String, PaymentMethodResponse]] = paymentMethodGetter(accountSummary.defaultPaymentMethod.map(_.id))
//    val paymentMethodLatestDate: Future[Disjunction[String, DateTime]] = paymentMethod.map { pm: Disjunction[String, PaymentMethodResponse] => pm map { resp => resp.lastTransactionDateTime}}

//    val actionText = s"Our attempt to take payment for your membership on ${accountSummary.defaultPaymentMethod.}"
    val actionText = "Don't just sit there; do something!"

    alertAvailableFor(accountSummary, subscription, paymentMethodGetter) map { action =>
      if(action)
        Some(actionText)
      else None
    }

  }
}
