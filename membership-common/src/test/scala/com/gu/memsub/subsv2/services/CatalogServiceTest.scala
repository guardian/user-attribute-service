package com.gu.memsub.subsv2.services
import com.gu.config.SubsV2ProductIds
import com.gu.memsub.Subscription.ProductRatePlanId
import com.gu.monitoring.SafeLogger
import com.gu.okhttp.RequestRunners.HttpClient
import com.gu.zuora.ZuoraRestConfig
import com.gu.zuora.rest.SimpleClient
import com.typesafe.config.ConfigFactory
import io.lemonlabs.uri.typesafe.dsl._
import okhttp3._
import org.specs2.mutable.Specification
import scalaz.Id._
import utils.Resource
import utils.TestLogPrefix.testLogPrefix

class CatalogServiceTest extends Specification {

  "Catalog service" should {

    "Read a catalog in CODE with the config product IDs" in {
      val dev = ConfigFactory.parseResources("touchpoint.CODE.conf")
      val ids = SubsV2ProductIds.load(dev.getConfig("touchpoint.backend.environments.CODE.zuora.productIds"))
      val cats = CatalogService.read(FetchCatalog.fromZuoraApi(CatalogServiceTest.client("rest/Catalog.json")), ids).toOption.get
      val supporterPlus = cats.productRatePlans(ProductRatePlanId("8ad08e1a8586721801858805663f6fab"))
      val supporterPlusMonth = cats.productRatePlans(ProductRatePlanId("8ad08cbd8586721c01858804e3275376"))
      supporterPlus.productType.productTypeString must beEqualTo("Supporter Plus")
      supporterPlus.name must beEqualTo("Supporter Plus V2 - Annual")
      supporterPlusMonth.productType.productTypeString must beEqualTo("Supporter Plus")
      supporterPlusMonth.name must beEqualTo("Supporter Plus V2 - Monthly")

      val tierThreeProductId = ids.find(_._2 == com.gu.memsub.Product.TierThree).get._1 // TODO might need to do this better
      val tierThree = cats.productRatePlans.collect { case (_, plan) if plan.productId == tierThreeProductId => plan }.toList
      tierThree.size must beEqualTo(4)

      val guardianAdLite = cats.productRatePlans(ProductRatePlanId("71a1c43a1e192b28f702b3b47113000a"))
      guardianAdLite.productType.productTypeString must beEqualTo("Guardian Ad-Lite")
      guardianAdLite.name must beEqualTo("Guardian Ad-Lite Monthly")
    }
  }
}

object CatalogServiceTest {

  def client(path: String) = {
    val runner: HttpClient[Id] = new HttpClient[Id] {
      override def execute(request: Request)(implicit logPrefix: SafeLogger.LogPrefix): scalaz.Id.Id[Response] =
        new Response.Builder()
          .request(request)
          .message("test")
          .code(200)
          .body(ResponseBody.create(MediaType.parse("application/json"), Resource.getJson(path).toString))
          .protocol(Protocol.HTTP_1_1)
          .build()
    }

    val restConfig = ZuoraRestConfig("foo", "http://localhost", "joe", "public")
    new SimpleClient[Id](restConfig, runner)
  }

}
