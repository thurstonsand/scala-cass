import microsites._

val cassandra3Version = "3.5.0"
val cassandra2Version = "2.1.10.3"
val cassandraVersion = sys.props.getOrElse("cassandra-driver.version", cassandra3Version) match {
  case v @ (`cassandra3Version` | `cassandra2Version`) => v
  case _ => throw new IllegalArgumentException(s"cassandra version must be one of $cassandra3Version, $cassandra2Version")
}

val baseVersion = "3.2.1"

lazy val codeLinterSettings = {
  Seq(
    wartremoverWarnings in (Compile, compile) ++= Seq(
      Wart.AsInstanceOf, Wart.DefaultArguments, Wart.EitherProjectionPartial, Wart.Enumeration,
      Wart.Equals, Wart.ExplicitImplicitTypes, Wart.FinalCaseClass, Wart.FinalVal,
      Wart.IsInstanceOf, Wart.JavaConversions, Wart.JavaSerializable, Wart.LeakingSealed,
      Wart.Null, Wart.OptionPartial, Wart.Product, Wart.Recursion, Wart.Return,
      Wart.Serializable, Wart.StringPlusAny, Wart.TryPartial, Wart.Var, Wart.While),
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

lazy val commonSettings = Seq(
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq("2.13.1", "2.12.10", "2.11.12", "2.10.7"),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Yno-adapted-args",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Xlint:adapted-args,nullary-unit,inaccessible,nullary-override,infer-any,missing-interpolator,doc-detached,private-shadow,type-parameter-shadow,poly-implicit-overload,option-implicit,delayedinit-select,by-name-right-associative,package-object-classes,unsound-match,stars-align", "-Ywarn-unused:privates,locals")
    case Some((2, 11)) => Seq("-Xlint:adapted-args,nullary-unit,inaccessible,nullary-override,infer-any,missing-interpolator,doc-detached,private-shadow,type-parameter-shadow,poly-implicit-overload,option-implicit,delayedinit-select,by-name-right-associative,package-object-classes,unsound-match,stars-align", "-Ywarn-unused", "-Ywarn-unused-import")
    case Some((2, 10)) => Seq("-Xlint")
    case _             => throw new IllegalArgumentException(s"scala version not configured: ${scalaVersion.value}")
  }),
  (scalacOptions in Test) -= "-Xfatal-warnings",
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
  parallelExecution in Test := false,
) ++ codeLinterSettings

lazy val macroSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalameta" %% "scalameta" % "3.7.4" % "provided",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "com.datastax.cassandra" % "cassandra-driver-core" % cassandraVersion classifier "shaded"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 10)) => Seq("org.scalamacros" %% "quasiquotes" % "2.1.1" cross CrossVersion.binary)
    case _ => Seq.empty
  })
)

lazy val applicationSettings = Seq(
  name := "ScalaCass",
  organization := "com.github.thurstonsand",
  description := "a wrapper for the Java Cassandra driver that uses case classes to simplify and codify creating cached statements in a type-safe manner",
  version := s"$baseVersion-$cassandraVersion",
  libraryDependencies ++= Seq(
    "com.google.code.findbugs" % "jsr305" % "3.0.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "org.joda" % "joda-convert" % "1.8.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "org.slf4j" % "slf4j-api" % "1.7.25" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
    "joda-time" % "joda-time" % "2.9.4",
    "com.chuusai" %% "shapeless" % "2.3.3",
    "com.google.guava" % "guava" % "19.0",
    "com.datastax.cassandra" % "cassandra-driver-core" % cassandraVersion classifier "shaded" excludeAll ExclusionRule("com.google.guava", "guava"),
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  )  ++ (if (cassandraVersion startsWith "2.1.") Seq(
    "org.cassandraunit" % "cassandra-unit" % "2.2.2.1" % "test"
  ) else Seq(
    "com.datastax.cassandra" % "cassandra-driver-extras" % cassandraVersion excludeAll (ExclusionRule("com.datastax.cassandra", "cassandra-driver-core"), ExclusionRule("com.google.guava", "guava")),
    "org.cassandraunit" % "cassandra-unit" % "3.3.0.2" % "test"
  )),
  initialize := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 10)) => sys.props("scalac.patmat.analysisBudget") = "off"
      case _             => sys.props remove "scalac.patmat.analysisBudget"
    }
  }
)

lazy val noPublishSettings = Seq(
  publish := ((): Unit),
  publishLocal := ((): Unit),
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

lazy val micrositeSettings = Seq(
  micrositeName := "scala-cass",
  micrositeAuthor := "Thurston Sandberg",
  micrositeDescription := "Java Cassandra Driver Bindings for Friendlier Scala",
  micrositeGithubOwner := "thurstonsand",
  micrositeGithubRepo := "scala-cass",
  micrositeBaseUrl := sys.props.getOrElse("microsite.baseurl", "scala-cass"),
  micrositeImgDirectory := baseDirectory.value / "imgs",
  micrositeCssDirectory := baseDirectory.value / "css",
  micrositeDataDirectory := baseDirectory.value / "data",
  micrositeExternalIncludesDirectory := baseDirectory.value / "includes",
  micrositeGitterChannelUrl := "scala-cass/Lobby",
  micrositeShareOnSocial := false,
  micrositeHighlightTheme := "docco",
  micrositeConfigYaml := ConfigYml(
    yamlCustomProperties = Map(
      "baseVersion" -> baseVersion,
      "cassandra2Version" -> cassandra2Version,
      "cassandra3Version" -> cassandra3Version
    )
  ),
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md",
  ghpagesNoJekyll := false,
  fork in tut := true,
  git.remoteRepo := "git@github.com:thurstonsand/scala-cass.git"
)

// in case I need macros in the future
//lazy val `scala-cass-macros` = project.in(file("macro"))
//  .settings(moduleName := "scala-cass-macros")
//  .settings(commonSettings: _*)
//  .settings(macroSettings: _*)

lazy val `scala-cass` = project.in(file("."))
  .settings(moduleName := "scala-cass",
            sourceGenerators in Compile += (sourceManaged in Compile).map(Boilerplate.gen).taskValue)
  .settings(commonSettings: _*)
  .settings(applicationSettings: _*)
  .settings(publishSettings: _*)
  .settings(addUnmanagedSourceDirsFrom(if (cassandraVersion startsWith "2.1.") "scala_cass21" else "scala_cass3"))
//  .dependsOn(`scala-cass-macros`)

lazy val `tut-cass3` = project.in(file("docs/cass3"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings: _*)
  .settings(applicationSettings: _*)
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(addUnmanagedSourceDirsFrom("scala_cass3"): _*)
  .dependsOn(`scala-cass`)

lazy val `tut-cass21`=  project.in(file("docs/cass21"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings: _*)
  .settings(applicationSettings: _*)
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(addUnmanagedSourceDirsFrom("scala_cass21"): _*)
  .dependsOn(`scala-cass`)

lazy val docs = project.in(file("docs/root"))
  .enablePlugins(MicrositesPlugin)
  .settings(micrositeSettings: _*)
  .settings(noPublishSettings: _*)
