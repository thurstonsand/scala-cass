---
layout: docs
title: "Row Extraction"
section: "c3"
---
```tut:invisible
import com.datastax.driver.core.{Cluster, Session}
import com.weather.scalacass.ScalaSession
import com.datastax.driver.core.Row

implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
implicit val session: Session = cluster.connect()

val sSession: ScalaSession = ScalaSession("mykeyspace")
sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 1}").execute()

case class MyTable(s: String, i: Int, l: List[Long])
val createStatement = sSession.createTable[MyTable]("mytable", 1, 0)
createStatement.execute()

val insertStatement = sSession.insert("mytable", MyTable("a str", 1234, List(5678L)))
insertStatement.execute()
```
# Row Extraction

Cassandra's `Row` holds the response from a statement. Using the driver, conversion into a useful Scala data type is 
cumbersome both in extracting a value from the Row, and converting it from the Java type. Scala-Cass handles all of that
transparently.

As an example, start with a `Row` retrieved from Cassandra table. Let's say this table has a definition of

```tut
case class MyRow(s: String, i: Int, l: List[Long]) // s varchar, i int, l bigint
```
Then we select a row using a `ScalaSession` (for more on `select`, [see the relevant 
page](/cass3/scalasession/select.html))

```tut
case class Select(s: String)
val row: Row = sSession.selectOneStar("mytable", Select("a str")).execute().right.toOption.flatten.get
```

First, let's extract into `MyRow` using the regular driver

```tut
import scala.collection.JavaConverters._
val driverDerivedRow = MyRow(row.getString("s"), row.getInt("i"), row.getList("l", classOf[java.lang.Long]).asScala.toList.map(Long.unbox))
```

Especially for the `List[Long]`, it is unnecessarily verbose, since we already have the necessary type information. In
addition, we don't know if any of these values came back as `null`, so in truth, we would need null checks as well

We can hide all of this boilerplate using the ScalaCass library. First, we need to import the syntax, 
`com.weather.scalacass.syntax`. Then let's mirror that extraction from above

```tut
import com.weather.scalacass.syntax._
val scalaCassDerivedRow = MyRow(row.as[String]("s"), row.as[Int]("i"), row.as[List[Long]]("l"))
```

All we need to specify is the type that we 
want[*](#For-a-full-list-of-type-mappings-between-the-cassandra-types-and-scala-types), and the library handles the rest.
If one of these values came back null, the driver will throw an exception since we do not want to introduce null values
into our code.

If you do need to handle null types, use the `Option` type to extract values, as this will return `None` instead of 
null

```tut
row.as[Option[String]]("s")
```

There are 2 convenience functions around this, `getAs` and `getOrElse`, that retrieves an Optional response of the type,
and, in the case of `getOrElse`, provides a default in the case it gets back a `None`

```tut:invisible
case class DeleteColumn(i: Int)
sSession.delete[DeleteColumn]("mytable", Select("a str")).execute()
```
```tut
row.getAs[Int]("i")
row.getOrElse[Int]("i", -12345)
```
```tut:invisible
insertStatement.execute()
```

If you want to handle the exception yourself, there is a way to simply attempt the extraction and return either the 
success or the failure

```tut
row.attemptAs[Int]("i")
```

## Case Classes

We already have a conveniently defined `MyRow` with all of the type information we want, so the Scala-Cass library (with
the help of the [Shapeless library](https://github.com/milessabin/shapeless)) can automatically use the case class 
directly for extraction

```tut
row.as[MyRow]
row.getAs[MyRow]
row.getOrElse[MyRow](MyRow("default row", -12345, List(-5678L)))
row.attemptAs[MyRow]
```

Note that no arguments (aside from the type parameter) are passed when extracting to a case class because you are acting
 on the entire row.
 
##### For performance characteristics of these extractions, [see the performance page](/cass3/performance.html)

##### For a full list of type mappings between the Cassandra types and Scala types, 
[see the type mappings page](/cass3/type-mappings.html)


```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```