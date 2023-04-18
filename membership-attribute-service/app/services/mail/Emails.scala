package services.mail

import com.gu.i18n.Currency
import com.gu.memsub.BillingPeriod.RecurringPeriod
import com.gu.memsub.subsv2.SubscriptionPlan
import com.gu.salesforce.Contact
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat

import java.text.DecimalFormat

object Emails {
  private val dateFormat = DateTimeFormat.forPattern("d MMMM yyyy")

  def paymentMethodChangedEmail(emailAddress: String, contact: Contact, paymentMethod: PaymentType, plan: SubscriptionPlan.AnyPlan): EmailData = {
    EmailData(
      emailAddress = emailAddress,
      salesforceContactId = contact.salesforceContactId,
      campaignName = "payment-method-changed-email",
      dataPoints = Map(
        "first_name" -> contact.firstName.getOrElse(""),
        "last_name" -> contact.lastName,
        "payment_method" -> paymentMethod.valueForEmail,
        "product_type" -> plan.productName,
      ),
    )
  }

  def subscriptionCancelledEmail(
      emailAddress: String,
      contact: Contact,
      plan: SubscriptionPlan.AnyPlan,
      cancellationEffectiveDate: Option[LocalDate],
  ): EmailData = {
    EmailData(
      emailAddress,
      contact.salesforceContactId,
      "subscription-cancelled-email",
      Map(
        "first_name" -> "Frank",
        "last_name" -> "Poole",
        "product_type" -> plan.productName,
      ) ++ cancellationEffectiveDate.map("cancellation_effective_date" -> dateFormat.print(_)),
    )
  }

  def updateAmountEmail(
      email: String,
      contact: Contact,
      newPrice: BigDecimal,
      currency: Currency,
      billingPeriod: RecurringPeriod,
      nextPaymentDate: LocalDate,
  ): EmailData = {
    EmailData(
      email,
      contact.salesforceContactId,
      "payment-amount-changed-email",
      Map(
        "first_name" -> contact.firstName.getOrElse(""),
        "last_name" -> contact.lastName,
        "new_amount" -> decimalFormat.format(newPrice),
        "currency" -> currency.iso,
        "frequency" -> billingPeriod.noun,
        "next_payment_date" -> dateFormat.print(nextPaymentDate),
      ),
    )
  }

  val decimalFormat = {
    val format = new DecimalFormat()
    format.setMinimumFractionDigits(2)
    format.setMaximumFractionDigits(2)
    format
  }
}

class PaymentType(val valueForEmail: String)
case object Card extends PaymentType("card")
case object DirectDebit extends PaymentType("direct_debit")