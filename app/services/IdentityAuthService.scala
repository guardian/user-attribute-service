package services

import _root_.play.api.mvc.RequestHeader
import cats.implicits._
import com.gu.identity.IdapiConfig
import com.gu.identity.auth._
import com.gu.identity.play.IdentityPlayAuthService
import com.gu.identity.play.IdentityPlayAuthService.UserCredentialsMissingError
import com.gu.monitoring.SafeLogger
import models.{UserFromToken, UserFromTokenParser}
import org.http4s.Uri
import services.AuthenticationFailure.{Forbidden, Unauthorised}

import scala.concurrent.{ExecutionContext, Future}

class IdentityAuthService(apiConfig: IdapiConfig, oktaTokenValidationConfig: OktaTokenValidationConfig)(implicit ec: ExecutionContext)
    extends AuthenticationService {

  val idApiUrl = Uri.unsafeFromString(apiConfig.url)

  val identityPlayAuthService = {
    val idapiConfig = IdapiAuthConfig(idApiUrl, apiConfig.token, Some("membership"))
    IdentityPlayAuthService.unsafeInit(UserFromTokenParser, idapiConfig, oktaTokenValidationConfig)
  }

  def user(requiredScopes: List[AccessScope])(implicit requestHeader: RequestHeader): Future[Either[AuthenticationFailure, UserFromToken]] = {
    getUser(requestHeader, requiredScopes).attempt
      .map {
        case Left(UserCredentialsMissingError(_)) =>
          // IdentityPlayAuthService throws an error if there is no SC_GU_U cookie or crypto auth token
          // frontend decides to make a request based on the existence of a GU_U cookie, so this case is expected.
          SafeLogger.info(s"unable to authorize user - no token or cookie provided")
          Left(Unauthorised)

        case Left(OktaValidationException(validationError: ValidationError)) =>
          validationError match {
            case MissingRequiredScope(_) =>
              SafeLogger.warn(s"could not validate okta token - $validationError")
              Left(Forbidden)
            case OktaValidationError(originalException) =>
              SafeLogger.warn(s"could not validate okta token - $validationError", originalException)
              Left(Unauthorised)
            case _ =>
              SafeLogger.warn(s"could not validate okta token - $validationError")
              Left(Unauthorised)
          }

        case Left(err) =>
          SafeLogger.warn(s"valid request but expired token or cookie so user must log in again - $err")
          Left(Unauthorised)

        case Right(Some(user)) => Right(user)

        case Right(None) => Left(Unauthorised)
      }
  }

  private def getUser(requestHeader: RequestHeader, requiredScopes: List[AccessScope]): Future[Option[UserFromToken]] =
    identityPlayAuthService
      .getUserClaimsFromRequestOrWithIdapi(requestHeader, requiredScopes)
      .map {
        case (_: OktaUserCredentials, claims) =>
          SafeLogger.warn("Authorised by Okta token")
          Some(claims)
        case (_: IdapiUserCredentials, claims) =>
          SafeLogger.warn("Authorised by Idapi token")
          Some(claims)
      }
      .unsafeToFuture()
}