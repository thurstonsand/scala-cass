---
layout: docs
title: "Raw"
section: "c3"
---
```tut:invisible
import com.datastax.driver.core.{Cluster, Session}
import com.weather.scalacass.ScalaSession

implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
implicit val session: Session = cluster.connect()

val sSession: ScalaSession = ScalaSession("mykeyspace")
sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 1}").execute()

case class MyTable(s: String, i: Int, l: Long)
val createStatement = sSession.createTable[MyTable]("mytable", 1, 0)
createStatement.execute()
```
# Raw Statements

In case the library cannot fulfill a specific need you have (it does not have 100% parity with the Java driver's 
features) or you otherwise need to build up your own queries as `String`s, you can write a raw statement equivalent to 
what you would pass to a `session.execute` and still get convenient caching of the prepared statement. Note, however,
that you must provide the exact type that the Java driver expects, meaning you need to manually box any `Int`s, `Long`s,
etc and convert any `Map`s, `List`s, etc to their Java counterparts. 
 
There are two variants, depending on the kind of response you expect:

```tut
val rawStatement = sSession.rawStatement("INSERT INTO mykeyspace.mytable (s, i, l) VALUES (?, ?, ?)", "a str", Int.box(1234), Long.box(5678L))
rawStatement.execute()
```
```tut
val rawSelect = sSession.rawSelect("SELECT COUNT(*) FROM mykeyspace.mytable WHERE s = ?", "a str")
rawSelect.execute()
```
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```