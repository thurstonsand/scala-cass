resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.0.2")

addSbtPlugin("com.fortysevendeg"  % "sbt-microsites" % "0.3.3")
