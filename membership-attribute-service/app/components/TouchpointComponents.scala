package components

import akka.actor.ActorSystem
import com.gu.aws.ProfileName
import com.gu.config
import com.gu.identity.IdapiService
import com.gu.identity.auth.{DefaultIdentityClaims, IdapiAuthConfig, OktaTokenValidationConfig}
import com.gu.identity.play.IdentityPlayAuthService
import services.PaymentService
import com.gu.memsub.subsv2.services.SubscriptionService.CatalogMap
import com.gu.memsub.subsv2.services.{CatalogService, FetchCatalog, SubscriptionService}
import com.gu.monitoring.SafeLogger._
import com.gu.monitoring.{SafeLogger, ZuoraMetrics}
import com.gu.okhttp.RequestRunners
import com.gu.salesforce.SimpleContactRepository
import com.gu.touchpoint.TouchpointBackendConfig
import com.gu.zuora.ZuoraSoapService
import com.gu.zuora.api.{InvoiceTemplate, InvoiceTemplates, PaymentGateway}
import com.gu.zuora.rest.{SimpleClient, ZuoraRestService}
import com.gu.zuora.soap.ClientWithFeatureSupplier
import com.typesafe.config.Config
import configuration.Stage
import models.{UserFromToken, UserFromTokenParser}
import monitoring.CreateMetrics
import org.http4s.Uri
import scalaz.std.scalaFuture._
import services._
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  EnvironmentVariableCredentialsProvider,
  InstanceProfileCredentialsProvider,
  ProfileCredentialsProvider,
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbAsyncClientBuilder}

import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

