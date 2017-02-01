---
layout: docs
title: "Date Codecs"
section: "c3"
---
```tut:invisible
import com.datastax.driver.core.Cluster
import com.weather.scalacass.ScalaSession
import com.weather.scalacass.joda.Implicits._
import com.weather.scalacass.syntax._

implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
com.weather.scalacass.joda.register(cluster)
val session: ScalaSession = ScalaSession("datecodeckeyspace")(cluster.connect())
session.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 3}").execute()

case class TS(str: String, mytimestamp: org.joda.time.Instant, mydate: org.joda.time.LocalDate, mytime: org.joda.time.LocalTime, mydt: org.joda.time.DateTime)
case class Query(str: String)
val table = "mytable"
session.createTable[TS](table, 1, 0).execute()
val ts = TS("a primary key", org.joda.time.Instant.now, org.joda.time.LocalDate.now, org.joda.time.LocalTime.now, org.joda.time.DateTime.now)
session.insert(table, ts).execute()
val r = session.selectOneStar(table, Query(ts.str)).execute().right.toOption.flatten.get
```

# Date Codecs

By default, Scala-Cass uses the date/time formats provided as default for the Java driver. They are:

| Cassandra Type |           Scala Type               |
|:--------------:|:----------------------------------:|
| timestamp      | java.util.Date                     |
| date           | com.datastax.driver.core.LocalDate |
| time           | Time                               |

where `time` is actually a `Long` wrapped in the `Time` case class to prevent collision with `bigint`.

You have the option of using the Joda library or Jdk8 date library as a replacement for these defaults. While the 
examples below showcase how to read data of joda/jdk8 types, the same process is required for writing these types to
Cassandra.

### Joda Implicits

* You will need to provide an implicit instance of your Cluster for `DateTime` because it uses `TupleType`, which 
is derived from the `Cluster`
* first, register the override codecs with the cluster, provided as a `register` function 
`com.weather.scalacass.joda.register`
* then, import the implicits required to use the joda types, provided in `com.weather.scalacass.joda.Implicits._`

```tut
cluster // your cluster, which must be implicit for DateTime
com.weather.scalacass.joda.register(cluster)
import com.weather.scalacass.joda.Implicits._

r // some row from your cluster
r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
r.as[org.joda.time.LocalDate]("mydate") // cassandra "date"
r.as[org.joda.time.LocalTime]("mytime") // cassandra "time"
r.as[org.joda.time.DateTime]("mydt") // cassandra "tuple<timestamp,varchar>"
```

[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#joda-time) for information about the 
format of `DateTime`

#### Jdk8 Date Implicits

* You will need to provide an implicit instance of your Cluster for `ZonedDateTime` because it uses `TupleType`, which 
is derived from the `Cluster`
* first, register the override codecs with the cluster, provided as a `register` function 
`com.weather.scalacass.jdk8.register`
* then, import the implicits required to use the joda types, provided in `com.weather.scalacass.jdk8.Implicits._`

```tut
// under the hood ZonedDateTime uses a tuple, meaning the cluster must be implicit
cluster // your cluster, which must be implicit for DateTime
com.weather.scalacass.jdk8.register(cluster)

import com.weather.scalacass.jdk8.Implicits._

r // some row from your cluster
r.as[java.time.Instant]("mytimestamp") // cassandra "timestamp"
r.as[java.time.LocalDate]("mydate") // cassandra "date"
r.as[java.time.LocalTime]("mytime") // cassandra "time"
r.as[java.time.ZonedDateTime]("mydt") // cassandra "tuple<timestamp,varchar>"
```

[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#jdk-8) for information about the format 
of `ZonedDateTime`

```tut:invisible
session.dropKeyspace.execute()
session.close()
cluster.close()
```