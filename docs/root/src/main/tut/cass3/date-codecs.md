---
layout: docs
title: "Date Codecs"
section: "cthree"
---



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

```scala
scala> cluster // your cluster, which must be implicit for DateTime
res4: com.datastax.driver.core.Cluster = com.datastax.driver.core.Cluster@436df122

scala> com.weather.scalacass.joda.register(cluster)

scala> import com.weather.scalacass.joda.Implicits._
import com.weather.scalacass.joda.Implicits._

scala> r // some row from your cluster
res6: com.datastax.driver.core.Row = Row[a primary key, 2017-02-01, 2017-02-01T14:26:07.369-05:00, 51967366000000, Wed Feb 01 14:26:07 EST 2017]

scala> r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
res7: org.joda.time.Instant = 2017-02-01T19:26:07.340Z

scala> r.as[org.joda.time.LocalDate]("mydate") // cassandra "date"
res8: org.joda.time.LocalDate = 2017-02-01

scala> r.as[org.joda.time.LocalTime]("mytime") // cassandra "time"
res9: org.joda.time.LocalTime = 14:26:07.366

scala> r.as[org.joda.time.DateTime]("mydt") // cassandra "tuple<timestamp,varchar>"
res10: org.joda.time.DateTime = 2017-02-01T14:26:07.369-05:00
```

[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#joda-time) for information about the 
format of `DateTime`

#### Jdk8 Date Implicits

* You will need to provide an implicit instance of your Cluster for `ZonedDateTime` because it uses `TupleType`, which 
is derived from the `Cluster`
* first, register the override codecs with the cluster, provided as a `register` function 
`com.weather.scalacass.jdk8.register`
* then, import the implicits required to use the joda types, provided in `com.weather.scalacass.jdk8.Implicits._`

```scala
scala> // under the hood ZonedDateTime uses a tuple, meaning the cluster must be implicit
     | cluster // your cluster, which must be implicit for DateTime
res12: com.datastax.driver.core.Cluster = com.datastax.driver.core.Cluster@436df122

scala> com.weather.scalacass.jdk8.register(cluster)

scala> import com.weather.scalacass.jdk8.Implicits._
import com.weather.scalacass.jdk8.Implicits._

scala> r // some row from your cluster
res14: com.datastax.driver.core.Row = Row[a primary key, 2017-02-01, 2017-02-01T14:26:07.369-05:00, 51967366000000, Wed Feb 01 14:26:07 EST 2017]

scala> r.as[java.time.Instant]("mytimestamp") // cassandra "timestamp"
res15: java.time.Instant = 2017-02-01T19:26:07.340Z

scala> r.as[java.time.LocalDate]("mydate") // cassandra "date"
res16: java.time.LocalDate = 2017-02-01

scala> r.as[java.time.LocalTime]("mytime") // cassandra "time"
res17: java.time.LocalTime = 14:26:07.366

scala> r.as[java.time.ZonedDateTime]("mydt") // cassandra "tuple<timestamp,varchar>"
res18: java.time.ZonedDateTime = 2017-02-01T14:26:07.369-05:00[America/New_York]
```

[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#jdk-8) for information about the format 
of `ZonedDateTime`



