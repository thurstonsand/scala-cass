resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.3")

addSbtPlugin("com.47deg" % "sbt-microsites" % "0.9.6")
