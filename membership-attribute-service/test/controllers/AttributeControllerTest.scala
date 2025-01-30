package controllers

import actions.{AuthAndBackendRequest, AuthenticatedUserAndBackendRequest, CommonActions, HowToHandleRecencyOfSignedIn}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import com.gu.identity.auth.AccessScope
import com.gu.identity.{RedirectAdviceResponse, SignedInRecently}
import com.gu.monitoring.SafeLogger.LogPrefix
import com.typesafe.config.ConfigFactory
import components.{TouchpointBackends, TouchpointComponents}
import configuration.{CreateTestUsernames, Stage}
import filters.{AddGuIdentityHeaders, TestUserChecker}
import models.{Attributes, FeastApp, MobileSubscriptionStatus, UserFromToken}
import monitoring.CreateNoopMetrics
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.LocalDate.now
import org.mockito.IdiomaticMockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.AuthenticationFailure.Unauthorised
import services.{AuthenticationFailure, AuthenticationService, FakePostgresService, MobileSubscriptionService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AttributeControllerTest extends Specification with AfterAll with IdiomaticMockito {

  implicit val as: ActorSystem = ActorSystem("test")

  private val dateTimeInTheFuture = DateTime.now().plusDays(1)
  private val dateBeforeFeastLaunch = LocalDate.parse("2024-07-10")
  private val validUserId = "1"
  private val userWithoutAttributesUserId = "2"
  private val userWithRecurringContributionUserId = "3"
  private val userWithLiveAppUserId = "4"
  private val userWithNewspaperUserId = "5"
  private val userWithNewspaperPlusUserId = "6"
  private val userWithGuardianWeeklyUserId = "7"
  private val unvalidatedEmailUserId = "8"
  private val userWithTierThreeId = "9"
  private val userWithGuardianAdLiteUserId = "10"

  private val testAttributes = Attributes(
    UserId = validUserId,
    Tier = Some("patron"),
    MembershipJoinDate = Some(new LocalDate(2017, 5, 13)),
    RecurringContributionPaymentPlan = Some("Monthly Contribution"),
    DigitalSubscriptionExpiryDate = Some(new LocalDate(2100, 1, 1)),
    PaperSubscriptionExpiryDate = Some(new LocalDate(2099, 1, 1)),
    GuardianWeeklySubscriptionExpiryDate = Some(new LocalDate(2099, 1, 1)),
    SupporterPlusExpiryDate = Some(new LocalDate(2024, 1, 1)),
    GuardianAdLiteExpiryDate = Some(new LocalDate(2024, 1, 1)),
    RecurringContributionAcquisitionDate = Some(dateBeforeFeastLaunch),
  )
  private val recurringContributionOnlyAttributes = Attributes(
    UserId = userWithRecurringContributionUserId,
    RecurringContributionPaymentPlan = Some("Monthly Contribution"),
    RecurringContributionAcquisitionDate = Some(dateBeforeFeastLaunch),
  )

  private val newspaperOnlyAttributes = Attributes(
    UserId = userWithNewspaperUserId,
    PaperSubscriptionExpiryDate = Some(dateTimeInTheFuture.toLocalDate),
  )
  private val newspaperPlusAttributes = Attributes(
    UserId = userWithNewspaperPlusUserId,
    PaperSubscriptionExpiryDate = Some(dateTimeInTheFuture.toLocalDate),
    DigitalSubscriptionExpiryDate = Some(dateTimeInTheFuture.toLocalDate),
  )
  private val guardianWeeklyOnlyAttributes = Attributes(
    UserId = userWithGuardianWeeklyUserId,
    GuardianWeeklySubscriptionExpiryDate = Some(dateTimeInTheFuture.toLocalDate),
  )

  private val tierThreeAttributes = Attributes(
    UserId = userWithTierThreeId,
    SupporterPlusExpiryDate = Some(dateTimeInTheFuture.toLocalDate),
    GuardianWeeklySubscriptionExpiryDate = Some(dateTimeInTheFuture.toLocalDate),
  )

  private val guardianAdLiteAttributes = Attributes(
    UserId = userWithGuardianAdLiteUserId,
    GuardianAdLiteExpiryDate = Some(dateTimeInTheFuture.toLocalDate),
  )

  private val validUserCookie = Cookie("validUser", "true")
  private val validUnvalidatedEmailCookie = Cookie("unvalidatedEmailUser", "true")
  private val userWithoutAttributesCookie = Cookie("invalidUser", "true")
  private val recurringContributorCookie = Cookie("recurringContributor", "true")
  private val liveAppCookie = Cookie("liveApp", "true")
  private val newspaperCookie = Cookie("newspaper", "true")
  private val newspaperPlusCookie = Cookie("newspaperPlus", "true")
  private val guardianWeeklyCookie = Cookie("guardianWeekly", "true")
  private val tierThreeCookie = Cookie("tierThree", "true")
  private val guardianAdLiteCookie = Cookie("guardianAdLite", "true")
  private val validUser = UserFromToken(
    primaryEmailAddress = "test@thegulocal.com",
    identityId = validUserId,
    userEmailValidated = Some(true),
    authTime = None,
  )
  private val unvalidatedEmailUser = UserFromToken(
    primaryEmailAddress = "unvalidatedEmail@thegulocal.com",
    identityId = unvalidatedEmailUserId,
    userEmailValidated = Some(false),
    authTime = None,
  )
  private val userWithoutAttributes = UserFromToken(
    primaryEmailAddress = "notcached@thegulocal.com",
    identityId = userWithoutAttributesUserId,
    authTime = None,
  )
  private val userWithRecurringContribution = UserFromToken(
    primaryEmailAddress = "recurringContribution@thegulocal.com",
    identityId = userWithRecurringContributionUserId,
    authTime = None,
  )
  private val userWithLiveApp = UserFromToken(
    primaryEmailAddress = "liveapp@thegulocal.com",
    identityId = userWithLiveAppUserId,
    authTime = None,
  )
  private val userWithNewspaper = UserFromToken(
    primaryEmailAddress = "newspaper@thegulocal.com",
    identityId = userWithNewspaperUserId,
    authTime = None,
  )

  private val userWithNewspaperPlus = UserFromToken(
    primaryEmailAddress = "newspaperPlus@thegulocal.com",
    identityId = userWithNewspaperPlusUserId,
    authTime = None,
  )

  private val userWithGuardianWeekly = UserFromToken(
    primaryEmailAddress = "GuardianWeekly@thegulocal.com",
    identityId = userWithGuardianWeeklyUserId,
    authTime = None,
  )

  private val userWithTierThree = UserFromToken(
    primaryEmailAddress = "TierThree@thegulocal.com",
    identityId = userWithTierThreeId,
    authTime = None,
  )

  private val userWithGuardianAdLite = UserFromToken(
    primaryEmailAddress = "GuardianAdLite@thegulocal.com",
    identityId = userWithGuardianAdLiteUserId,
    authTime = None,
  )

  private val guardianEmployeeUser = UserFromToken(
    primaryEmailAddress = "foo@guardian.co.uk",
    identityId = "1234321",
    userEmailValidated = Some(true),
    authTime = None,
  )
  private val guardianEmployeeCookie = Cookie("employeeDigiPackHack", "true")

  private val guardianEmployeeUserTheguardian = UserFromToken(
    primaryEmailAddress = "foo@theguardian.com",
    identityId = "123theguardiancom",
    userEmailValidated = Some(true),
    authTime = None,
  )
  private val guardianEmployeeCookieTheguardian = Cookie("employeeDigiPackHackTheguardian", "true")

  private val validEmployeeUser = UserFromToken(
    primaryEmailAddress = "bar@theguardian.com",
    identityId = "userWithRealProducts",
    userEmailValidated = Some(true),
    authTime = None,
  )
  private val validEmployeeUserCookie = Cookie("userWithRealProducts", "true")

  private val fakeAuthService = new AuthenticationService {
    override def user(requiredScopes: List[AccessScope])(implicit request: RequestHeader): Future[Either[AuthenticationFailure, UserFromToken]] =
      request.cookies.headOption match {
        case Some(c) if c == validUserCookie => Future.successful(Right(validUser))
        case Some(c) if c == validUnvalidatedEmailCookie => Future.successful(Right(unvalidatedEmailUser))
        case Some(c) if c == userWithoutAttributesCookie => Future.successful(Right(userWithoutAttributes))
        case Some(c) if c == recurringContributorCookie => Future.successful(Right(userWithRecurringContribution))
        case Some(c) if c == liveAppCookie => Future.successful(Right(userWithLiveApp))
        case Some(c) if c == newspaperCookie => Future.successful(Right(userWithNewspaper))
        case Some(c) if c == newspaperPlusCookie => Future.successful(Right(userWithNewspaperPlus))
        case Some(c) if c == guardianWeeklyCookie => Future.successful(Right(userWithGuardianWeekly))
        case Some(c) if c == tierThreeCookie => Future.successful(Right(userWithTierThree))
        case Some(c) if c == guardianAdLiteCookie => Future.successful(Right(userWithGuardianAdLite))
        case Some(c) if c == guardianEmployeeCookie => Future.successful(Right(guardianEmployeeUser))
        case Some(c) if c == guardianEmployeeCookieTheguardian => Future.successful(Right(guardianEmployeeUserTheguardian))
        case Some(c) if c == validEmployeeUserCookie => Future.successful(Right(validEmployeeUser))
        case _ => Future.successful(Left(Unauthorised))
      }
  }

  val config = ConfigFactory.load()

  private object FakeAuthAndBackendViaAuthLibAction extends ActionRefiner[Request, AuthenticatedUserAndBackendRequest] {
    override val executionContext = scala.concurrent.ExecutionContext.global
    override protected def refine[A](request: Request[A]): Future[Either[Result, AuthenticatedUserAndBackendRequest[A]]] = {
      object components extends TouchpointComponents(Stage("PROD"), CreateNoopMetrics, config)

      fakeAuthService
        .user(requiredScopes = Nil)(request)
        .map(_.map(new AuthenticatedUserAndBackendRequest[A](_, components, request)).left.map(_ => Results.Unauthorized))

    }
  }

  private object FakeAuthAndBackendViaIdapiAction extends ActionRefiner[Request, AuthAndBackendRequest] {
    override val executionContext = scala.concurrent.ExecutionContext.global
    override protected def refine[A](request: Request[A]): Future[Either[Result, AuthAndBackendRequest[A]]] = {

      object components extends TouchpointComponents(Stage("PROD"), CreateNoopMetrics, config)

      val redirectAdviceResponse = RedirectAdviceResponse(SignedInRecently, None, None, None, None)

      Future(Right(new AuthAndBackendRequest[A](redirectAdviceResponse, components, request)))
    }
  }

  private val actorSystem = ActorSystem()
  private val materializer = Materializer(actorSystem)

  private val touchpointBackends = new TouchpointBackends(actorSystem, ConfigFactory.load(), CreateNoopMetrics)
  private val stubParser = Helpers.stubBodyParser(AnyContent("test"))
  private val ex = scala.concurrent.ExecutionContext.global
  private val testUsers = CreateTestUsernames.from(config)
  private val testUserChecker = new TestUserChecker(testUsers)
  private val commonActions =
    new CommonActions(touchpointBackends, stubParser, testUserChecker)(scala.concurrent.ExecutionContext.global, materializer) {
      override def AuthorizeForScopes(requiredScopes: List[AccessScope]) = NoCacheAction andThen FakeAuthAndBackendViaAuthLibAction
      override def AuthorizeForRecentLogin(howToHandleRecencyOfSignedIn: HowToHandleRecencyOfSignedIn, requiredScopes: List[AccessScope]) =
        NoCacheAction andThen FakeAuthAndBackendViaIdapiAction
    }

  object FakeMobileSubscriptionService extends MobileSubscriptionService {
    override def getSubscriptionStatusForUser(
        identityId: String,
    )(implicit logPrefix: LogPrefix): Future[Either[String, Option[MobileSubscriptionStatus]]] = {
      if (identityId == userWithLiveAppUserId)
        Future.successful(Right(Some(MobileSubscriptionStatus(valid = true, dateTimeInTheFuture))))
      else
        Future.successful(Right(None))
    }

  }

  private val addGuIdentityHeaders = new AddGuIdentityHeaders(touchpointBackends.normal.identityAuthService, testUserChecker)

  private val controller =
    new AttributeController(
      commonActions,
      Helpers.stubControllerComponents(),
      FakePostgresService(validUserId),
      FakeMobileSubscriptionService,
      addGuIdentityHeaders,
      CreateNoopMetrics,
    ) {
      override val executionContext = scala.concurrent.ExecutionContext.global
      override def getSupporterProductDataAttributes(
          identityId: String,
      )(implicit request: AuthenticatedUserAndBackendRequest[AnyContent]): Future[(String, Option[Attributes])] = Future {
        if (identityId == validUserId || identityId == validEmployeeUser.identityId)
          ("Zuora", Some(testAttributes))
        else if (identityId == userWithRecurringContributionUserId) {
          ("Zuora", Some(recurringContributionOnlyAttributes))
        } else if (identityId == userWithLiveAppUserId) {
          ("Zuora", Some(Attributes(UserId = userWithLiveAppUserId)))
        } else if (identityId == userWithNewspaperUserId) {
          ("Zuora", Some(newspaperOnlyAttributes))
        } else if (identityId == userWithNewspaperPlusUserId) {
          ("Zuora", Some(newspaperPlusAttributes))
        } else if (identityId == userWithGuardianWeeklyUserId) {
          ("Zuora", Some(guardianWeeklyOnlyAttributes))
        } else if (identityId == userWithTierThreeId) {
          ("Zuora", Some(tierThreeAttributes))
        } else if (identityId == userWithGuardianAdLiteUserId) {
          ("Zuora", Some(guardianAdLiteAttributes))
        } else
          ("Zuora", None)
      }
    }

  private def verifyDefaultFeaturesResult(result: Future[Result]) = {
    status(result) shouldEqual OK
    val jsonBody = contentAsJson(result)
    jsonBody shouldEqual
      Json.parse("""
                   | {
                   |   "adblockMessage": true
                   | }
                 """.stripMargin)
  }

  private def verifySuccessfulFeaturesResult(result: Future[Result]) = {
    status(result) shouldEqual OK
    val jsonBody = contentAsJson(result)
    jsonBody shouldEqual
      Json.parse(s"""
                   | {
                   |   "userId": "$validUserId",
                   |   "adblockMessage": false,
                   |   "membershipJoinDate": "2017-05-13"
                   | }
                 """.stripMargin)
  }

  private def verifySuccessfulMembershipResult(result: Future[Result]) = {
    status(result) shouldEqual OK
    val jsonBody = contentAsJson(result)
    jsonBody shouldEqual
      Json.parse(s"""
        | {
        |   "tier": "patron",
        |   "userId": "$validUserId",
        |   "contentAccess": {
        |     "member": true,
        |     "paidMember": true
        |   }
        | }
      """.stripMargin)
  }

  private def verifyIdentityHeadersSet(result: Future[Result], expectedUserId: String, expectedTestUser: Boolean = false) = {
    val resultHeaders = headers(result)
    resultHeaders.get("X-Gu-Identity-Id") should beSome(expectedUserId)
    resultHeaders.get("X-Gu-Membership-Test-User") should beSome(expectedTestUser.toString)

  }

  private def verifySuccessfulAttributesResult(result: Future[Result]) = {
    status(result) shouldEqual OK
    val jsonBody = contentAsJson(result)
    jsonBody shouldEqual
      Json.parse(s"""
                   | {
                   |   "tier": "patron",
                   |   "userId": "$validUserId",
                   |   "membershipJoinDate": "2017-05-13",
                   |   "recurringContributionPaymentPlan":"Monthly Contribution",
                   |   "digitalSubscriptionExpiryDate":"2100-01-01",
                   |   "paperSubscriptionExpiryDate":"2099-01-01",
                   |   "guardianWeeklyExpiryDate":"2099-01-01",
                   |   "guardianAdLiteExpiryDate":"2024-01-01",
                   |   "recurringContributionAcquisitionDate":"$dateBeforeFeastLaunch",
                   |   "showSupportMessaging": false,
                   |   "contentAccess": {
                   |     "member": true,
                   |     "paidMember": true,
                   |     "recurringContributor": true,
                   |     "supporterPlus":false,
                   |     "feast":true,
                   |     "digitalPack": true,
                   |     "paperSubscriber": true,
                   |     "guardianWeeklySubscriber": true,
                   |     "guardianPatron": false,
                   |     "guardianAdLite":false
                   |   }
                   | }
                 """.stripMargin)
  }

  private def verifySuccessfulOneOffContributionsResult(result: Future[Result]) = {
    status(result) shouldEqual OK
    val jsonBody = contentAsJson(result)
    jsonBody shouldEqual
      Json.parse("""[
                   | {
                   |   "created":1638057600000,
                   |   "currency":"GBP",
                   |   "price":11,
                   |   "status":"statusValue"
                   | }
                   | ]
                 """.stripMargin)
  }

  "getMyMembershipAttributesFeatures" should {
    "return unauthorised when cookies not provided" in {
      val req = FakeRequest()
      val result1 = controller.membership(req)
      val result2 = controller.attributes(req)
      val result3 = controller.features(req)
      val result4 = controller.oneOffContributions(req)

      status(result1) shouldEqual UNAUTHORIZED
      status(result2) shouldEqual UNAUTHORIZED
      status(result3) shouldEqual UNAUTHORIZED
      status(result4) shouldEqual UNAUTHORIZED
    }

    "return not found and set identity headers for unknown users in membership" in {
      val req = FakeRequest().withCookies(userWithoutAttributesCookie)
      val result = controller.membership(req)

      status(result) shouldEqual NOT_FOUND
      verifyIdentityHeadersSet(result, userWithoutAttributesUserId)
    }

    "return all false attributes and set identity headers for unknown users" in {
      val req = FakeRequest().withCookies(userWithoutAttributesCookie)
      val result = controller.attributes(req)

      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      jsonBody shouldEqual
        Json.parse(s"""
                       |{
                       |  "userId": "$userWithoutAttributesUserId",
                       |  "showSupportMessaging": true,
                       |  "feastIosSubscriptionGroup": "${FeastApp.IosSubscriptionGroupIds.IntroductoryOffer}",
                       |  "feastAndroidOfferTags": ["${FeastApp.AndroidOfferTags.IntroductoryOffer}"],
                       |  "contentAccess": {
                       |    "member": false,
                       |    "paidMember": false,
                       |    "recurringContributor": false,
                       |    "supporterPlus" : false,
                       |    "feast": false,
                       |    "digitalPack": false,
                       |    "paperSubscriber": false,
                       |    "guardianWeeklySubscriber": false,
                       |    "guardianPatron": false,
                       |    "guardianAdLite":false
                       |  }
                       |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithoutAttributesUserId)

    }

    "return the correct feast attributes for recurring contributors who signed up before feast launch" in {
      val req = FakeRequest().withCookies(recurringContributorCookie)
      val result = controller.attributes(req)
      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      jsonBody shouldEqual
        Json.parse(s"""
               |{
               |  "userId": "$userWithRecurringContributionUserId",
               |  "showSupportMessaging": false,
               |  "feastIosSubscriptionGroup": "${FeastApp.IosSubscriptionGroupIds.IntroductoryOffer}",
               |  "feastAndroidOfferTags": ["${FeastApp.AndroidOfferTags.IntroductoryOffer}"],
               |  "recurringContributionPaymentPlan":"Monthly Contribution",
               |  "recurringContributionAcquisitionDate":"$dateBeforeFeastLaunch",
               |  "contentAccess": {
               |    "member": false,
               |    "paidMember": false,
               |    "recurringContributor": true,
               |    "supporterPlus" : false,
               |    "feast": false,
               |    "digitalPack": false,
               |    "paperSubscriber": false,
               |    "guardianWeeklySubscriber": false,
               |    "guardianPatron": false,
               |    "guardianAdLite": false
               |  }
               |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithRecurringContributionUserId)

    }

    "return the correct feast attributes for live app subscribers" in {
      val req = FakeRequest().withCookies(liveAppCookie)
      val result = controller.attributes(req)

      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      jsonBody shouldEqual
        Json.parse(s"""
               |{
               |  "userId": "$userWithLiveAppUserId",
               |  "liveAppSubscriptionExpiryDate":"${dateTimeInTheFuture.toLocalDate}",
               |  "showSupportMessaging": false,
               |  "feastIosSubscriptionGroup": "${FeastApp.IosSubscriptionGroupIds.IntroductoryOffer}",
               |  "feastAndroidOfferTags": ["${FeastApp.AndroidOfferTags.IntroductoryOffer}"],
               |  "contentAccess": {
               |    "member": false,
               |    "paidMember": false,
               |    "recurringContributor": false,
               |    "supporterPlus" : false,
               |    "feast": false,
               |    "digitalPack": false,
               |    "paperSubscriber": false,
               |    "guardianWeeklySubscriber": false,
               |    "guardianPatron": false,
               |    "guardianAdLite": false
               |  }
               |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithLiveAppUserId)

    }

    "return the correct feast attributes for newspaper subscribers" in {
      val req = FakeRequest().withCookies(newspaperCookie)
      val result = controller.attributes(req)

      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      println(Json.prettyPrint(jsonBody))
      jsonBody shouldEqual
        Json.parse(s"""
               |{
               |  "userId": "$userWithNewspaperUserId",
               |  "paperSubscriptionExpiryDate":"${dateTimeInTheFuture.toLocalDate}",
               |  "showSupportMessaging": false,
               |  "contentAccess": {
               |    "member": false,
               |    "paidMember": false,
               |    "recurringContributor": false,
               |    "supporterPlus" : false,
               |    "feast": true,
               |    "digitalPack": true,
               |    "paperSubscriber": true,
               |    "guardianWeeklySubscriber": false,
               |    "guardianPatron": false,
               |    "guardianAdLite":false
               |  }
               |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithNewspaperUserId)

    }

    "return the correct feast attributes for newspaper plus subscribers" in {
      val req = FakeRequest().withCookies(newspaperPlusCookie)
      val result = controller.attributes(req)

      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      println(Json.prettyPrint(jsonBody))
      jsonBody shouldEqual
        Json.parse(s"""
                        |{
                        |  "userId": "$userWithNewspaperPlusUserId",
                        |  "digitalSubscriptionExpiryDate":"${dateTimeInTheFuture.toLocalDate}",
                        |  "paperSubscriptionExpiryDate":"${dateTimeInTheFuture.toLocalDate}",
                        |  "showSupportMessaging": false,
                        |  "contentAccess": {
                        |    "member": false,
                        |    "paidMember": false,
                        |    "recurringContributor": false,
                        |    "supporterPlus" : false,
                        |    "feast": true,
                        |    "digitalPack": true,
                        |    "paperSubscriber": true,
                        |    "guardianWeeklySubscriber": false,
                        |    "guardianPatron": false,
                        |    "guardianAdLite":false
                        |  }
                        |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithNewspaperPlusUserId)

    }

    "return the correct feast attributes for Guardian Weekly subscribers" in {
      val req = FakeRequest().withCookies(guardianWeeklyCookie)
      val result = controller.attributes(req)

      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      println(Json.prettyPrint(jsonBody))
      jsonBody shouldEqual
        Json.parse(s"""
               |{
               |  "userId": "$userWithGuardianWeeklyUserId",
               |  "guardianWeeklyExpiryDate":"${dateTimeInTheFuture.toLocalDate}",
               |  "showSupportMessaging": false,
               |  "feastIosSubscriptionGroup": "${FeastApp.IosSubscriptionGroupIds.IntroductoryOffer}",
               |  "feastAndroidOfferTags": ["${FeastApp.AndroidOfferTags.IntroductoryOffer}"],
               |  "contentAccess": {
               |    "member": false,
               |    "paidMember": false,
               |    "recurringContributor": false,
               |    "supporterPlus" : false,
               |    "feast": false,
               |    "digitalPack": false,
               |    "paperSubscriber": false,
               |    "guardianWeeklySubscriber": true,
               |    "guardianPatron": false,
               |    "guardianAdLite":false
               |  }
               |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithGuardianWeeklyUserId)

    }

    "return the correct attributes for Tier Three subscribers" in {
      val req = FakeRequest().withCookies(tierThreeCookie)
      val result = controller.attributes(req)

      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      println(Json.prettyPrint(jsonBody))
      jsonBody shouldEqual
        Json.parse(s"""
             |{
             |  "userId": "$userWithTierThreeId",
             |  "guardianWeeklyExpiryDate":"${dateTimeInTheFuture.toLocalDate}",
             |  "showSupportMessaging": false,
             |  "contentAccess": {
             |    "member": false,
             |    "paidMember": false,
             |    "recurringContributor": false,
             |    "supporterPlus" : true,
             |    "feast": true,
             |    "digitalPack": true,
             |    "paperSubscriber": false,
             |    "guardianWeeklySubscriber": true,
             |    "guardianPatron": false,
             |    "guardianAdLite":false
             |  }
             |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithTierThreeId)

    }

    "return the correct Guardian Ad-Lite attributes for Guardian Ad-Lite subscribers" in {
      val req = FakeRequest().withCookies(guardianAdLiteCookie)
      val result = controller.attributes(req)

      status(result) shouldEqual OK
      val jsonBody = contentAsJson(result)
      jsonBody shouldEqual
        Json.parse(s"""
             |{
             |  "userId": "$userWithGuardianAdLiteUserId",
             |  "guardianAdLiteExpiryDate":"${dateTimeInTheFuture.toLocalDate}",
             |  "showSupportMessaging": true,
             |  "feastIosSubscriptionGroup": "${FeastApp.IosSubscriptionGroupIds.IntroductoryOffer}",
             |  "feastAndroidOfferTags": ["${FeastApp.AndroidOfferTags.IntroductoryOffer}"],
             |  "contentAccess": {
             |    "member": false,
             |    "paidMember": false,
             |    "recurringContributor": false,
             |    "supporterPlus" : false,
             |    "feast": false,
             |    "digitalPack": false,
             |    "paperSubscriber": false,
             |    "guardianWeeklySubscriber": false,
             |    "guardianPatron": false,
             |    "guardianAdLite": true
             |  }
             |}""".stripMargin)
      verifyIdentityHeadersSet(result, userWithGuardianAdLiteUserId)

    }

    "retrieve default features and set identity headers for unknown users" in {
      val req = FakeRequest().withCookies(userWithoutAttributesCookie)

      val result = controller.features(req)
      verifyDefaultFeaturesResult(result)
      verifyIdentityHeadersSet(result, userWithoutAttributesUserId)

    }

    "retrieve features and set identity headers for user in cookie" in {
      val req = FakeRequest().withCookies(validUserCookie)
      val result: Future[Result] = controller.features(req)

      verifySuccessfulFeaturesResult(result)
      verifyIdentityHeadersSet(result, validUser.identityId)

    }

    "retrieve membership attributes and set identity headers for user in cookie" in {
      val req = FakeRequest().withCookies(validUserCookie)
      val result: Future[Result] = controller.membership(req)

      verifySuccessfulMembershipResult(result)
      verifyIdentityHeadersSet(result, validUser.identityId)

    }

    "retrieve all the attributes and set identity headers for user in cookie" in {
      val req = FakeRequest().withCookies(validUserCookie)
      val result: Future[Result] = controller.attributes(req)

      verifySuccessfulAttributesResult(result)
      verifyIdentityHeadersSet(result, validUser.identityId)

    }

    "return unauthorised and set identity headers when attempting to retrieve one off contributions for user with a non validated email" in {
      val req = FakeRequest().withCookies(validUnvalidatedEmailCookie)
      val result: Future[Result] = controller.oneOffContributions(req)
      status(result) shouldEqual 401
      verifyIdentityHeadersSet(result, unvalidatedEmailUser.identityId)
    }

    "return one off contributions and set identity headers for user with a validated email" in {
      val req = FakeRequest().withCookies(validUserCookie)
      val result: Future[Result] = controller.oneOffContributions(req)

      verifySuccessfulOneOffContributionsResult(result)
      verifyIdentityHeadersSet(result, validUser.identityId)
    }

    "return unauthorised and set identity headers for user with a validated email but not contributions" in {
      val req = FakeRequest().withCookies(validUserCookie)
      val result: Future[Result] = controller.oneOffContributions(req)

      verifySuccessfulOneOffContributionsResult(result)
      verifyIdentityHeadersSet(result, validUser.identityId)
    }

    val digipackAllowEmployeeAccessDateHack = Some(new LocalDate(2999, 1, 1))
    "allow DigiPack access via hack to guardian employees with validated guardian.co.uk email" in {
      val req = FakeRequest().withCookies(guardianEmployeeCookie)
      val defaultAttribsWithDigipackOverride =
        Attributes(guardianEmployeeUser.identityId)
          .copy(DigitalSubscriptionExpiryDate = digipackAllowEmployeeAccessDateHack)
      contentAsJson(controller.attributes(req)) shouldEqual Json.toJson(defaultAttribsWithDigipackOverride)
    }

    "allow DigiPack access via hack to guardian employees with validated theguardian.com email" in {
      val req = FakeRequest().withCookies(guardianEmployeeCookieTheguardian)
      val defaultAttribsWithDigipackOverride =
        Attributes(guardianEmployeeUserTheguardian.identityId)
          .copy(DigitalSubscriptionExpiryDate = digipackAllowEmployeeAccessDateHack)
      contentAsJson(controller.attributes(req)) shouldEqual Json.toJson(defaultAttribsWithDigipackOverride)
    }

    "allow DigiPack access via hack to guardian employees with affecting other products" in {
      val req = FakeRequest().withCookies(validEmployeeUserCookie)
      contentAsJson(controller.attributes(req)) shouldEqual
        Json.toJson(testAttributes.copy(DigitalSubscriptionExpiryDate = digipackAllowEmployeeAccessDateHack))
    }

  }

  override def afterAll(): Unit = as.terminate()
}
