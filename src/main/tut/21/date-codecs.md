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
import com.datastax.driver.core.Row
import com.weather.scalacass.syntax._
import com.weather.scalacass.joda.Implicits._

val r: Row = _ // some row from your cluster
r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
```

While this is an example of a read, the same process is required for writing joda `Instant` to Cassandra