package com.gu.memsub.subsv2

import com.github.nscala_time.time.Imports._
import com.gu.memsub
import com.gu.memsub.BillingPeriod.OneTimeChargeBillingPeriod
import com.gu.memsub.Product
import com.gu.memsub.Product.Contribution
import com.gu.memsub.promo.PromoCode
import com.gu.memsub.subsv2.services.Sequence
import org.joda.time.{DateTime, LocalDate}
import scalaz.syntax.all._
import scalaz.{NonEmptyList, Validation, \/}

case class Subscription(
    id: memsub.Subscription.Id,
    subscriptionNumber: memsub.Subscription.SubscriptionNumber,
    accountId: memsub.Subscription.AccountId,
    contractEffectiveDate: LocalDate,
    customerAcceptanceDate: LocalDate,
    termEndDate: LocalDate,
    isCancelled: Boolean,
    ratePlans: List[RatePlan],
    readerType: ReaderType,
    autoRenew: Boolean,
) {

  val firstPaymentDate: LocalDate = (customerAcceptanceDate :: ratePlans.map(_.effectiveStartDate)).min

  def plan(catalog: Catalog): RatePlan =
    GetCurrentPlans.currentPlans(this, LocalDate.now, catalog).fold(error => throw new RuntimeException(error), _.head)

}

/*
this goes through all the plan objects and only returns the ones that are current.
This could include more than one of the same type especially if it's the changeover date.
It then sorts them so the "best" one is first in the list.  Best just means more expensive,
so this code could be an area of disaster in future.
If we are comparing two Free plans (possible because there is a legacy Friend plan in Zuora) we need to work
with the newest plan for upgrade and cancel scenarios, so in this case the most recent start date wins.
 */
object GetCurrentPlans {

  def bestCancelledPlan(sub: Subscription): Option[RatePlan] =
    if (sub.isCancelled && sub.termEndDate.isBefore(LocalDate.now()))
      sub.ratePlans.sortBy(_.totalChargesMinorUnit).reverse.headOption
    else None

  case class DiscardedPlan(plan: RatePlan, why: String)

  def currentPlans(sub: Subscription, date: LocalDate, catalog: Catalog): String \/ NonEmptyList[RatePlan] = {

    val currentPlans = sub.ratePlans.sortBy(_.totalChargesMinorUnit).reverse.map { plan =>
      val product = plan.product(catalog)
      // If the sub hasn't been paid yet but has started we should fast-forward to the date of first payment (free trial)
      val dateToCheck = if (sub.contractEffectiveDate <= date && sub.customerAcceptanceDate > date) sub.customerAcceptanceDate else date

      val unvalidated = Validation.s[NonEmptyList[DiscardedPlan]](plan)
      /*
      Note that a Contributor may have future sub.acceptanceDate and plan.startDate values if the user has
      updated their payment amount via MMA since starting the contribution. In this case the alreadyStarted assessment
      just checks that the sub.startDate is before, or the same as, the date received by this function.
       */
      val ensureStarted = unvalidated.ensure(DiscardedPlan(plan, s"hasn't started as of $dateToCheck").wrapNel)(_)
      val alreadyStarted =
        if (product == Contribution)
          ensureStarted(_ => sub.contractEffectiveDate <= date)
        else
          ensureStarted(_.effectiveStartDate <= dateToCheck)
      val contributorPlanCancelled =
        alreadyStarted.ensure(DiscardedPlan(plan, "has a contributor plan which has been cancelled or removed").wrapNel)(_)
      val paidPlanEnded = alreadyStarted.ensure(DiscardedPlan(plan, "has a paid plan which has ended").wrapNel)(_)
      val digipackGiftEnded = alreadyStarted.ensure(DiscardedPlan(plan, "has a digipack gift plan which has ended").wrapNel)(_)
      if (product == Product.Contribution)
        contributorPlanCancelled(_ => !sub.isCancelled && !plan.lastChangeType.contains("Remove"))
      else if (product == Product.Digipack && plan.billingPeriod.toOption.contains(OneTimeChargeBillingPeriod))
        digipackGiftEnded(_ => sub.termEndDate >= dateToCheck)
      else
        paidPlanEnded(_ => {
          val inGracePeriodAndNotCancelled = plan.effectiveEndDate == dateToCheck && !sub.isCancelled
          plan.effectiveEndDate > dateToCheck || inGracePeriodAndNotCancelled
        })
    }

    Sequence(
      currentPlans.map(
        _.leftMap(_.map(discard => s"Discarded ${discard.plan.id.get} because it ${discard.why}").list.toList.mkString("\n")).toDisjunction,
      ),
    )
  }
}

object ReaderType {

  case object Direct extends ReaderType {
    val value = "Direct"
  }
  case object Gift extends ReaderType {
    val value = "Gift"
  }
  case object Agent extends ReaderType {
    val value = "Agent"
  }
  case object Student extends ReaderType {
    val value = "Student"
  }
  case object Complementary extends ReaderType {
    val value = "Complementary" // Spelled this way to match value in Saleforce/Zuora
    val alternateSpelling = "Complimentary"
  }
  case object Corporate extends ReaderType {
    val value = "Corporate"
  }
  case object Patron extends ReaderType {
    val value = "Patron"
  }

  def apply(maybeString: Option[String]): ReaderType =
    maybeString
      .map {
        case Direct.value => Direct
        case Gift.value => Gift
        case Agent.value => Agent
        case Student.value => Student
        case Complementary.value => Complementary
        case Complementary.alternateSpelling => Complementary
        case Corporate.value => Corporate
        case Patron.value => Patron
        case unknown => throw new RuntimeException(s"Unknown reader type: $unknown")
      }
      .getOrElse(Direct)

}
sealed trait ReaderType {
  def value: String
}
