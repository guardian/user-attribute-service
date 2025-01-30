package com.gu.memsub
import scalaz.syntax.std.option._
import scalaz.syntax.std.boolean._

/** This is an enumeration of the products we have in Zuora, where products contain multiple rate plans
  *
  * We need this in our model of a plan as paper delivery / paper voucher plans have exactly the same structure but are nested under different zuora
  * products
  */
sealed trait Product {
  val name: String
}
object Product {

  sealed trait ContentSubscription extends Product

  sealed trait Paper extends ContentSubscription
  sealed trait Weekly extends Paper

  case object UnknownProduct extends Product {
    val name = "UNKNOWN_PRODUCT"
  }

  case object Membership extends Product {
    val name = "membership"
  }
  case object GuardianPatron extends Product {
    val name = "guardianpatron"
  }
  case object SupporterPlus extends ContentSubscription {
    val name = "supporterPlus"
  }
  case object TierThree extends ContentSubscription {
    val name = "tierThree"
  }
  case object GuardianAdLite extends Product {
    val name = "guardianAdLite"
  }
  case object Digipack extends ContentSubscription {
    val name = "digitalpack"
  }
  case object Delivery extends Paper {
    val name = "delivery"
  }
  case object NationalDelivery extends Paper {
    val name = "nationalDelivery"
  }
  case object Voucher extends Paper {
    val name = "voucher"
  }
  case object DigitalVoucher extends Paper {
    val name = "digitalVoucher"
  }
  case object WeeklyZoneA extends Weekly {
    val name = "weeklyZoneA"
  }
  case object WeeklyZoneB extends Weekly {
    val name = "weeklyZoneB"
  }
  case object WeeklyZoneC extends Weekly {
    val name = "weeklyZoneC"
  }
  case object WeeklyDomestic extends Weekly {
    val name = "weeklyDomestic"
  }
  case object WeeklyRestOfWorld extends Weekly {
    val name = "weeklyRestOfWorld"
  }
  case object Contribution extends Product {
    val name = "contribution"
  }
  case object Discounts extends Product {
    val name = "discounts"
  }

  def fromId(id: String): Option[Product] = id match {
    case Digipack.name => Some(Digipack)
    case SupporterPlus.name => Some(SupporterPlus)
    case TierThree.name => Some(TierThree)
    case GuardianAdLite.name => Some(GuardianAdLite)
    case Membership.name => Some(Membership)
    case Delivery.name => Some(Delivery)
    case NationalDelivery.name => Some(NationalDelivery)
    case Voucher.name => Some(Voucher)
    case DigitalVoucher.name => Some(DigitalVoucher)
    case WeeklyZoneA.name => Some(WeeklyZoneA)
    case WeeklyZoneB.name => Some(WeeklyZoneB)
    case WeeklyZoneC.name => Some(WeeklyZoneC)
    case Contribution.name => Some(Contribution)
    case GuardianPatron.name => Some(GuardianPatron)
    case _ => None
  }

}

// Benefit is the catalog charge level ProductType__c
sealed trait ProductRatePlanChargeProductType {
  val id: String
  override def toString = s"benefit $id"
}

object ProductRatePlanChargeProductType {

  def fromId(id: String): Option[ProductRatePlanChargeProductType] =
    PaperDay.fromId(id) orElse
      PaidMemberTier.fromId(id) orElse
      (id == SupporterPlus.id).option(SupporterPlus) orElse
      (id == Digipack.id).option(Digipack) orElse
      (id == Adjustment.id).option(Adjustment) orElse
      (id == Contributor.id).option(Contributor) orElse
      (id == Weekly.id).option(Weekly) orElse
      (id == TierThree.id).option(TierThree) orElse
      (id == GuardianAdLite.id).option(GuardianAdLite)

  sealed trait MemberTier extends ProductRatePlanChargeProductType
  sealed trait PaidMemberTier extends MemberTier
  sealed trait PaperDay extends ProductRatePlanChargeProductType {
    val dayOfTheWeekIndex: Int
  }

  object PaperDay {
    def fromId(id: String): Option[PaperDay] = id match {
      case MondayPaper.id => MondayPaper.some
      case TuesdayPaper.id => TuesdayPaper.some
      case WednesdayPaper.id => WednesdayPaper.some
      case ThursdayPaper.id => ThursdayPaper.some
      case FridayPaper.id => FridayPaper.some
      case SaturdayPaper.id => SaturdayPaper.some
      case SundayPaper.id => SundayPaper.some
      case _ => None
    }
  }

  object PaidMemberTier {
    def fromId(id: String): Option[PaidMemberTier] = id match {
      case Supporter.id => Supporter.some
      case Partner.id => Partner.some
      case Patron.id => Patron.some
      case _ => None
    }
  }

  object Contributor extends ProductRatePlanChargeProductType {
    override val id = "Contributor"
  }

  object Supporter extends PaidMemberTier {
    override val id = "Supporter"
  }

  object Partner extends PaidMemberTier {
    override val id = "Partner"
  }

  object Patron extends PaidMemberTier {
    override val id = "Patron"
  }

  // This is the new non-membership version of a patron
  object GuardianPatron extends ProductRatePlanChargeProductType {
    override val id = "Guardian Patron"
  }

  object Digipack extends ProductRatePlanChargeProductType {
    override val id = "Digital Pack"
  }

  object SupporterPlus extends ProductRatePlanChargeProductType {
    override val id = "Supporter Plus"
  }

  object Weekly extends ProductRatePlanChargeProductType {
    override val id = "Guardian Weekly"
  }

  object TierThree extends ProductRatePlanChargeProductType {
    override val id = "Tier Three"
  }

  object GuardianAdLite extends ProductRatePlanChargeProductType {
    override val id = "Guardian Ad-Lite"
  }

  object MondayPaper extends PaperDay {
    override val id = "Print Monday"
    override val dayOfTheWeekIndex = 1
  }

  object TuesdayPaper extends PaperDay {
    override val id = "Print Tuesday"
    override val dayOfTheWeekIndex = 2
  }

  object WednesdayPaper extends PaperDay {
    override val id = "Print Wednesday"
    override val dayOfTheWeekIndex = 3
  }

  object ThursdayPaper extends PaperDay {
    override val id = "Print Thursday"
    override val dayOfTheWeekIndex = 4
  }

  object FridayPaper extends PaperDay {
    override val id = "Print Friday"
    override val dayOfTheWeekIndex = 5
  }

  object SaturdayPaper extends PaperDay {
    override val id = "Print Saturday"
    override val dayOfTheWeekIndex = 6
  }

  object SundayPaper extends PaperDay {
    override val id = "Print Sunday"
    override val dayOfTheWeekIndex = 7
  }

  object Adjustment extends ProductRatePlanChargeProductType {
    override val id = "Adjustment"
  }

}
