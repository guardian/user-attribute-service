package models

import com.github.nscala_time.time.OrderingImplicits._
import json._
import models.FeastApp.{getFeastAndroidOfferTags, getFeastIosSubscriptionGroup}
import org.joda.time.LocalDate
import org.joda.time.LocalDate.now
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scalaz.syntax.std.boolean._

import scala.language.implicitConversions

case class ContentAccess(
    member: Boolean,
    paidMember: Boolean,
    recurringContributor: Boolean,
    supporterPlus: Boolean,
    feast: Boolean,
    digitalPack: Boolean,
    paperSubscriber: Boolean,
    guardianWeeklySubscriber: Boolean,
    guardianPatron: Boolean,
    guardianAdLite: Boolean,
)

object ContentAccess {

  implicit val jsWrite = Json.writes[ContentAccess]
}

case class Attributes(
    UserId: String,
    Tier: Option[String] = None,
    RecurringContributionPaymentPlan: Option[String] = None,
    OneOffContributionDate: Option[LocalDate] = None,
    MembershipJoinDate: Option[LocalDate] = None,
    SupporterPlusExpiryDate: Option[LocalDate] = None,
    DigitalSubscriptionExpiryDate: Option[LocalDate] = None,
    PaperSubscriptionExpiryDate: Option[LocalDate] = None,
    GuardianWeeklySubscriptionExpiryDate: Option[LocalDate] = None,
    LiveAppSubscriptionExpiryDate: Option[LocalDate] = None,
    GuardianPatronExpiryDate: Option[LocalDate] = None,
    AlertAvailableFor: Option[String] = None,
    RecurringContributionAcquisitionDate: Option[LocalDate] = None,
    GuardianAdLiteExpiryDate: Option[LocalDate] = None,
) {
  lazy val isSupporterTier = Tier.exists(_.equalsIgnoreCase("supporter"))
  lazy val isPartnerTier = Tier.exists(_.equalsIgnoreCase("partner"))
  lazy val isPatronTier = Tier.exists(_.equalsIgnoreCase("patron"))
  lazy val isPaidTier: Boolean = isSupporterTier || isPartnerTier || isPatronTier
  lazy val isRecurringContributor = RecurringContributionPaymentPlan.isDefined
  lazy val isRecentOneOffContributor = OneOffContributionDate.exists(_.isAfter(now.minusMonths(3)))
  lazy val isSupporterPlus = SupporterPlusExpiryDate.exists(_.isAfter(now))
  lazy val staffDigitalSubscriptionExpiryDate: Option[LocalDate] = Tier.exists(_.equalsIgnoreCase("staff")).option(now.plusDays(1))
  lazy val latestDigitalSubscriptionExpiryDate =
    Some(Set(staffDigitalSubscriptionExpiryDate, DigitalSubscriptionExpiryDate).flatten).filter(_.nonEmpty).map(_.max)
  lazy val digitalSubscriberHasActivePlan = latestDigitalSubscriptionExpiryDate.exists(_.isAfter(now))
  lazy val isPaperSubscriber = PaperSubscriptionExpiryDate.exists(_.isAfter(now))
  lazy val isGuardianWeeklySubscriber = GuardianWeeklySubscriptionExpiryDate.exists(_.isAfter(now))
  lazy val isPremiumLiveAppSubscriber = LiveAppSubscriptionExpiryDate.exists(_.isAfter(now))
  lazy val isGuardianPatron = GuardianPatronExpiryDate.exists(_.isAfter(now))
  lazy val isGuardianAdLite = GuardianAdLiteExpiryDate.exists(_.isAfter(now))

  lazy val contentAccess = ContentAccess(
    member = isPaidTier,
    paidMember = isPaidTier,
    recurringContributor = isRecurringContributor,
    supporterPlus = isSupporterPlus,
    digitalPack = digitalSubscriberHasActivePlan || isPaperSubscriber || isGuardianPatron || isSupporterPlus,
    paperSubscriber = isPaperSubscriber,
    guardianWeeklySubscriber = isGuardianWeeklySubscriber,
    guardianPatron = isGuardianPatron,
    feast = FeastApp.shouldGetFeastAccess(this),
    guardianAdLite = isGuardianAdLite,
  )

  // show support messaging (in app & on dotcom) if they do NOT have any active products
  // TODO in future this could become more sophisticated (e.g. two weeks before their products expire)
  lazy val showSupportMessaging = !(
    isPaidTier
      || isSupporterPlus
      || isRecurringContributor
      || isRecentOneOffContributor
      || digitalSubscriberHasActivePlan
      || isPaperSubscriber
      || isGuardianWeeklySubscriber
      || isPremiumLiveAppSubscriber
  )

}

object Attributes {

  implicit val jsAttributesWrites: OWrites[Attributes] = (
    (__ \ "userId").write[String] and
      (__ \ "tier").writeNullable[String] and
      (__ \ "recurringContributionPaymentPlan").writeNullable[String] and
      (__ \ "oneOffContributionDate").writeNullable[LocalDate] and
      (__ \ "membershipJoinDate").writeNullable[LocalDate] and
      JsPath.writeNullable[LocalDate].contramap[Option[LocalDate]](_ => None) and // do not serialize supporterPlusExpiryDate
      (__ \ "digitalSubscriptionExpiryDate").writeNullable[LocalDate] and
      (__ \ "paperSubscriptionExpiryDate").writeNullable[LocalDate] and
      (__ \ "guardianWeeklyExpiryDate").writeNullable[LocalDate] and
      (__ \ "liveAppSubscriptionExpiryDate").writeNullable[LocalDate] and
      (__ \ "guardianPatronExpiryDate").writeNullable[LocalDate] and
      (__ \ "alertAvailableFor").writeNullable[String] and
      (__ \ "recurringContributionAcquisitionDate").writeNullable[LocalDate] and
      (__ \ "guardianAdLiteExpiryDate").writeNullable[LocalDate]
  )(unlift(Attributes.unapply))
    .addNullableField("digitalSubscriptionExpiryDate", _.latestDigitalSubscriptionExpiryDate)
    .addField("showSupportMessaging", _.showSupportMessaging)
    .addNullableField("feastIosSubscriptionGroup", getFeastIosSubscriptionGroup)
    .addNullableField("feastAndroidOfferTags", getFeastAndroidOfferTags)
    .addField("contentAccess", _.contentAccess)
}

case class MembershipAttributes(
    UserId: String,
    Tier: String,
    AdFree: Option[Boolean] = None,
    ContentAccess: MembershipContentAccess,
)

object MembershipAttributes {

  implicit val jsWrite: OWrites[MembershipAttributes] = (
    (__ \ "userId").write[String] and
      (__ \ "tier").write[String] and
      (__ \ "adFree").writeNullable[Boolean] and
      (__ \ "contentAccess").write[MembershipContentAccess](MembershipContentAccess.jsWrite)
  )(unlift(MembershipAttributes.unapply))

  def fromAttributes(attr: Attributes): Option[MembershipAttributes] = for {
    tier <- attr.Tier
  } yield {
    MembershipAttributes(
      UserId = attr.UserId,
      Tier = tier,
      ContentAccess = MembershipContentAccess(
        member = attr.contentAccess.member,
        paidMember = attr.contentAccess.paidMember,
      ),
    )
  }

}

case class MembershipContentAccess(member: Boolean, paidMember: Boolean)

object MembershipContentAccess {
  implicit val jsWrite = Json.writes[MembershipContentAccess]
}
