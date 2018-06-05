---
layout: docs
title: "Caching Implicits"
section: "c21"
---
```tut:invisible
import com.datastax.driver.core.{Cluster, Session}
import com.weather.scalacass.{ScalaSession, CCCassFormatEncoder, CCCassFormatDecoder}
import com.weather.scalacass.syntax._
import com.datastax.driver.core.Row

implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
implicit val session: Session = cluster.connect()

val sSession: ScalaSession = ScalaSession("mykeyspace")
sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 1}").execute()

case class MyTable(s: String, i: Int, l: Long)
val createStatement = sSession.createTable[MyTable]("mytable", 1, 0)
createStatement.execute()

val insertStatement = sSession.insert("mytable", MyTable("a str", 1234, 5678L))
insertStatement.execute()
```
# Caching Implicits

When deriving the encoders and decoders to your case classes, the library is built to be able to automatically resolve
the implicit `CCCassFormatEncoder`/`CCCassFormatDecoder`. This will work fine, but if you are using these 
encoders/decoders often, it may be worth it to cache them so that they do not have to be built at every call site. The
best and easiest way to do this is to derive these implicits in the companion object to your case classes, as follows:

(quick note: the `ImplicitCaching` object is a workaround for the compilation of these docs. It is unnecessary to wrap
your case class/companion object definition in code)

```tut
object ImplicitCaching {
  case class Query(s: String)
  object Query {
    implicit val encoder: CCCassFormatEncoder[Query] = CCCassFormatEncoder.derive
    implicit val decoder: CCCassFormatDecoder[Query] = CCCassFormatDecoder.derive
  }
}
sSession.selectStar("mytable", ImplicitCaching.Query("a str")).execute()
```

the `derive` function will implicitly create the encoder/decoder in the companion object, and now at every call site,
this implicit is used instead of one that would be built by the library.
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```