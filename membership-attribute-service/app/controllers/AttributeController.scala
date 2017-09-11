package controllers
import actions._
import com.gu.memsub.subsv2.reads.ChargeListReads._
import com.gu.memsub.subsv2.reads.SubPlanReads._
import com.gu.memsub.subsv2.{Subscription, SubscriptionPlan}
import com.gu.scanamo.error.DynamoReadError
import configuration.Config
import configuration.Config.authentication
import loghandling.LoggingField.{LogField, LogFieldString}
import loghandling.LoggingWithLogstashFields
import models.ApiError._
import models.ApiErrors._
import models.Features._
import models._
import monitoring.Metrics
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc._
import play.filters.cors.CORSActionBuilder
import prodtest.Allocator._
import services.{AuthenticationService, IdentityAuthService}

import scala.concurrent.Future
import scalaz.std.scalaFuture._
import scalaz.syntax.std.either._
import scalaz.syntax.std.option._
import scalaz.{EitherT, \/}


class AttributeController extends Controller with LoggingWithLogstashFields {

  val keys = authentication.keys.map(key => s"Bearer $key")

  def apiKeyFilter(): ActionBuilder[Request] = new ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      request.headers.get("Authorization") match {
        case Some(header) if keys.contains(header) => block(request)
        case _ => Future.successful(ApiErrors.invalidApiKey)
      }
    }
  }

  lazy val corsFilter = CORSActionBuilder(Config.corsConfig)
  lazy val backendAction = NoCacheAction andThen corsFilter andThen BackendFromCookieAction
  lazy val backendForSyncWithZuora = NoCacheAction andThen apiKeyFilter andThen WithBackendFromUserIdAction
  lazy val authenticationService: AuthenticationService = IdentityAuthService
  lazy val metrics = Metrics("AttributesController")

  private def lookup(endpointDescription: String, onSuccessMember: Attributes => Result, onSuccessSupporter: Attributes => Result, onNotFound: Result, endpointEligibleForTest: Boolean, sendAttributesIfNotFound: Boolean = false) =
  {
    def pickAttributes(identityId: String) (implicit request: BackendRequest[AnyContent]): (String, Future[Option[Attributes]]) = {
      if(endpointEligibleForTest){
        val percentageInTest = request.touchpoint.featureToggleData.getPercentageTrafficForZuoraLookupTask.get()
        isInTest(identityId, percentageInTest) match {
          case true => ("Zuora", request.touchpoint.zuoraAttrService.attributesFromZuora(identityId))
          case false => ("Dynamo", request.touchpoint.attrService.get(identityId))
        }
      } else ("Dynamo", request.touchpoint.attrService.get(identityId))
    }

    backendAction.async { implicit request =>
      authenticationService.userId(request) match {
        case Some(identityId) =>
          val (fromWhere, attributes) = pickAttributes(identityId)
          def customFields(supporterType: String): List[LogField] = List(LogFieldString("lookup-endpoint-description", endpointDescription), LogFieldString("supporter-type", supporterType), LogFieldString("data-source", fromWhere))

          attributes.map {
            case Some(attrs @ Attributes(_, Some(tier), _, _, _, _, _, _)) =>
              logInfoWithCustomFields(s"$identityId is a $tier member - $endpointDescription - $attrs found via $fromWhere", customFields("member"))
              onSuccessMember(attrs).withHeaders(
                "X-Gu-Membership-Tier" -> tier,
                "X-Gu-Membership-Is-Paid-Tier" -> attrs.isPaidTier.toString
              )
            case Some(attrs) =>
              attrs.DigitalSubscriptionExpiryDate.foreach { date =>
                logInfoWithCustomFields(s"$identityId is a digital subscriber expiring $date", customFields("digital-subscriber"))
              }
              attrs.RecurringContributionPaymentPlan.foreach { paymentPlan =>
                logInfoWithCustomFields(s"$identityId is a regular $paymentPlan contributor", customFields("contributor"))
              }
              attrs.AdFree.foreach { _ =>
                logInfoWithCustomFields(s"$identityId is an ad-free reader", customFields("ad-free-reader"))
              }
              logInfoWithCustomFields(s"$identityId supports the guardian - $attrs - found via $fromWhere", customFields("supporter"))
              onSuccessSupporter(attrs)
            case None if sendAttributesIfNotFound =>
              Attributes(identityId, AdFree = Some(false))
            case _ =>
              onNotFound
          }
        case None =>
          metrics.put(s"$endpointDescription-cookie-auth-failed", 1)
          Future(unauthorized)

        }
      }
  }

  private def zuoraLookup(endpointDescription: String) =
    backendAction.async { implicit request =>
      authenticationService.userId(request) match {
        case Some(identityId) =>
          request.touchpoint.zuoraAttrService.attributesFromZuora(identityId).map {
            case Some(attrs) =>
              log.info(s"Successfully retrieved attributes from Zuora for user $identityId: $attrs")
              attrs
            case _ => notFound
          }
        case None =>
          metrics.put(s"$endpointDescription-cookie-auth-failed", 1)
          Future(unauthorized)
      }
  }

  val notFound = ApiError("Not found", "Could not find user in the database", 404)
  val notAMember = ApiError("Not found", "User was found but they are not a member", 404)

  private def membershipAttributesFromAttributes(attributes: Attributes): Result = {
     MembershipAttributes.fromAttributes(attributes)
       .map(member => Ok(Json.toJson(member)))
       .getOrElse(notFound)
  }

  def membership = lookup("membership", onSuccessMember = membershipAttributesFromAttributes, onSuccessSupporter = _ => notAMember, onNotFound = notFound, endpointEligibleForTest = true)
  def attributes = lookup("attributes", onSuccessMember = identity[Attributes], onSuccessSupporter = identity[Attributes], onNotFound = notFound, endpointEligibleForTest = true, sendAttributesIfNotFound = true)
  def features = lookup("features", onSuccessMember = Features.fromAttributes, onSuccessSupporter = Features.notAMember, onNotFound = Features.unauthenticated, endpointEligibleForTest = true)
  def zuoraMe = zuoraLookup("zuoraLookup")

  def updateAttributes(identityId : String): Action[AnyContent] = backendForSyncWithZuora.async { implicit request =>

    val tp = request.touchpoint

    val result: EitherT[Future, String, Attributes] =
      // TODO - add the Stripe lookups for the Contribution and Membership cards to this flow, then we can deprecate the Salesforce hook.
      for {
        contact <- EitherT(tp.contactRepo.get(identityId).map(_.flatMap(_ \/> s"No contact for $identityId")))
        memSubF = EitherT[Future, String, Option[Subscription[SubscriptionPlan.Member]]](tp.subService.current[SubscriptionPlan.Member](contact).map(a => \/.right(a.headOption)))
        conSubF = EitherT[Future, String, Option[Subscription[SubscriptionPlan.Contributor]]](tp.subService.current[SubscriptionPlan.Contributor](contact).map(a => \/.right(a.headOption)))
        memSub <- memSubF
        conSub <- conSubF
        _ <- EitherT(Future.successful(if (memSub.isEmpty && conSub.isEmpty) \/.left("No recurring relationship") else \/.right(())))
        attributes = Attributes(
          UserId = identityId,
          Tier = memSub.map(_.plan.charges.benefit.id),
          MembershipNumber = contact.regNumber,
          RecurringContributionPaymentPlan = conSub.map(_.plan.name),
          MembershipJoinDate = memSub.map(_.startDate)
        )
        res <- EitherT(tp.attrService.update(attributes).map(_.disjunction)).leftMap(e => s"Dynamo failed to update the user attributes: ${DynamoReadError.describe(e)}")
      } yield attributes

    result.fold(
      {  error =>
        log.error(s"Failed to update attributes - $error")
        ApiErrors.badRequest(error)
      },
      { attributes =>
        log.info(s"${attributes.UserId} -> ${attributes.Tier} || ${attributes.RecurringContributionPaymentPlan}")
        Ok(Json.obj("updated" -> true))
      }
    )
  }
}
