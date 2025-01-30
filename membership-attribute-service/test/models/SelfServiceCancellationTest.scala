package models

import com.gu.i18n.Country.{Australia, Canada, Ireland, NewZealand, UK, US}
import com.gu.memsub.Product._
import org.specs2.mutable.Specification

class SelfServiceCancellationTest extends Specification {
  val allCountries = Set(UK, Australia, US, Canada, NewZealand, Ireland)
  val allProducts = Set(
    Membership,
    Contribution,
    SupporterPlus,
    TierThree,
    GuardianAdLite,
    Voucher,
    Delivery,
    DigitalVoucher,
    Digipack,
    GuardianPatron,
    WeeklyDomestic,
    WeeklyRestOfWorld,
    WeeklyZoneA,
    WeeklyZoneB,
    WeeklyZoneC,
  )

  "SelfServiceCancellation.apply" should {

    "allow cancellation for Membership in all countries" in {
      allCountries.toList.map { country =>
        SelfServiceCancellation(Membership, Some(country)).isAllowed shouldEqual true
      }
    }

    "allow cancellation for Contribution in all countries" in {
      allCountries.map { country =>
        SelfServiceCancellation(Contribution, Some(country)).isAllowed shouldEqual true
      }.toList
    }

    "allow cancellation for Tier Three in all countries" in {
      allCountries.map { country =>
        SelfServiceCancellation(TierThree, Some(country)).isAllowed shouldEqual true
      }.toList
    }

    "allow cancellation for Guardian Ad-Lite in all countries" in {
      allCountries.map { country =>
        SelfServiceCancellation(GuardianAdLite, Some(country)).isAllowed shouldEqual true
      }.toList
    }

    "disallow cancellation for all products except Membership, Contribution, Digipack, Tier Three, Guardian Ad-Lite and Supporter Plus in the UK" in {
      allProducts
        .diff(Set(Membership, Contribution, SupporterPlus, Digipack, TierThree, GuardianAdLite))
        .map { product =>
          SelfServiceCancellation(product, Some(UK)).isAllowed shouldEqual false
        }
        .toList
    }

    "allow cancellation for all products in countries other than the UK" in {
      (for {
        product <- allProducts
        country <- allCountries.diff(Set(UK))
      } yield SelfServiceCancellation(product, Some(country)).isAllowed shouldEqual true).toList
    }

    "allow cancellation for all products if country is undefined" in {
      allProducts.toList
        .map { product =>
          SelfServiceCancellation(product, None).isAllowed shouldEqual true
        }
    }
  }
}
