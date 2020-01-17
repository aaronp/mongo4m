import org.scoverage.coveralls.Imports.CoverallsKeys._
import eie.io._

ThisBuild / organization := "mongo4m"
ThisBuild / scalaVersion := "2.13.0"

val projectName = "mongo4m"
val username = "aaronp"
val scalaTwelve = "2.12.10"
val scalaThirteen = "2.13.0"
val defaultScalaVersion = scalaThirteen //scalaTwelve

name := projectName

organization := s"com.github.$username"

enablePlugins(GhpagesPlugin)
enablePlugins(ParadoxPlugin)
enablePlugins(SiteScaladocPlugin)
enablePlugins(ParadoxMaterialThemePlugin) // see https://jonas.github.io/paradox-material-theme/getting-started.html

scalaVersion := defaultScalaVersion
crossScalaVersions := Seq(scalaTwelve, scalaThirteen)

paradoxProperties += ("project.url" -> s"https://$username.github.io/$projectName/docs/current/")

Compile / paradoxMaterialTheme ~= {
  _.withLanguage(java.util.Locale.ENGLISH)
    .withColor("blue", "grey")
    //.withLogoIcon("cloud")
    .withRepository(uri(s"https://github.com/$username/$projectName"))
    .withSocial(uri("https://github.com/$username"))
    .withoutSearch()
}

//scalacOptions += Seq("-encoding", "UTF-8")

siteSourceDirectory := target.value / "paradox" / "site" / "main"

siteSubdirName in SiteScaladoc := "api/latest"

val monix =
  List("monix", "monix-execution", "monix-eval", "monix-reactive", "monix-tail")

val monixDependencies = monix.map { art =>
  "io.monix" %% art % "3.1.0"
}
val logging = List("com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
                   "ch.qos.logback" % "logback-classic" % "1.2.3" % "test")

val circeDependencies = List("circe-core",
                             "circe-generic",
                             "circe-parser",
                             "circe-literal",
                             "circe-generic-extras").map {
  case art @ "circe-generic-extras" => "io.circe" %% art % "0.12.2"
  case art                          => "io.circe" %% art % "0.12.3"
}

libraryDependencies ++= monixDependencies ++ circeDependencies ++ logging ++ List(
  "com.typesafe" % "config" % "1.4.0" % "provided",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.8.0",
  "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % Test,
  "com.github.aaronp" %% "dockerenv" % "0.4.5" % "test",
  "com.github.aaronp" %% "dockerenv" % "0.4.5" % "test" classifier ("tests")
)

libraryDependencies ++= List(
  "org.scalactic" %% "scalactic" % "3.1.0" % "test",
  "org.scalatest" %% "scalatest" % "3.1.0" % "test",
  "org.pegdown" % "pegdown" % "1.6.0" % "test",
  "junit" % "junit" % "4.13" % "test"
)

publishMavenStyle := true
releaseCrossBuild := true
coverageMinimum := 90
coverageFailOnMinimum := true
git.remoteRepo := s"git@github.com:$username/mongo4m.git"
ghpagesNoJekyll := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value
publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

test in assembly := {}
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

// https://coveralls.io/github/aaronp/mongo4m
// https://github.com/scoverage/sbt-coveralls#specifying-your-repo-token
coverallsTokenFile := Option(
  (Path.userHome / ".sbt" / ".coveralls.mongo4m").asPath.toString)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "mongo4m.build"

// see http://scalameta.org/scalafmt/
scalafmtOnCompile in ThisBuild := true
scalafmtVersion in ThisBuild := "1.4.0"

// see http://www.scalatest.org/user_guide/using_scalatest_with_sbt
testOptions in Test += (Tests
  .Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports", "-oN"))

pomExtra := {
  <url>https://github.com/{username}/{projectName}</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>{username}</id>
        <name>{username}</name>
        <url>http://github.com/{username}</url>
      </developer>
    </developers>
}
