package services

import cats.effect.IO
import com.gu.identity.auth.{IdapiUserCredentials, InvalidOrExpiredToken, OktaUserCredentials, OktaValidationException}
import com.gu.identity.play.IdentityPlayAuthService
import com.gu.identity.play.IdentityPlayAuthService.UserCredentialsMissingError
import models.AccessScope.readSelf
import models.{ApiError, UserFromToken}
import org.mockito.IdiomaticMockito
import org.mockito.Mockito.when
import org.specs2.mutable.Specification
import play.api.test.FakeRequest

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class IdentityAuthServiceTest extends Specification with IdiomaticMockito {

  "userFromOktaToken" should {

    val requiredScopes = List(readSelf)

    "give user and Okta credentials if Okta token is valid" in {
      val request = FakeRequest()
      val credentials = mock[OktaUserCredentials]
      val user = mock[UserFromToken]
      val identityPlayAuthService = mock[IdentityPlayAuthService]
      when(identityPlayAuthService.validateCredentialsFromRequest[UserFromToken](request, requiredScopes))
        .thenReturn(IO.pure((credentials, user)))
      val identityAuthService = new IdentityAuthService(identityPlayAuthService)
      val result = identityAuthService.userAndCredentials(request, requiredScopes)
      Await.result(result, Duration.Inf) shouldEqual Right(UserAndCredentials(user, credentials))
    }

    "give user and Idapi credentials if Idapi credentials are valid" in {
      val request = FakeRequest()
      val credentials = mock[IdapiUserCredentials]
      val user = mock[UserFromToken]
      val identityPlayAuthService = mock[IdentityPlayAuthService]
      when(identityPlayAuthService.validateCredentialsFromRequest[UserFromToken](request, requiredScopes))
        .thenReturn(IO.pure(credentials, user))
      val identityAuthService = new IdentityAuthService(identityPlayAuthService)
      val result = identityAuthService.userAndCredentials(request, requiredScopes)
      Await.result(result, Duration.Inf) shouldEqual Right(UserAndCredentials(user, credentials))
    }

    "give API error if Okta token is invalid" in {
      val request = FakeRequest()
      val identityPlayAuthService = mock[IdentityPlayAuthService]
      when(identityPlayAuthService.validateCredentialsFromRequest[UserFromToken](request, requiredScopes))
        .thenReturn(IO.raiseError(OktaValidationException(InvalidOrExpiredToken)))
      val identityAuthService = new IdentityAuthService(identityPlayAuthService)
      val result = identityAuthService.userAndCredentials(request, requiredScopes)
      Await.result(result, Duration.Inf) shouldEqual Left(ApiError("Token is invalid or expired", "", 401))
    }

    "give API error if Idapi credentials are invalid" in {
      val request = FakeRequest()
      val identityPlayAuthService = mock[IdentityPlayAuthService]
      when(identityPlayAuthService.validateCredentialsFromRequest[UserFromToken](request, requiredScopes))
        .thenReturn(IO.raiseError(UserCredentialsMissingError("missing")))
      val identityAuthService = new IdentityAuthService(identityPlayAuthService)
      val result = identityAuthService.userAndCredentials(request, requiredScopes)
      Await.result(result, Duration.Inf) shouldEqual Left(ApiError("Unauthorized", "Failed to authenticate", 401))
    }

    "throw an exception if validation failed for some other reason" in {
      val request = FakeRequest()
      val exception = new RuntimeException()
      val identityPlayAuthService = mock[IdentityPlayAuthService]
      when(identityPlayAuthService.validateCredentialsFromRequest[UserFromToken](request, requiredScopes))
        .thenReturn(IO.raiseError(exception))
      val identityAuthService = new IdentityAuthService(identityPlayAuthService)
      val result = identityAuthService.userAndCredentials(request, requiredScopes)
      Await.result(result.failed, Duration.Inf) shouldEqual exception
    }
  }
}
