name := "ScalaCass"

version := "0.1"

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

resolvers ++= Seq(
  Resolver.jcenterRepo,
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "3.0.0-M10",
	"com.datastax.cassandra" % "cassandra-driver-core" % "2.1.9",
  "org.cassandraunit" % "cassandra-unit" % "2.2.2.1",
  "joda-time" % "joda-time" % "2.9.1",
  "com.chuusai" %% "shapeless" % "2.3.0"
)

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
    <id>thurstonsand</id>
    <name>Thurston Sandberg</name>
    <url>https://github.com/thurstonsand</url>
  </developers>
