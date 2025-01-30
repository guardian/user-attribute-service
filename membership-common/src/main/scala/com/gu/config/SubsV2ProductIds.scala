package com.gu.config
import com.gu.memsub.Product
import com.gu.memsub.Product._
import com.gu.memsub.Subscription.ProductId

object SubsV2ProductIds {

  val guardianPatronProductId: ProductId = ProductId("guardian_patron")

  type ProductMap = Map[ProductId, Product]

  def load(config: com.typesafe.config.Config): ProductMap = Map[ProductId, Product](
    ProductId(config.getString("subscriptions.voucher")) -> Voucher,
    ProductId(config.getString("subscriptions.digitalVoucher")) -> DigitalVoucher,
    ProductId(config.getString("subscriptions.delivery")) -> Delivery,
    ProductId(config.getString("subscriptions.nationalDelivery")) -> NationalDelivery,
    ProductId(config.getString("subscriptions.weeklyZoneA")) -> WeeklyZoneA,
    ProductId(config.getString("subscriptions.weeklyZoneB")) -> WeeklyZoneB,
    ProductId(config.getString("subscriptions.weeklyZoneC")) -> WeeklyZoneC,
    ProductId(config.getString("subscriptions.weeklyDomestic")) -> WeeklyDomestic,
    ProductId(config.getString("subscriptions.weeklyRestOfWorld")) -> WeeklyRestOfWorld,
    ProductId(config.getString("subscriptions.digipack")) -> Digipack,
    ProductId(config.getString("subscriptions.supporterPlus")) -> SupporterPlus,
    ProductId(config.getString("subscriptions.tierThree")) -> TierThree,
    ProductId(config.getString("subscriptions.guardianAdLite")) -> GuardianAdLite,
    ProductId(config.getString("membership.supporter")) -> Membership,
    ProductId(config.getString("membership.partner")) -> Membership,
    ProductId(config.getString("membership.patron")) -> Membership,
    ProductId(config.getString("contributions.contributor")) -> Contribution,
    ProductId(config.getString("discounts")) -> Discounts,
    guardianPatronProductId -> GuardianPatron,
  )

}
