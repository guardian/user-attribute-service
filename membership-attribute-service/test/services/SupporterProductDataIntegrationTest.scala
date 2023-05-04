package services

import akka.actor.ActorSystem
import com.gu.aws.ProfileName
import com.typesafe.scalalogging.LazyLogging
import configuration.Stage
import monitoring.CreateNoopMetrics
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  EnvironmentVariableCredentialsProvider,
  InstanceProfileCredentialsProvider,
  ProfileCredentialsProvider,
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbAsyncClientBuilder}

class SupporterProductDataIntegrationTest(implicit ee: ExecutionEnv) extends Specification with LazyLogging {

  val stage = "CODE" // Whichever stage is specified here, you will need config for it in /etc/gu/members-data-api.private.conf
  lazy val CredentialsProvider = AwsCredentialsProviderChain.builder
    .credentialsProviders(
      ProfileCredentialsProvider.builder.profileName(ProfileName).build,
      InstanceProfileCredentialsProvider.builder.asyncCredentialUpdateEnabled(false).build,
      EnvironmentVariableCredentialsProvider.create(),
    )
    .build

  lazy val dynamoClientBuilder: DynamoDbAsyncClientBuilder = DynamoDbAsyncClient.builder
    .credentialsProvider(CredentialsProvider)
    .region(Region.EU_WEST_1)
  lazy val mapper = new SupporterRatePlanToAttributesMapper(Stage(stage))
  lazy val supporterProductDataTable = s"SupporterProductData-$stage"
  lazy val supporterProductDataService =
    new DynamoSupporterProductDataService(dynamoClientBuilder.build(), supporterProductDataTable, mapper, CreateNoopMetrics)

  implicit private val actorSystem: ActorSystem = ActorSystem()

  args(skipAll = true) // This test requires credentials so won't run on CI, change skipAll to false to run locally

  "SupporterProductData" should {
    "get attributes by identity id" in {
      supporterProductDataService.getNonCancelledAttributes("3355555").map {
        case Right(attributes) =>
          logger.info(attributes.toString)
          ok
        case Left(err) => ko(err)
      }
    }
  }
}
