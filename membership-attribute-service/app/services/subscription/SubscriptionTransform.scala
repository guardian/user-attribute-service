package services.subscription

import models.subscription.Subscription.{ProductRatePlanId, RatePlanId}
import models.subscription.subsv2.SubscriptionPlan.AnyPlan
import models.subscription.subsv2._
import models.subscription.subsv2.reads.ChargeListReads.ProductIds
import models.subscription.subsv2.reads.CommonReads._
import models.subscription.subsv2.reads.SubJsonReads._
import models.subscription.subsv2.reads.SubPlanReads
import services.SubscriptionService.CatalogMap
import monitoring.SafeLogger
import org.joda.time.LocalDate
import play.api.libs.json.{Reads => JsReads, _}
import scalaz._
import scalaz.syntax.all._
import scalaz.syntax.std.either._
import scalaz.syntax.std.option._

import scala.language.higherKinds
import scala.util.Try

// this is (all?) the testable stuff without mocking needed
// we should make the subscription service just getting the json, and then we can have testable pure functions here
object SubscriptionTransform {

  val subIdsReads: JsReads[SubIds] = new JsReads[SubIds] {
    override def reads(json: JsValue): JsResult[SubIds] = {

      (
        (json \ "id").validate[String].map(RatePlanId) |@|
          (json \ "productRatePlanId").validate[String].map(ProductRatePlanId)
      )(SubIds)
    }
  }

  def backdoorRatePlanIdsFromJson(subJson: JsValue): Disjunction[String, List[SubIds]] = {
    val ids = (subJson \ "ratePlans").validate[List[SubIds]](niceListReads(subIdsReads)).asEither.disjunction.leftMap(_.toString)
    // didn't actually check if they're current

    ids.leftMap { error =>
      SafeLogger.warn(s"Error from sub service for json: $error")
    }

    ids
  }

  def tryTwoReadersForSubscriptionJson[PREFERRED <: AnyPlan: SubPlanReads, FALLBACK <: AnyPlan: SubPlanReads](catalog: CatalogMap, pids: ProductIds)(
      subJsons: List[JsValue],
  ): \/[String, Disjunction[Subscription[FALLBACK], Subscription[PREFERRED]]] = {
    val maybePreferred =
      getCurrentSubscriptions[PREFERRED](catalog, pids)(subJsons).map(_.head /*if more than one current, just pick one (for now!)*/ )
    lazy val maybeFallback =
      getCurrentSubscriptions[FALLBACK](catalog, pids)(subJsons).map(_.head /*if more than one current, just pick one (for now!)*/ )
    maybePreferred match {
      case \/-(preferredSub) => \/.right(\/-(preferredSub))
      case -\/(err1) =>
        maybeFallback match {
          case \/-(fallbackSub) => \/.right(-\/(fallbackSub))
          case -\/(err2) => \/.left(s"Error from sub service: $err1\n\n$err2")
        }
    }
  }

  type TimeRelativeSubTransformer[P <: AnyPlan] = (CatalogMap, ProductIds) => List[JsValue] => Disjunction[String, NonEmptyList[Subscription[P]]]

  def getCurrentSubscriptions[P <: AnyPlan: SubPlanReads](catalog: CatalogMap, pids: ProductIds)(
      subJsons: List[JsValue],
  ): Disjunction[String, NonEmptyList[Subscription[P]]] = {

    def getFirstCurrentSub[P <: AnyPlan](
        subs: NonEmptyList[Subscription[P]],
    ): String \/ NonEmptyList[Subscription[P]] = // just quickly check to find one with a current plan
      Sequence(
        subs
          .map { sub =>
            Try {
              sub.plan // just to force a throw if it doesn't have one
            } match {
              case scala.util.Success(_) => \/-(sub): \/[String, Subscription[P]]
              case scala.util.Failure(ex) => -\/(ex.toString): \/[String, Subscription[P]]
            }
          }
          .list
          .toList,
      )

    Sequence(subJsons.map { subJson =>
      getSubscription(catalog, pids)(subJson)
    }).flatMap(getFirstCurrentSub[P])
  }

  def getSubscriptionsActiveOnOrAfter[P <: AnyPlan: SubPlanReads](
      onOrAfter: LocalDate,
  )(catalog: CatalogMap, pids: ProductIds)(subJsons: List[JsValue]): Disjunction[String, NonEmptyList[Subscription[P]]] =
    Sequence(subJsons.map(getSubscription[P](catalog, pids)).filter {
      case \/-(sub) => !sub.termEndDate.isBefore(onOrAfter)
      case _ => false
    })

  def getRecentlyCancelledSubscriptions[P <: AnyPlan: SubPlanReads](
      today: LocalDate,
      lastNMonths: Int, // cancelled in the last n months
      catalog: CatalogMap,
      pids: ProductIds,
      subJsons: List[JsValue],
  ): Disjunction[String, List[Subscription[P]]] = {
    import Scalaz._
    subJsons
      .map(getSubscription[P](catalog, pids))
      .sequence
      .map {
        _.filter { sub =>
          sub.isCancelled &&
          (sub.termEndDate isAfter today.minusMonths(lastNMonths)) &&
          (sub.termEndDate isBefore today)
        }
      }
  }

  def getSubscription[P <: AnyPlan: SubPlanReads](
      catalog: CatalogMap,
      pids: ProductIds,
      now: () => LocalDate = LocalDate.now, /*now only needed for pending friend downgrade*/
  )(subJson: JsValue): Disjunction[String, Subscription[P]] = {
    import Trace.Traceable
    val planToSubscriptionFunction =
      subscriptionReads[P](now()).reads(subJson).asEither.disjunction.leftMap(_.mkString(" ")).withTrace("planToSubscriptionFunction")

    val lowLevelPlans = subJson
      .validate[List[SubscriptionZuoraPlan]](subZuoraPlanListReads)
      .asEither
      .disjunction
      .leftMap(_.toString)
      .withTrace("validate-lowLevelPlans")
    lowLevelPlans.flatMap { lowLevelPlans =>
      val validHighLevelPlans: String \/ NonEmptyList[P] =
        Sequence(
          lowLevelPlans
            .map { lowLevelPlan =>
              // get the equivalent plan from the catalog so we can merge them into a standard high level object
              catalog
                .get(lowLevelPlan.productRatePlanId)
                .toRightDisjunction(s"No catalog plan - prpId = ${lowLevelPlan.productRatePlanId}")
                .flatMap { catalogPlan =>
                  val maybePlans = implicitly[SubPlanReads[P]].read(pids, lowLevelPlan, catalogPlan)
                  maybePlans.toDisjunction
                    .leftMap(
                      _.list.zipWithIndex
                        .map { case (err, index) =>
                          s"  ${index + 1}: $err"
                        }
                        .toList
                        .mkString("\n", "\n", "\n"),
                    )
                    .withTrace(s"high-level-plan-read: ${lowLevelPlan.id}")
                }
            },
        )

      // now wrap them in a subscription
      validHighLevelPlans.flatMap(highLevelPlans => planToSubscriptionFunction.map(_.apply(highLevelPlans)))
    }
  }

}
