scalaVersion := "2.11.7"

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers ++= Seq(
  Resolver.jcenterRepo,
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "3.0.0-M10",
	"com.datastax.cassandra" % "cassandra-driver-core" % "2.1.9",
  "org.cassandraunit" % "cassandra-unit" % "2.2.2.1",
  "org.scalaz.stream" %% "scalaz-stream" % "0.8",
  "com.iheart" %% "ficus" % "1.1.3",
  "joda-time" % "joda-time" % "2.9.1",
  "com.chuusai" %% "shapeless" % "2.2.5"
)