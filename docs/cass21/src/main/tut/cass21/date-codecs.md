---
layout: docs
title: "Date Codecs for C* 2.1"
section: "c21"
---
```tut:invisible
import com.datastax.driver.core.Cluster
import com.weather.scalacass.ScalaSession
import com.weather.scalacass.joda.Implicits._

implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
val session: ScalaSession = ScalaSession("datecodeckeyspace")(cluster.connect())
session.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 3}").execute()

case class TS(str: String, mytimestamp: org.joda.time.Instant)
case class Query(str: String)
val table = "mytable"
session.createTable[TS](table, 1, 0).execute()
val ts = TS("a primary key", org.joda.time.Instant.now)
session.insert(table, ts).execute()
val r = session.selectOneStar(table, Query(ts.str)).execute().right.toOption.flatten.get
```

# Date Codecs for Cassandra 2.1

By default, Scala-Cass uses the timestamp format provided as default for the Java driver. It is:

| Cassandra Type |           Scala Type               |
|:--------------:|:----------------------------------:|
| timestamp      | java.util.Date                     |


You have the option of using the Joda library as a replacement for this default.

All you need to do is import the implicits required to understand the joda `Instant`, 
`com.weather.scalacass.joda.Implicits._`

```tut
import com.weather.scalacass.syntax._, com.weather.scalacass.joda.Implicits._
r // some row from your table
r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
```

The same import is required for writing joda's `Instant` to Cassandra

```tut:invisible
session.dropKeyspace.execute()
session.close()
cluster.close()
```