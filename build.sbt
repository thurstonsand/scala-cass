organization := "com.github.thurstonsand"
name := "ScalaCass"

val javaVersion = SettingKey[String]("javaVersion", "the version of cassandra found in the system")
javaVersion := sys.props("java.specification.version")

val cassV3 = "3"
val cassV22 = "22"
val cassVersion = SettingKey[String]("cassVersion", "the version of cassandra to use for compilation")
cassVersion := Option(System.getProperty("cassVersion")).getOrElse(javaVersion.value match {
  case "1.7" => cassV22
  case _     => cassV3
})
def wrongCassVersion = new RuntimeException("unknown cassVersion. use either \"" + cassV3 + "\" or \"" + cassV22 + "\"")

version := {
  val majorVersion = (cassVersion.value, javaVersion.value) match {
    case (`cassV3`, "1.8") => "4"
    case (`cassV22`, "1.7") => "3"
    case (cv, jv) => throw new RuntimeException("invalid cassandra/java version combination: " + cv + "/" + jv + ". use either cass \"" + cassV3 + "\" with java 8 or cass \"" + cassV22 + "\" with java 7")
  }
  s"0.$majorVersion.4"
}

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

//parallelExecution in Test := false

resolvers ++= Seq(
  Resolver.jcenterRepo,
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "joda-time" % "joda-time" % "2.9.1",
  "com.chuusai" %% "shapeless" % "2.3.1",
  "com.google.guava" % "guava" % "19.0",
  "org.scalatest" %% "scalatest" % "3.0.0-M15" % "test",
  "com.whisk" %% "docker-testkit-scalatest" % "0.9.0-M5" % "test"

) ++ (cassVersion.value match {
  case `cassV3` => Seq(
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.0" classifier "shaded" excludeAll ExclusionRule(organization = "io.netty", name = "netty-handler"),
    "com.datastax.cassandra" % "cassandra-driver-extras" % "3.1.0",
    "org.cassandraunit" % "cassandra-unit" % "3.0.0.1"
  )
  case `cassV22` =>  Seq(
    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.10.2" classifier "shaded" excludeAll ExclusionRule(organization = "io.netty", name = "netty-handler"),
    "org.cassandraunit" % "cassandra-unit" % "2.2.2.1"
  )
  case _ => throw new RuntimeException("unknown cassVersion. use either \"" + cassV3 + "\" or \"" + cassV22 + "\"")
})

unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, sourceDirectory in Compile, cassVersion) {
  (sds: Seq[java.io.File], sd: java.io.File, v: String) =>
    if (v == cassV3) sds ++ Seq(new java.io.File(sd, "scala_cass3"))
    else if (v == cassV22) sds ++ Seq(new java.io.File(sd, "scala_cass22"))
    else throw wrongCassVersion
}
unmanagedSourceDirectories in Test <<= (unmanagedSourceDirectories in Test, sourceDirectory in Test, cassVersion) {
  (sds: Seq[java.io.File], sd: java.io.File, v: String) =>
    if (v == cassV3) sds ++ Seq(new java.io.File(sd, "scala_cass3"))
    else if (v == cassV22) sds ++ Seq(new java.io.File(sd, "scala_cass22"))
    else throw wrongCassVersion
}

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
