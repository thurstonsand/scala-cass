---
layout: docs
title: "Date Codecs for C* 2.1"
section: "c21"
---



# Date Codecs for Cassandra 2.1

By default, Scala-Cass uses the timestamp format provided as default for the Java driver. It is:

| Cassandra Type |           Scala Type               |
|:--------------:|:----------------------------------:|
| timestamp      | java.util.Date                     |


You have the option of using the Joda library as a replacement for this default.

All you need to do is import the implicits required to understand the joda `Instant`, 
`com.weather.scalacass.joda.Implicits._`

```scala
scala> import com.weather.scalacass.syntax._, com.weather.scalacass.joda.Implicits._
import com.weather.scalacass.syntax._
import com.weather.scalacass.joda.Implicits._

scala> r // some row from your table
res3: com.datastax.driver.core.Row = Row[a primary key, Wed Feb 01 14:24:51 EST 2017]

scala> r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
res4: org.joda.time.Instant = 2017-02-01T19:24:51.453Z
```

The same import is required for writing joda's `Instant` to Cassandra



