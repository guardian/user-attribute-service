import sbt._
import play.sbt.PlayImport

object Dependencies {

  val awsClientVersion = "1.12.387"
  val awsClientV2Version = "2.16.104"

  val playJsonVersion = "2.9.4"

  val sentryLogback = "io.sentry" % "sentry-logback" % "1.7.5"
  val identityAuth = "com.gu.identity" %% "identity-auth-play" % "4.9"
  val supportInternationalisation = "com.gu" %% "support-internationalisation" % "0.16"
  val identityTestUsers = "com.gu" %% "identity-test-users" % "0.8"
  val postgres = "org.postgresql" % "postgresql" % "42.5.1"
  val jdbc = PlayImport.jdbc
  val playWS = PlayImport.ws
  val playFilters = PlayImport.filters
  val playJson = "com.typesafe.play" %% "play-json" % playJsonVersion
  val playJsonJoda = "com.typesafe.play" %% "play-json-joda" % playJsonVersion
  val specs2 = PlayImport.specs2 % Test
  val specs2MatchersExtra = "org.specs2" %% "specs2-matcher-extra" % "4.5.1" % Test
  val scanamo = "org.scanamo" %% "scanamo" % "1.0.0-M23"
  val awsDynamo = "software.amazon.awssdk" % "dynamodb" % awsClientV2Version
  val awsSQS = "com.amazonaws" % "aws-java-sdk-sqs" % awsClientVersion
  val awsCloudWatch = "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsClientVersion
  val scalaz = "org.scalaz" %% "scalaz-core" % "7.3.7"
  val kinesis = "com.gu" % "kinesis-logback-appender" % "2.0.3"
  val logstash = "net.logstash.logback" % "logstash-logback-encoder" % "4.9"
  val anorm = "org.playframework.anorm" %% "anorm" % "2.7.0"
  val netty = "io.netty" % "netty-codec" % "4.1.87.Final"
  val nettyHttp = "io.netty" % "netty-codec-http" % "4.1.87.Final"
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
  val mockServer = "org.mock-server" % "mockserver-netty" % "5.14.0" % Test
  val mockitoScala = "org.mockito" %% "mockito-scala" % "1.17.12" % Test

  val jacksonVersion = "2.14.1"
  val jacksonDatabindVersion = "2.14.1"
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % "10.2.9"
  val oktaJwtVerifierVersion = "0.5.7"
  val jackson = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion,
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  )
  val oktaJwtVerifier = Seq(
    "com.okta.jwt" % "okta-jwt-verifier" % oktaJwtVerifierVersion,
    "com.okta.jwt" % "okta-jwt-verifier-impl" % oktaJwtVerifierVersion,
  )
  val unirest = "com.konghq" % "unirest-java" % "4.0.0-RC2" % Test
  val scalaUri = "io.lemonlabs" %% "scala-uri" % "2.2.0"

  val awsS3 = "com.amazonaws" % "aws-java-sdk-s3" % awsClientVersion
  val dynamoDB = "com.amazonaws" % "aws-java-sdk-dynamodb" % awsClientVersion
  val libPhoneNumber = "com.googlecode.libphonenumber" % "libphonenumber" % "8.13.4"

  // projects

  val apiDependencies = Seq(
    jdbc,
    postgres,
    sentryLogback,
    identityAuth,
    identityTestUsers,
    playWS,
    playFilters,
    scanamo,
    awsDynamo,
    awsSQS,
    awsCloudWatch,
    scalaz,
    specs2.exclude("org.specs2", "specs2-mock_2.13"),
    specs2MatchersExtra,
    kinesis,
    logstash,
    anorm,
    "com.amazonaws" % "aws-java-sdk-autoscaling" % awsClientVersion,
    netty,
    nettyHttp,
    "com.google.guava" % "guava" % "30.1.1-jre", // until https://github.com/playframework/playframework/pull/10874
    akkaHttpCore,
    unirest,
    mockServer,
    mockitoScala,
    supportInternationalisation,
    scalaUri,
    awsS3,
    playJson,
    playJsonJoda,
    libPhoneNumber,
    dynamoDB,
  ) ++ jackson ++ oktaJwtVerifier

  val dependencyOverrides = jackson ++ Seq(scalaXml)
  val excludeDependencies = Seq(
    ExclusionRule("com.squareup.okio", "okio"),
  )
}
