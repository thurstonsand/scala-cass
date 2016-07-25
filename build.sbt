organization := "com.github.thurstonsand"
name := "ScalaCass"

version := "0.3.5"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

//parallelExecution in Test := false

resolvers ++= Seq(
  Resolver.jcenterRepo,
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.9" classifier "shaded" excludeAll ExclusionRule(organization = "io.netty", name = "netty-handler"),
  "joda-time" % "joda-time" % "2.9.1",
  "com.chuusai" %% "shapeless" % "2.3.1",
  "com.google.guava" % "guava" % "19.0",
  "org.scalatest" %% "scalatest" % "3.0.0-M15" % "test",
  "com.whisk" %% "docker-testkit-scalatest" % "0.8.3" % "test"
)

import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform, SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Force)
  .setPreference(SpacesAroundMultiImports, false)

wartremoverWarnings in (Compile, compile) ++= Seq(
  Wart.Any, Wart.Any2StringAdd, Wart.AsInstanceOf,
  Wart.EitherProjectionPartial, Wart.IsInstanceOf, Wart.ListOps,
  Wart.Null, Wart.OptionPartial,
  Wart.Product, Wart.Return, Wart.Serializable,
  Wart.TryPartial, Wart.Var,
  Wart.Enumeration, Wart.FinalCaseClass, Wart.JavaConversions)

publishMavenStyle := true

pomIncludeRepository := { _ => false }

bintrayReleaseOnPublish in ThisBuild := false
bintrayPackageLabels := Seq("cassandra")

licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.php"))
homepage := Some(url("https://github.com/thurstonsand/scala-cass"))
pomExtra :=
  <scm>
    <url>git@github.com/thurstonsand/scala-cass.git</url>
    <connection>scm:git:git@github.com/thurstonsand/scala-cass.git</connection>
  </scm>
  <developers>
    <developer>
      <id>thurstonsand</id>
      <name>Thurston Sandberg</name>
      <url>https://github.com/thurstonsand</url>
    </developer>
  </developers>
