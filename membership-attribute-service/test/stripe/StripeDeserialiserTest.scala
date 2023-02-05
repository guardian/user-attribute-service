package stripe

import org.joda.time.LocalDate
import org.specs2.mutable.Specification
import services.stripe.Stripe
import services.stripe.Stripe.Deserializer.{readsEvent, readsSubscription}
import services.stripe.Stripe.{Charge, Event, StripeObject}
import util.Resource
import services.stripe.Stripe._

class StripeDeserialiserTest extends Specification {

  "Stripe deserialiser" should {
    "deserialise a charge event okay" in {
      val event = Resource.getJson("stripe/event.json").as[Event[StripeObject]]
      event.`object`.asInstanceOf[Charge].id mustEqual "chargeid"
      event.`object`.asInstanceOf[Charge].metadata("marketing-opt-in") mustEqual "true"
    }
    "deserialise a failed charge event okay" in {
      val event = Resource.getJson("stripe/failedCharge.json").as[Event[StripeObject]]
      event.`object`.asInstanceOf[Charge].id mustEqual "ch_18zUytRbpG0cjdye76ytdj"
      event.`object`.asInstanceOf[Charge].metadata("marketing-opt-in") mustEqual "false"
      event.`object`.asInstanceOf[Charge].balance_transaction must beNone
    }
    "deserialise a failed charge event okay" in {
      import Stripe.Deserializer.readsError
      val error = Resource.getJson("stripe/error.json").validate[Stripe.Error].get
      error mustEqual Error(
        `type` = "card_error",
        charge = Some("ch_111111111111111111111111"),
        message = Some("Your card was declined."),
        code = Some("card_declined"),
        decline_code = Some("do_not_honor"),
        param = None,
      )
    }
    "deserialise a Stripe subscription (eg. guardian patrons) okay" in {
      val subscription = Resource.getJson("stripe/subscription.json").as[Subscription]
      subscription.id mustEqual "sub_1L8mv1JETvkRwpwqhowvEOlL"
      subscription.created mustEqual LocalDate.parse("2022-06-09")
    }
  }

}
