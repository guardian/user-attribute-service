import scala.sys.process.*
import scala.io.Source
import scala.util.Try

val appVersion = "1.0-SNAPSHOT"
name := "members-data-api"
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("releases") // libraries that haven't yet synced to maven central

def commitId(): String =
  try { "git rev-parse HEAD".!!.trim }
  catch { case _: Exception => "unknown" }

def buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    ("buildNumber", Option(System.getenv("BUILD_NUMBER")) getOrElse "DEV"),
    ("buildTime", System.currentTimeMillis),
    ("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse (commitId())),
  ),
  buildInfoPackage := "app",
  buildInfoOptions += BuildInfoOption.ToMap,
)

val commonSettings = Seq(
  organization := "com.gu",
  version := appVersion,
  scalaVersion := "2.13.15",
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / publishArtifact := false,
  Global / parallelExecution := false,
  updateOptions := updateOptions.value.withCachedResolution(true),
  Test / javaOptions += "-Dconfig.resource=TEST.public.conf",
  Test / fork := true,
) ++ buildInfoSettings

import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd
val buildDebSettings = Seq(
  Debian / serverLoading := Some(Systemd),
  maintainer := "Membership Dev <membership.dev@theguardian.com>",
  packageSummary := "Members Data API",
  packageDescription := """Members Data API""",
  riffRaffManifestProjectName := s"MemSub::Membership::members-data-api",
  riffRaffPackageType := (Debian / packageBin).value,
  riffRaffArtifactResources += (file("cloudformation/membership-attribute-service.yaml") -> "cloudformation/membership-attribute-service.yaml"),
  Universal / javaOptions ++= Seq(
    "-Dpidfile.path=/dev/null",
    "-J-XX:MaxRAMPercentage=50",
    "-J-XX:InitialRAMPercentage=50",
    "-J-XX:MaxMetaspaceSize=500m",
    "-J-XX:+PrintGCDetails",
    s"-J-Xlog:gc:/var/log/${packageName.value}/gc.log",
  ),
)

val `membership-common` =
  (project in file("membership-common"))
    .settings(
      Seq(
        name := "membership-common",
        organization := "com.gu",
        scalaVersion := "2.13.15",
        scalacOptions := Seq("-feature", "-deprecation"),
        crossScalaVersions := Seq(scalaVersion.value),
        Compile / doc / sources := List(), // no docs please

        scmInfo := Some(
          ScmInfo(
            url("https://github.com/guardian/membership-common"),
            "scm:git:git@github.com:guardian/membership-common.git",
          ),
        ),
        description := "Scala library for common Guardian Membership/Subscriptions functionality.",
        licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
        Compile / unmanagedResourceDirectories += baseDirectory.value / "conf",
        libraryDependencies ++= MembershipCommonDependencies.dependencies,
        dependencyOverrides += MembershipCommonDependencies.jacksonDatabind,
      ),
    )

val api = Project("membership-attribute-service", file("membership-attribute-service"))
  .enablePlugins(SystemdPlugin, PlayScala, BuildInfoPlugin, RiffRaffArtifact, JDebPackaging)
  .settings(commonSettings)
  .settings(buildDebSettings)
  .settings(
    libraryDependencies ++= Dependencies.apiDependencies,
    dependencyOverrides ++= Dependencies.dependencyOverrides,
    excludeDependencies ++= Dependencies.excludeDependencies,
  )
  .settings(routesGenerator := InjectedRoutesGenerator)
  .settings(
    addCommandAlias("devrun", "run 9400"),
    addCommandAlias("batch-load", "runMain BatchLoader"),
    addCommandAlias("play-artifact", "riffRaffNotifyTeamcity"),
  )
  .dependsOn(`membership-common` % "test->test;compile->compile")

val root = project.in(file(".")).aggregate(api)
