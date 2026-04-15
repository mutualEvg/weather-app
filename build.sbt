ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"

val http4sVersion = "0.23.30"
val circeVersion  = "0.14.10"

lazy val root = (project in file("."))
  .settings(
    name := "weather-app",
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-ember-server" % http4sVersion,
      "org.http4s"    %% "http4s-ember-client" % http4sVersion,
      "org.http4s"    %% "http4s-circe"        % http4sVersion,
      "org.http4s"    %% "http4s-dsl"          % http4sVersion,
      "io.circe"      %% "circe-generic"       % circeVersion,
      "io.circe"      %% "circe-parser"        % circeVersion,
      "com.github.pureconfig" %% "pureconfig" % "0.17.8",
      "ch.qos.logback" % "logback-classic"     % "1.5.12",
      "org.typelevel"  %% "munit-cats-effect"   % "2.0.0"  % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
