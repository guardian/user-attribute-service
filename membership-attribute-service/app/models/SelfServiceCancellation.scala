package models

import com.gu.i18n.Country
import com.gu.i18n.Country._
import com.gu.memsub.Product
import com.gu.memsub.Product._

/*
 * this file aims to model https://docs.google.com/spreadsheets/d/1GydjiURBMRk8S_xD4iwbbIBpuXXYI5h3_M87DtRDV8I
 * */
case class SelfServiceCancellation(
    isAllowed: Boolean,
    shouldDisplayEmail: Boolean,
    phoneRegionsToDisplay: List[String],
)

object SelfServiceCancellation {

  private val ukRowPhone = "UK & ROW"
  private val usaPhone = "US"
  private val ausPhone = "AUS"
  private val allPhones = List(ukRowPhone, usaPhone, ausPhone)

  def apply(product: Product, billingCountry: Option[Country]): SelfServiceCancellation = {

    if (isOneOf(product, Membership, Contribution, SupporterPlus, Digipack, TierThree, GuardianAdLite)) {
      SelfServiceCancellation(
        isAllowed = true,
        shouldDisplayEmail = true,
        phoneRegionsToDisplay = allPhones,
      )
    } else if (billingCountry.contains(UK)) {
      SelfServiceCancellation(
        isAllowed = false,
        shouldDisplayEmail = false,
        phoneRegionsToDisplay = List(ukRowPhone),
      )
    } else if (isOneOf(billingCountry, US, Canada)) {
      SelfServiceCancellation(
        isAllowed = true,
        shouldDisplayEmail = true,
        phoneRegionsToDisplay = List(usaPhone),
      )
    } else {
      SelfServiceCancellation(
        isAllowed = true,
        shouldDisplayEmail = true,
        phoneRegionsToDisplay = allPhones,
      )
    }
  }

  private def isOneOf[T](product: T, products: T*): Boolean = products.toSet.contains(product)
  private def isOneOf[T](product: Option[T], products: T*): Boolean = product.isDefined && products.toSet.contains(product.get)
}
