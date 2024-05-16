package com.gu.salesforce

sealed trait Tier {
  def name: String
  def isPublic: Boolean
  def isPaid: Boolean
}

sealed trait FreeTier extends Tier {
  def isPaid = false
}

sealed trait PaidTier extends Tier {
  def isPaid = true
}

object PaidTier {
  def all = Seq[PaidTier](Tier.Supporter(), Tier.Partner(), Tier.Patron())
}

object Tier {
  case class Staff() extends FreeTier {
    override val name = "Staff"
    override def isPublic = false
  }

  case class Supporter() extends PaidTier {
    override val name = "Supporter"
    override def isPublic = true
  }

  case class Partner() extends PaidTier {
    override val name = "Partner"
    override def isPublic = true
  }

  case class Patron() extends PaidTier {
    override val name = "Patron"
    override def isPublic = true
  }

  // The order of this list is used in Ordered[Tier] above
  lazy val all = FreeTier.all ++ PaidTier.all

  val staff = Staff()
  val supporter = Supporter()
  val partner = Partner()
  val patron = Patron()
}

object FreeTier {
  def all = Seq[FreeTier](Tier.Staff())
}
