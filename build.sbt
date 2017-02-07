lazy val codeLinterSettings = {
  import scalariform.formatter.preferences._
  import com.typesafe.sbt.SbtScalariform, SbtScalariform.ScalariformKeys

  SbtScalariform.scalariformSettings ++
  Seq(
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(DanglingCloseParenthesis, Force)
      .setPreference(SpacesAroundMultiImports, false),

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
}

def addUnmanagedSourceDirsFrom(folder: String) = {
  def addSourceFilesTo(conf: Configuration) =
    unmanagedSourceDirectories in conf := {
      val sds = (unmanagedSourceDirectories in conf).value
      val sd = (sourceDirectory in conf).value
      sds :+ new java.io.File(sd, folder)
    }

  Seq(addSourceFilesTo(Compile), addSourceFilesTo(Test))
}

def usingMajorVersion(mVersion: String) = s"$mVersion.0.0-M2"

lazy val commonSettings = Seq(
  organization := "com.github.thurstonsand",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8", "2.10.6"),
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
  }),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "com.google.code.findbugs" % "jsr305" % "3.0.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "org.joda" % "joda-convert" % "1.8.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "org.slf4j" % "slf4j-api" % "1.7.21" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "joda-time" % "joda-time" % "2.9.4",
    "com.chuusai" %% "shapeless" % "2.3.1",
    "com.google.guava" % "guava" % "19.0",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "com.whisk" %% "docker-testkit-scalatest" % "0.9.0-M5" % "test"
  ),
  initialize := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 10)) => sys.props("scalac.patmat.analysisBudget") = "off"
      case _             => sys.props remove "scalac.patmat.analysisBudget"
    }
  },
  parallelExecution in Test := false,
  name := "ScalaCass",
  description := "a wrapper for the Java Cassandra driver that uses case classes to simplify and codify creating cached statements in a type-safe manner",
  scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Ywarn-unused-import")),
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value
) ++ codeLinterSettings

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/thurstonsand")),
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

lazy val cass3Settings = Seq(
  version := usingMajorVersion("2"),
  libraryDependencies ++= Seq(
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.0" classifier "shaded" excludeAll ExclusionRule("com.google.guava", "guava"),
    "com.datastax.cassandra" % "cassandra-driver-extras" % "3.1.0" excludeAll (ExclusionRule("com.datastax.cassandra", "cassandra-driver-core"), ExclusionRule("com.google.guava", "guava")),
    "org.cassandraunit" % "cassandra-unit" % "3.0.0.1" % "test"
  )
) ++ addUnmanagedSourceDirsFrom("scala_cass3")

lazy val cass21Settings = Seq(
  version := usingMajorVersion("1"),
  libraryDependencies ++= Seq(
    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.10.2" classifier "shaded" excludeAll ExclusionRule("com.google.guava", "guava"),
    "org.cassandraunit" % "cassandra-unit" % "2.2.2.1" % "test"
  )
) ++ addUnmanagedSourceDirsFrom("scala_cass21")

lazy val micrositeSettings = Seq(
  micrositeName := "ScalaCass",
  micrositeAuthor := "Thurston Sandberg",
  micrositeDescription := "Java Cassandra Driver Bindings for Friendlier Scala",
  micrositeGithubOwner := "thurstonsand",
  micrositeGithubRepo := "scala-cass",
  micrositeBaseUrl := "",
  //micrositeHomepage := "https://github.com/thurstonsand",
  micrositeImgDirectory := baseDirectory.value / "imgs",
  micrositeCssDirectory := baseDirectory.value / "css",
  micrositeDataDirectory := baseDirectory.value / "data",
  micrositeExternalIncludesDirectory := baseDirectory.value / "includes",
  micrositeHighlightTheme := "docco",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md",
  com.typesafe.sbt.SbtGhPages.GhPagesKeys.ghpagesNoJekyll := false,
  fork in tut := true,
  git.remoteRepo := "git@github.com:thurstonsand/scala-cass.git"
)

val javaVersion = sys.props("java.specification.version")

lazy val `scala-cass` = {
  lazy val `scala-cass` = project.in(file("."))
    .settings(moduleName := "scala-cass")
    .settings(commonSettings: _*)
    .settings(publishSettings: _*)
  javaVersion match {
    case "1.7" => `scala-cass`.settings(cass21Settings: _*)
    case _     => `scala-cass`.settings(cass3Settings: _*)
  }
}

lazy val `tut-cass3` = project.in(file("docs/cass3"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings: _*)
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)
  .dependsOn(`scala-cass` % "test")

lazy val `tut-cass21`=  project.in(file("docs/cass21"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings: _*)
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)
  .dependsOn(`scala-cass` % "test")

lazy val docs = project.in(file("docs/root"))
  .enablePlugins(MicrositesPlugin)
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)