class TouchpointComponents(
    stage: Stage,
    createMetrics: CreateMetrics,
    conf: Config,
    supporterProductDataServiceOverride: Option[SupporterProductDataService] = None,
    contactRepositoryOverride: Option[SimpleContactRepository] = None,
    subscriptionServiceOverride: Option[SubscriptionService[Future]] = None,
    zuoraRestServiceOverride: Option[ZuoraRestService[Future]] = None,
    catalogServiceOverride: Option[CatalogService[Future]] = None,
    zuoraServiceOverride: Option[ZuoraSoapService with HealthCheckableService] = None,
    patronsStripeServiceOverride: Option[BasicStripeService] = None,
)(implicit
    system: ActorSystem,
    executionContext: ExecutionContext,
) {
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  lazy val touchpointConfig = conf.getConfig("touchpoint.backend")
  lazy val environmentConfig = touchpointConfig.getConfig(s"environments." + stage.value)

  lazy val digitalPackConf = environmentConfig.getConfig(s"zuora.ratePlanIds.digitalpack")
  lazy val paperCatalogConf = environmentConfig.getConfig(s"zuora.productIds.subscriptions")
  lazy val membershipConf = environmentConfig.getConfig(s"zuora.ratePlanIds.membership")
  lazy val supporterProductDataTable = environmentConfig.getString("supporter-product-data.table")
  lazy val invoiceTemplatesConf = environmentConfig.getConfig(s"zuora.invoiceTemplateIds")

  lazy val digitalPackPlans = config.DigitalPackRatePlanIds.fromConfig(digitalPackConf)
  lazy val productIds = config.SubsV2ProductIds(environmentConfig.getConfig("zuora.productIds"))
  lazy val membershipPlans = config.MembershipRatePlanIds.fromConfig(membershipConf)
  lazy val subsProducts = config.SubscriptionsProductIds(paperCatalogConf)

  lazy val tpConfig = TouchpointBackendConfig.byEnv(stage.value, touchpointConfig)
  implicit lazy val _bt: TouchpointBackendConfig = tpConfig

  lazy val patronsStripeService: BasicStripeService = patronsStripeServiceOverride
    .getOrElse(new BasicStripeService(tpConfig.stripePatrons, RequestRunners.futureRunner))
  lazy val ukStripeService: StripeService = new StripeService(tpConfig.stripeUKMembership, RequestRunners.futureRunner)
  lazy val auStripeService: StripeService = new StripeService(tpConfig.stripeAUMembership, RequestRunners.futureRunner)
  lazy val allStripeServices: Seq[StripeService] = Seq(ukStripeService, auStripeService)
  lazy val stripeServicesByPaymentGateway: Map[PaymentGateway, StripeService] = allStripeServices.map(s => s.paymentGateway -> s).toMap
  lazy val stripeServicesByPublicKey: Map[String, StripeService] = allStripeServices.map(s => s.publicKey -> s).toMap

  lazy val contactRepo: SimpleContactRepository = contactRepositoryOverride.getOrElse(
    new SimpleContactRepository(tpConfig.salesforce, system.scheduler, configuration.ApplicationName.applicationName),
  )
  lazy val salesforceService: SalesforceService = new SalesforceService(contactRepo)

  lazy val CredentialsProvider = AwsCredentialsProviderChain.builder
    .credentialsProviders(
      ProfileCredentialsProvider.builder.profileName(ProfileName).build,
      InstanceProfileCredentialsProvider.builder.asyncCredentialUpdateEnabled(false).build,
      EnvironmentVariableCredentialsProvider.create(),
    )
    .build

  private lazy val dynamoClientBuilder: DynamoDbAsyncClientBuilder = DynamoDbAsyncClient.builder
    .credentialsProvider(CredentialsProvider)
    .region(Region.EU_WEST_1)

  private lazy val mapper = new SupporterRatePlanToAttributesMapper(stage)
  private lazy val dynamoSupporterProductDataService =
    new DynamoSupporterProductDataService(dynamoClientBuilder.build(), supporterProductDataTable, mapper, createMetrics)

  lazy val supporterProductDataService: SupporterProductDataService =
    supporterProductDataServiceOverride.getOrElse(dynamoSupporterProductDataService)

  private val zuoraMetrics = new ZuoraMetrics(stage.value, configuration.ApplicationName.applicationName)
  private lazy val zuoraSoapClient =
    new ClientWithFeatureSupplier(
      featureCodes = Set.empty,
      apiConfig = tpConfig.zuoraSoap,
      httpClient = RequestRunners.configurableFutureRunner(timeout = Duration(30, SECONDS)),
      extendedHttpClient = RequestRunners.futureRunner,
      metrics = zuoraMetrics,
    )
  lazy val zuoraService = zuoraServiceOverride.getOrElse(
    new ZuoraSoapService(zuoraSoapClient) with HealthCheckableService {
      override def checkHealth: Boolean = zuoraSoapClient.isReady
    },
  )

  private lazy val zuoraRestClient = new SimpleClient[Future](tpConfig.zuoraRest, RequestRunners.configurableFutureRunner(30.seconds))
  lazy val zuoraRestService = zuoraRestServiceOverride.getOrElse(new ZuoraRestService[Future]()(futureInstance(ec), zuoraRestClient))

  lazy val catalogRestClient = new SimpleClient[Future](tpConfig.zuoraRest, RequestRunners.configurableFutureRunner(60.seconds))
  lazy val catalogService = catalogServiceOverride.getOrElse(
    new CatalogService[Future](productIds, FetchCatalog.fromZuoraApi(catalogRestClient), Await.result(_, 60.seconds), stage.value),
  )

  lazy val futureCatalog: Future[CatalogMap] = catalogService.catalog
    .map(_.fold[CatalogMap](error => { println(s"error: ${error.list.toList.mkString}"); Map() }, _.map))
    .recover { case error =>
      SafeLogger.error(scrub"Failed to load the product catalog from Zuora due to: $error")
      throw error
    }

  lazy val subService: SubscriptionService[Future] = subscriptionServiceOverride.getOrElse(
    new SubscriptionService[Future](productIds, futureCatalog, zuoraRestClient, zuoraService.getAccountIds),
  )
  lazy val paymentService: PaymentService = new PaymentService(zuoraService, catalogService.unsafeCatalog.productMap)

  lazy val idapiService = new IdapiService(tpConfig.idapi, RequestRunners.futureRunner)
  lazy val tokenVerifierConfig = OktaTokenValidationConfig(
    issuerUrl = conf.getString("okta.verifier.issuerUrl"),
    audience = conf.getString("okta.verifier.audience"),
  )
  lazy val identityPlayAuthService: IdentityPlayAuthService[UserFromToken, DefaultIdentityClaims] = {
    val apiConfig = tpConfig.idapi
    val idApiUrl = Uri.unsafeFromString(apiConfig.url)
    val idapiConfig = IdapiAuthConfig(idApiUrl, apiConfig.token, Some("membership"))
    IdentityPlayAuthService.unsafeInit(
      idapiConfig,
      tokenVerifierConfig,
      accessClaimsParser = UserFromTokenParser,
    )
  }
  lazy val identityAuthService = new IdentityAuthService(identityPlayAuthService)

  lazy val guardianPatronService =
    new GuardianPatronService(supporterProductDataService, patronsStripeService, tpConfig.stripePatrons.stripeCredentials.publicKey)

  lazy val chooseStripeService: ChooseStripeService = new ChooseStripeService(stripeServicesByPaymentGateway, ukStripeService)

  lazy val paymentDetailsForSubscription: PaymentDetailsForSubscription = new PaymentDetailsForSubscription(paymentService)

  lazy val accountDetailsFromZuora: AccountDetailsFromZuora =
    new AccountDetailsFromZuora(
      createMetrics,
      zuoraRestService,
      contactRepo,
      subService,
      chooseStripeService,
      paymentDetailsForSubscription,
    )
}
