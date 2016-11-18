val cassV3 = "3"
val cassV21 = "21"

lazy val javaVersion = SettingKey[String]("javaVersion", "the version of cassandra found in the system")
lazy val cassVersion = SettingKey[String]("cassVersion", "the version of cassandra to use for compilation")
lazy val versionC = SettingKey[String]("versionC", "version dependent on Cassandra version")
lazy val commonScalacOptions = SettingKey[Seq[String]]("commonScalacOptions", "scalac options to apply to all settings")
lazy val commonDependencies = SettingKey[Seq[ModuleID]]("commonDependencies", "common dependencies")

lazy val init = Seq(
  javaVersion := sys.props("java.specification.version"),
  cassVersion := sys.props.getOrElse("cassVersion", javaVersion.value match {
    case "1.7" => cassV21
    case _     => cassV3
  }),
  versionC := {
    val majorVersion = (cassVersion.value, javaVersion.value) match {
      case (`cassV3`, "1.8") => "2"
      case (`cassV21`, "1.7") => "1"
      case (cv, jv) => throw new RuntimeException("invalid cassandra/java version combination: " + cv + "/" + jv + ". use either cass \"" + cassV3 + "\" with java 8 or cass \"" + cassV21 + "\" with java 7")
    }
    s"$majorVersion.0.0-M2"
  },
  commonScalacOptions := Seq(
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
  }),
  commonDependencies := Seq(
    "com.google.code.findbugs" % "jsr305" % "3.0.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "org.joda" % "joda-convert" % "1.8.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "org.slf4j" % "slf4j-api" % "1.7.21" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "joda-time" % "joda-time" % "2.9.4",
    "com.chuusai" %% "shapeless" % "2.3.1",
    "com.google.guava" % "guava" % "19.0",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "com.whisk" %% "docker-testkit-scalatest" % "0.9.0-M5" % "test"
  ) ++ (cassVersion.value match {
    case `cassV3` => Seq(
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.0" classifier "shaded" excludeAll ExclusionRule("com.google.guava", "guava"),
      "com.datastax.cassandra" % "cassandra-driver-extras" % "3.1.0" excludeAll (ExclusionRule("com.datastax.cassandra", "cassandra-driver-core"), ExclusionRule("com.google.guava", "guava")),
      "org.cassandraunit" % "cassandra-unit" % "3.0.0.1" % "test"
    )
    case `cassV21` =>  Seq(
      "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.10.2" classifier "shaded" excludeAll ExclusionRule("com.google.guava", "guava"),
      "org.cassandraunit" % "cassandra-unit" % "2.2.2.1" % "test"
    )
    case _ => throw new RuntimeException("unknown cassVersion. use either \"" + cassV3 + "\" or \"" + cassV21 + "\"")
  })
)

def wrongCassVersion = new RuntimeException("unknown cassVersion. use either \"" + cassV3 + "\" or \"" + cassV21 + "\"")
lazy val versionSpecificSourceDirectories = {
  def addSourceFilesTo(conf: Configuration) =
    unmanagedSourceDirectories in conf := {
      val sds = (unmanagedSourceDirectories in conf).value
      val sd = (sourceDirectory in conf).value
      val v = cassVersion.value

      if (v == cassV3) sds ++ Seq(new java.io.File(sd, "scala_cass3"))
      else if (v == cassV21) sds ++ Seq(new java.io.File(sd, "scala_cass21"))
      else throw wrongCassVersion
    }
  Seq(
    addSourceFilesTo(Compile),
    addSourceFilesTo(Test)
  )
}

lazy val scalariformSettings = {
  import scalariform.formatter.preferences._
  import com.typesafe.sbt.SbtScalariform, SbtScalariform.ScalariformKeys

  SbtScalariform.scalariformSettings

  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(DanglingCloseParenthesis, Force)
    .setPreference(SpacesAroundMultiImports, false)
}

lazy val wartRemoverSettings = Seq(
  wartremoverWarnings in (Compile, compile) := Seq.empty,
  wartremoverWarnings in (Compile, compile) ++= Seq(
    Wart.Any, Wart.Any2StringAdd,
    Wart.EitherProjectionPartial, Wart.ListOps,
    Wart.Null, Wart.OptionPartial,
    Wart.Product, Wart.Return, Wart.Serializable,
    Wart.TryPartial, Wart.Var,
    Wart.Enumeration, Wart.FinalCaseClass, Wart.JavaConversions),
  wartremoverWarnings in (Compile, console) := Seq.empty
)

lazy val buildSettings = Seq(
  organization := "com.github.thurstonsand",
  name := "ScalaCass",
  description := "a wrapper for the Java Cassandra driver that uses case classes to simplify and codify creating cached statements in a type-safe manner",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8", "2.10.6")
)



lazy val publishSettings = Seq(
  homepage := Some(url("http://scala-cass.github.io")),
  licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.php")),
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
    </developers>,
  publishMavenStyle := true,
  pomIncludeRepository := (_ => false),
  bintrayReleaseOnPublish in ThisBuild := false,
  bintrayPackageLabels := Seq("cassandra")
)

lazy val commonSettings = init ++ Seq(
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  scalacOptions ++= commonScalacOptions.value,
  resolvers ++= Seq(
    Resolver.jcenterRepo
  ),
  libraryDependencies ++= commonDependencies.value,
  initialize := { CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 10)) => sys.props("scalac.patmat.analysisBudget") = "off"
    case _             => sys.props remove "scalac.patmat.analysisBudget"
  }},
  scalariformSettings
) ++ versionSpecificSourceDirectories ++ wartRemoverSettings ++ buildSettings

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val micrositeSettings = Seq(
  micrositeAuthor := "Thurston Sandberg",
  micrositeDescription := "Java Cassandra driver bindings for friendlier Scala",
  micrositeGithubOwner := "thurstonsand",
  micrositeGithubRepo := "scala-cass",
  micrositeBaseUrl := "",
  com.typesafe.sbt.SbtGhPages.GhPagesKeys.ghpagesNoJekyll := false,
  micrositeHomepage := "http://thurstonsand.github.io/scala-cass",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md",
  fork in tut := true
)

lazy val core = project.in(file("."))
  .settings(moduleName := "core")
  .settings(commonSettings)
  .settings(version := versionC.value)

lazy val docs = project
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings)
  .settings(micrositeSettings)
  .settings(noPublishSettings)
  .dependsOn(core)

lazy val root = core
  .settings(publishSettings)
  .settings(moduleName := "scalacass")
  .settings(
    scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Ywarn-unused-import")),
    scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
    parallelExecution in Test := false,
    fork in test := true
  )
  .aggregate(docs)