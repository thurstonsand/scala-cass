val cassV2 = "2.1"
val cassV3 = "3.0+"
val javaVersion = sys.props("java.specification.version")
val cassVersion = sys.props.getOrElse("cassVersion", javaVersion match {
  case "1.7" => cassV2
  case _     => cassV3
})
version := {
  val majorVersion = (cassVersion, javaVersion) match {
    case (`cassV3`, "1.8") => "2"
    case (`cassV2`, "1.7") => "1"
    case (cv, jv) => throw new RuntimeException("invalid cassandra/java version combination: " + cv + "/" + jv + ". use either cass \"" + cassV3 + "\" with java 8 or cass \"" + cassV2 + "\" with java 7")
  }
  s"$majorVersion.0.0-M2"
}

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 11)) => Seq("-Ywarn-unused", "-Ywarn-unused-import")
  case _             => Seq.empty
})

libraryDependencies ++= Seq(
  "com.google.code.findbugs" % "jsr305" % "3.0.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
  "org.joda" % "joda-convert" % "1.8.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
  "org.slf4j" % "slf4j-api" % "1.7.21" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
  "joda-time" % "joda-time" % "2.9.4",
  "com.chuusai" %% "shapeless" % "2.3.1",
  "com.google.guava" % "guava" % "19.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "com.whisk" %% "docker-testkit-scalatest" % "0.9.0-M5" % "test"
) ++ (cassVersion match {
  case `cassV3` => Seq(
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.0" classifier "shaded" excludeAll ExclusionRule("com.google.guava", "guava"),
    "com.datastax.cassandra" % "cassandra-driver-extras" % "3.1.0" excludeAll (ExclusionRule("com.datastax.cassandra", "cassandra-driver-core"), ExclusionRule("com.google.guava", "guava")),
    "org.cassandraunit" % "cassandra-unit" % "3.0.0.1" % "test"
  )
  case `cassV2` =>  Seq(
    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.10.2" classifier "shaded" excludeAll ExclusionRule("com.google.guava", "guava"),
    "org.cassandraunit" % "cassandra-unit" % "2.2.2.1" % "test"
  )
  case _ => throw new RuntimeException("unknown cassVersion. use either \"" + cassV3 + "\" or \"" + cassV2 + "\"")
})

def addSourceFilesTo(conf: Configuration) =
  unmanagedSourceDirectories in conf := {
    val sds = (unmanagedSourceDirectories in conf).value
    val sd = (sourceDirectory in conf).value

    cassVersion match {
      case `cassV3` => sds ++ Seq(new java.io.File(sd, "scala_cass3"))
      case `cassV2` => sds ++ Seq(new java.io.File(sd, "scala_cass21"))
      case _ => throw new RuntimeException("unknown cassVersion. use either \"" + cassV3 + "\" or \"" + cassV2 + "\"")
    }
  }
addSourceFilesTo(Compile)
addSourceFilesTo(Test)

import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform, SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings
ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Force)
  .setPreference(SpacesAroundMultiImports, false)

wartremoverWarnings in (Compile, compile) := Seq.empty
wartremoverWarnings in (Compile, compile) ++= Seq(
  Wart.Any, Wart.Any2StringAdd,
  Wart.EitherProjectionPartial, Wart.ListOps,
  Wart.Null, Wart.OptionPartial,
  Wart.Product, Wart.Return, Wart.Serializable,
  Wart.TryPartial, Wart.Var,
  Wart.Enumeration, Wart.FinalCaseClass, Wart.JavaConversions)
wartremoverWarnings in (Compile, console) := Seq.empty

organization := "com.github.thurstonsand"
name := "ScalaCass"
description := "a wrapper for the Java Cassandra driver that uses case classes to simplify and codify creating cached statements in a type-safe manner"
scalaVersion := "2.11.8"
crossScalaVersions := Seq("2.11.8", "2.10.6")

homepage := Some(url("http://scala-cass.github.io"))
licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.php"))
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
publishMavenStyle := true
pomIncludeRepository := (_ => false)
bintrayReleaseOnPublish in ThisBuild := false
bintrayPackageLabels := Seq("cassandra")

micrositeAuthor := "Thurston Sandberg"
micrositeDescription := "Java Cassandra driver bindings for friendlier Scala"
micrositeGithubOwner := "thurstonsand"
micrositeGithubRepo := "scala-cass"
micrositeBaseUrl := ""
com.typesafe.sbt.SbtGhPages.GhPagesKeys.ghpagesNoJekyll := false
micrositeHomepage := "http://thurstonsand.github.io/scala-cass"
includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md"
fork in tut := true
