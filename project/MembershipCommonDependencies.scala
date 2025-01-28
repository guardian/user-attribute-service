import sbt.*

object MembershipCommonDependencies {

  val playJsonVersion = "3.0.1"
  val specs2Version = "4.19.2"

  // versions
  val awsClientVersion = "1.12.472"
  val dynamoDbVersion = "1.12.387"
  // libraries
  val supportInternationalisation =
    "com.gu" %% "support-internationalisation" % "0.16" exclude ("com.typesafe.scala-logging", "scala-logging_2.13") // it's not actually used and is the 2.13 version
  val scalaUri = "io.lemonlabs" %% "scala-uri" % "4.0.3"
  val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "2.32.0"
  val pekkoActor = "org.apache.pekko" %% "pekko-actor" % "1.0.1"
  val playJson = "org.playframework" %% "play-json" % playJsonVersion
  val playJsonJoda = "org.playframework" %% "play-json-joda" % playJsonVersion
  val specs2 = "org.specs2" %% "specs2-core" % specs2Version
  val specs2Mock = "org.specs2" %% "specs2-mock" % specs2Version
  val specs2Matchers = "org.specs2" %% "specs2-matcher" % specs2Version
  val specs2MatchersExtra = "org.specs2" %% "specs2-matcher-extra" % specs2Version
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15"
  val diff = "com.softwaremill.diffx" %% "diffx-scalatest-should" % "0.9.0"
  val localDynamoDB = "com.amazonaws" %% "DynamoDBLocal" % dynamoDbVersion
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  val awsCloudWatch = "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsClientVersion
  val awsS3 = "com.amazonaws" % "aws-java-sdk-s3" % awsClientVersion
  val okHttp = "com.squareup.okhttp3" % "okhttp" % "4.10.0"
  val scalaz = "org.scalaz" %% "scalaz-core" % "7.3.7"
  val libPhoneNumber = "com.googlecode.libphonenumber" % "libphonenumber" % "8.13.12"
  val dynamoDB = "com.amazonaws" % "aws-java-sdk-dynamodb" % awsClientVersion
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.3.0"
  val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.2"

  val dependencies = Seq(
    scalaUri,
    nscalaTime,
    pekkoActor,
    supportInternationalisation,
    playJson,
    playJsonJoda,
    specs2 % "test",
    specs2Mock % "test",
    specs2Matchers % "test",
    specs2MatchersExtra % "test",
    scalaTest % "test",
    diff % "test",
    scalaLogging,
    awsCloudWatch,
    okHttp,
    scalaz,
    libPhoneNumber,
    dynamoDB,
    scalaXml,
  )
}
