resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.2.1")

addSbtPlugin("com.47deg" % "sbt-microsites" % "0.7.18")
