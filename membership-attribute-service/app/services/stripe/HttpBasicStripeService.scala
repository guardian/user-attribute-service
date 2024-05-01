package services.stripe

import com.gu.memsub.util.WebServiceHelper
import com.gu.monitoring.SafeLogging
import com.gu.okhttp.RequestRunners._
import com.gu.stripe.Stripe.Deserializer._
import com.gu.stripe.Stripe._
import com.gu.stripe.{BasicStripeServiceConfig, Stripe, StripeServiceConfig}
import okhttp3.Request

import scala.concurrent.{ExecutionContext, Future}

class HttpBasicStripeService(config: BasicStripeServiceConfig, val httpClient: FutureHttpClient)(implicit ec: ExecutionContext)
    extends WebServiceHelper[StripeObject, Stripe.Error]
    with BasicStripeService
    with SafeLogging {
  override val wsUrl = "https://api.stripe.com/v1" // Stripe URL is the same in all environments

  override def wsPreExecute(req: Request.Builder): Request.Builder = {
    req.addHeader("Authorization", s"Bearer ${config.stripeCredentials.secretKey}")

    config.version match {
      case Some(version) => {
        logger.info(s"Making a stripe call with version: $version")
        req.addHeader("Stripe-Version", version)
      }
      case None => req
    }
  }

  def fetchCustomer(customerId: String): Future[Customer] = for {
    customersPaymentMethods <- fetchPaymentMethod(customerId)
    customer <- customersPaymentMethods.cardStripeList.data match {
      case Nil => get[Customer](s"customers/$customerId") // fallback for older card tokens
      case _ => Future.successful(Stripe.Customer(customerId, customersPaymentMethods.cardStripeList))
    }
  } yield customer

  @Deprecated
  def createCustomer(card: String): Future[Customer] =
    post[Customer]("customers", Map("card" -> Seq(card)))

  def createCustomerWithStripePaymentMethod(stripePaymentMethodID: String): Future[Customer] = for {
    createCustomerResponse <- post[CreateCustomerResponse]("customers", Map("payment_method" -> Seq(stripePaymentMethodID)))
    synthesisedCustomerWithCardDetail <- fetchCustomer(createCustomerResponse.id)
  } yield synthesisedCustomerWithCardDetail

  def fetchPaymentMethod(customerId: String): Future[CustomersPaymentMethods] =
    get[CustomersPaymentMethods](s"payment_methods", "customer" -> customerId, "type" -> "card")

  def fetchEvent(id: String): Future[Stripe.Event[StripeObject]] =
    get[Stripe.Event[StripeObject]](s"events/$id")

  def fetchSubscription(id: String): Future[Stripe.Subscription] =
    get[Stripe.Subscription](s"subscriptions/$id", params = ("expand[]", "customer"))
}

object HttpBasicStripeService {
  def from(apiConfig: StripeServiceConfig, client: FutureHttpClient)(implicit ec: ExecutionContext) =
    new HttpBasicStripeService(BasicStripeServiceConfig(apiConfig.credentials, apiConfig.version), client)
}
