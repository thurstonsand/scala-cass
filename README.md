# ScalaCass

##### [Cassandra Java Driver](https://github.com/datastax/java-driver) wrapper that makes retrieval from Rows a little easier

[![Build Status](https://travis-ci.org/thurstonsand/scala-cass.svg?branch=master)](https://travis-ci.org/thurstonsand/scala-cass)
[![Join the chat at https://gitter.im/scala-cass/Lobby](https://badges.gitter.im/scala-cass/Lobby.svg)](https://gitter.im/scala-cass/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

### Getting ScalaCass

[you can find it on bintray](https://bintray.com/thurstonsand/maven/scalacass).

Supports **scala 2.10** and **scala 2.11** and

* Cassandra 2.1 on Java 7
* Cassandra 3.0+ on Java 8

#### SBT

Add the jcenter resolver

```scala
resolvers += Resolver.jcenterRepo
```

Add the appropriate version of the library

##### Cassandra 3.0+ with Java 8

```scala
libraryDependencies += "com.github.thurstonsand" %% "scalacass" % "0.6.10"
```

##### Cassandra 2.1 with Java 7

```scala
libraryDependencies += "com.github.thurstonsand" %% "scalacass" % "0.5.10"
```

#### Maven

Add the jcenter resolver

```xml
<repositories>
    <repository>
        <id>central</id>
        <name>bintray</name>
        <url>http://jcenter.bintray.com</url>
    </repository>
</repositories>
```

Pick a version

##### Cassandra 2.1 with Java 7

```xml
<properties>
    <scalaCass.version>0.5.10</scalaCassVersion>
</properties>
```

##### Cassandra 3.0+ with Java 8

```xml
<properties>
    <scalaCass.version>0.6.10</scalaCassVersion>
</properties>
```

Include the repo

```xml
<dependency>
  <groupId>com.github.thurstonsand</groupId>
  <artifactId>scalacass_${scala.version}</artifactId>
  <version>${scalaCass.version}</version>
  <type>pom</type>
</dependency>
```

### Overview

* [Components](#components)
  * [Session creation](#session-creation)
  * [Table creation](#table-creation)
  * [General characteristics of all Cassandra statements](#characteristics-of-cassandra-statements)
  * [Insert](#insert)
  * [Update](#update)
  * [Delete](#delete)
  * [Select](#select)
    * [Row extraction](#row-extraction)
* [Additional functionality](#additional-functionality)
  * [Batch statements](#batch-statements)
  * [Raw statements](#raw-statements)
* [Type mapping](#type-mapping)
* [Custom types](#custom-types)
  * [Map over existing type](#map-over-an-existing-type)
  * [Create new type](#create-a-new-type-from-scratch)
* [Parsing performance](#parsing-performance)

## Components

### Session creation

using a ScalaSession follows the same general rules as creating the regular Java Session. The major difference is that, 
in Cassandra 3.0+, this library requires a cluster instance in implicit scope when working with tuples. This is because
tuple types are defined based on the specific codecs associated with a cluster instance. effectively, this means that,

##### for Cassandra 2.1 / Java 7

```scala
val cluster = Cluster.builder.addContactPoint("localhost").build()
```

##### for Cassandra 3.0+ / Java 8

```scala
// implicit is only necessary if using tuple types
implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
```

#### Characteristics

* `PreparedStatement` caching
* acts on a single keyspace
* can optionally create a keyspace on instantiation
* can pick up Java `Session` implicitly

The session itself is a class that you must keep around, much like you would a Cassandra Java Session. This is because
the ScalaSession caches PreparedStatements automatically, so if you are calling the same request multiple times, it will
use an existing PreparedStatement instead of generating a new statement every time. the instantiation takes an optional
String with the right hand side of the keyspace definition, if you want it to automatically create the keyspace for you
(this will likely only really be used for testing purposes).

```scala
import com.weather.scalacass.ScalaSession

implicit val session = cluster.connect()

val sSessionCreateKeyspace = ScalaSession("mykeyspace",
  "replication = {'class':'SimpleStrategy', 'replication_factor' : 3}") // session can be picked up implicitly
val sSession = ScalaSession("mykeyspace")(session) // if mykeyspace already exists
```

In addition to creating a keyspace, a `ScalaSession` can delete the keyspace. This would make any subsequent calls via
that `ScalaSession` invalid

```scala
sSession.dropKeyspace()
```

Finally, you can close the associated `Session` for shutdown.

```scala
sSession.close()
```

### Table creation

This feature will likely be useful mostly for testing purposes. It takes advantage of case classes to model the data, so the type of the data maps to a cassandra type, and the names in the case class match to the cassandra column names.

#### Characteristics

* the createTable method takes 4 properties
  * name of table
  * number of partition keys
  * number of clustering keys
  * the right hand side of the table definition
* parameters wrapped in `Option` take the underlying type parameter as its type for table creation
* you must have at least 1 partition key
* the number of partition keys + clustering keys must be less than the number of fields in the case class
* any rules associated with cassandra semantics for data types must be followed (eg no counters in the primary key)
* to see all scala-cassandra type mappings, [jump to this section](#type-mapping)

```scala
case class MyTable(s: String, i: Int, l: Option[Long])
// generates """CREATE TABLE mykeyspace.mytable (s varchar, i int, l bigint, PRIMARY KEY ((s, i))) WITH COMPACT STORAGE"""
sSession.createTable[MyTable]("mytable", 2, 0, "COMPACT STORAGE")

case class MyTableWithClustering(s: String, i: Int, b: Boolean, l: Option[Long])
// generates """CREATE TABLE mykeyspace.mytablewithclustering (s varchar, i int, b bool, l bigint, PRIMARY KEY ((s, i), b))"""
sSession.createTable[MyTableWithClustering]("mytablewithclustering", 2, 1)
```

In addition, you can both truncate and drop existing tables

```scala
sSession.truncateTable("mytable")
sSession.dropTable("mytable")
```

### Characteristics of Cassandra statements

These are some characteristics shared by the remaining functions in the `ScalaSession`

* the first parameter of the function is the table name
* the names and types of the case class should match up to the underlying cassandra names and types
* there is a synchronous and asynchronous implementation of each statement. ScalaCass returns a `ResultSet`/
  `scala.concurrent.Future[ResultSet]` for insert/delete/update statements, `Iterator[Row]`/`Future[Iterator[Row]]` for
  select statements, and `Option[Row]`/`Future[Option[Row]]` for selectOne statements
* any columns missing from the table are not entered as `null` to cassandra. They are left out of the query
* optional values are included in the statement if they are `Some`, and left out of the statement if they are `None`. This
  includes values in any queries, and if there is a problem with the generated Cassandra query, an exception will be 
  thrown by the Cassandra Java driver

### Insert

Use case classes to model the data to insert. If you are inserting an entire row, the same case class used for the definition of the table can be used.

```scala
// given the table definition
case class MyTable(s: String, i: Int, l: Option[Long])

// generates """INSERT INTO mykeyspace.mytable (s, i, l) VALUES ('asdf', 1234, 123)"""
val insertRes: ResultSet = sSession.insert("mytable", MyTable("asdf", 123, Some(1234L)))

// generates """INSERT INTO mykeyspace.mytable (s, i) VALUES ('asdf', 1234)"""
val insertRes2: ResultSet = sSession.insert("mytable", MyTable("asdf", 123, None))

case class InsertSome(s: String, i: Int)
// generates """INSERT INTO mykeyspace.mytable (s, i) VALUES ('asdf', 1234)"""
val insertRes3: ResultSet = sSession.insert("mytable", InsertSome("asdf", 123))

val insertRes4: Future[ResultSet] = sSession.insertAsync("mytable", MyTable("asdf", 123, None))
```

if you need a TTL on the inserted data, that can be added to the insert statement

```scala
val insertResWithTTL: ResultSet = 
  sSession.insert("mytable", MyTable("asdf", 123, Some(1234L)), ttl=Some(60)) // 1 minute ttl
```

### Update

Use case classes to model the data to update. For update, 2 case classes are used -- one to specify the query,
and one to specify the data to update.

```scala
// given the table definition
case class MyTable(s: String, i: Int, l: Option[Long])

case class Update(l: Long)
case class Query(s: String, i: Int)

// generates """UPDATE mykeyspace.mytable SET l=321 WHERE str='asdf' AND i=1234"""
val updateRes: ResultSet = sSession.update("mytable", Update(5678L), Query("asdf", 123))
val updateRes2: Future[ResultSet] = sSession.updateAsync("mytable", Update(5678L), Query("asdf", 123))

// NOTE:
// generates """UPDATE mykeyspace.mytable SET s='asdf', i=123 where l=5678""", which will throw an exception
sSession.update("mytable", Query("asdf", 123), Update(5678L))
```

if you need a TTL on the updated data, that can be added to the update statement

```scala
val updateResWithTTL: ResultSet =
  sSession.update("mytable", Update(5678L), Query("asdf", 123), ttl=Some(60)) // 1 minute ttl
```

if you just want to update the TTL, pass `NoUpdate` to the update statement

```scala
val updateResWithOnlyTTL: ResultSet =
  sSession.update("mytable", ScalaSession.NoUpdate(), Query("asdf", 123), ttl=Some(60))
```

be warned, however, if you pass `NoUpdate` and do not specify a ttl, the library will throw an 
`IllegalArgumentException` because there is nothing to update.

### Delete

Use case classes to model the query for the data to delete.

```scala
// given the table and query definition
case class MyTable(s: String, i: Int, l: Option[Long])
case class Query(s: String, i: Int)

// generates """DELETE FROM mykeyspace.mytable WHERE s='asdf' AND i=123"""
val deleteRes: ResultSet = sSession.delete("mytable", Query("asdf", 123))
val deleteRes2: Future[ResultSet] = sSession.deleteAsync("mytable", Query("asdf", 123))
```

### Select

Use case classes to model the query for the data to select. You can either use `select`, which returns an 
`Iterator[Row]`, or `selectOne`, which returns an `Option[Row]` representing the first result returned from Cassandra if
it exists (using the underlying `(r: Row).one()`)

```scala
// given the table and query definition
case class MyTable(s: String, i: Int, l: Option[Long])
case class Query(s: String, i: Int)

// generates """SELECT * FROM mykeyspace.mytable WHERE s='asdf' AND i=123"""
val selectRes: Iterator[Row] = sSession.select("mytable", Query("asdf", 123))
val selectRes2: Future[Iterator[Row]] = sSession.selectAsync("mytable", Query("asdf", 123))

// generates """SELECT * FROM mykeyspace.mytable WHERE s='asdf' AND i=123""" and only returns first value found, if any
val selectOneRes: Option[Row] = sSession.selectOne("mytable", Query("asdf", 123))
val selectOneRes2: Future[Option[Row]] = sSession.selectOneAsync("mytable", Query("asdf", 123))
```

`select`/`selectOne` also takes an optional `allowFiltering` boolean, [as described here](http://www.datastax.com/dev/blog/allow-filtering-explained-2)

```scala
case class RequiresFilteringQuery(i: Int)
// generates """SELECT * FROM mykeyspace.mytable WHERE i=123 ALLOW FILTERING"""
val selectWithFiltering: Iterator[Row] = sSession.select("mytable", RequiresFilteringQuery(123), allowFiltering=true)
```

`select` also takes an optional `limit` parameter, to limit the number of results returned

```scala
// generates """SELECT * FROM mykeyspace.mytable WHERE s='asdf' AND i=123 LIMIT 100"""
val selectResWithLimit: Iterator[Row] = sSession.select("mytable", Query("asdf", 123), limit=Some(100))
```

If you want to pull out the entire table with the select statement, this can be achieved by using the `NoQuery` type

```scala
// generates """SELECT * FROM mykeyspace.mytable"""
val selectAll: Iterator[Row] = sSession.select("mytable", ScalaSession.NoQuery())
```

Finally, in case you only want to extract certain columns in a Row, this is provided by the `columns` variant of the 
select functions.

```scala
case class ColumnsIWant(i: Int, l: Long)

// generates """SELECT i, l FROM mykeyspace.mytable WHERE s='asdf' AND i=123"""
val someColumns: Option[Row] = sSession.selectColumnsOne[ColumnsIWant, Query]("mytable", Query("asdf", 123))

val aRowSomeColumns = someColumns.get
aRowSomeColumns.isNull("s") // true
aRowSomeColumns.getInt("i") // 123
aRowSomeColumns.getLong("l") // 1234L
// see "Row Extraction" for a better way to extract values from the Row

// other variants
sSession.selectColumns[ColumnsIWant]("mytable", Query("asdf", 123))
sSession.selectColumnsAsync[ColumnsIWant]("mytable", Query("asdf", 123))
sSession.selectColumnsOneAsync[ColumnsIWant]("mytable", Query("asdf", 123))
```

#### Row Extraction

After a select, you will have one or more `Row`s. Instead of the usual `getString`, `getInt`, etc, ScalaCass offers
a friendlier syntax

##### Characteristics

* requires an import of `com.weather.scalacass.syntax._`
* This functionality comes with no additional code cost thanks to the [Shapeless library](https://github.com/milessabin/shapeless)
* For a complete mapping between Cassandra and Scala types, [see the type mappings section](#type-mapping)

```scala
val aRow = selectOneRes.get // for demonstration purposes

// Java driver way
val s1: String = aRow.getString("s")

// ScalaCass way
import com.weather.scalacass.ScalaCass._
val s2: String = aRow.as[String]("s")
```

This opens up a number of possibilities, including optional extractions that return `None` if the value is not found and
attempted extractions that return an `Either[Throwable, T]` to capture failure information

```scala
val s1_?: Option[String] = aRow.as[Option[String]]("s")
// alternate syntax
val s2_?: Option[String] = aRow.getAs[String]("s")

val sAttempt: Either[Throwable, String] = aRow.attemptAs[String]("s")

val sWithDefault: String = aRow.getOrElse[String]("s", "defaultValue")
```

and perhaps most helpfully, allows extraction of an entire Row directly into a case class. Note that no column parameter is passed to these functions, since it is acting on the entire row, not on a single column

```scala
// given the table definition
case class MyTable(s: String, i: Int, l: Option[Long])

val allValues: MyTable = aRow.as[MyTable]
val allValues_?: Option[MyTable] = aRow.getAs[MyTable]
val allValuesAttempt: Either[Throwable, MyTable] = aRow.attemptAs[MyTable]
val allValuesOrDefault: MyTable = aRow.getOrElse[MyTable](MyTable("default_s", 0, None))
```

In case class extraction, the entire case class must be extracted correctly, or none of it will be. If some parameters do not need to be extracted, they can be made `Option`al fields inside the case class.

## Additional Functionality

Outside of the core statements for Cassandra, there are a few other bits of Cassandra behavior improved by Scala-ization.

### Batch Statements

inserts, updates, and deletes can be batched into a single statement sent to Cassandra, and with a log level of
`LOGGED`, will either all succeed or all fail. ScalaCass provides a way to pass in a `Seq` of case class definitions to
be batched.

**Note**: These case classes exist under `com.weather.scalacass`

```scala
import com.weather.scalacass._

val updateBatch = UpdateBatch("mytable", MyUpdate(320L), MyQuery("asdf", 1234))
val deleteBatch = DeleteBatch("mytable", MyQuery("qwer", 1234))
val insertBatch = InsertBatch("mytable", MyTable("hjkl", 678, Some(6789L)))

val batchRes: ResultSet = sSession.batch(Seq(updateBatch, deleteBatch, insertBatch))
val batchResFut: Future[ResultSet] = sSession.batchAsync(Seq(updateBatch, deleteBatch, insertBatch))
```

the batch methods default to using `LOGGED`, but you can pass in whatever type is available

```scala
val batchResUnlogged: ResultSet = sSession.batch(Seq(updateBatch, deleteBatch, insertBatch), BatchStatement.Type.UNLOGGED)
```

### Raw Statements

There are raw variants to insert, update, delete, select, and batch statements, primarily for cases where this library
cannot fulfill a specific need you have. Raw variants simply take a Cassandra query as String and the anyref arguments, if
any. The primary advantage is that the queries are cached as `PreparedStatement`s and can be mixed with other, more
well behaved queries. As with other queries, there are asynchronous variants.

```scala
val insertQuery = "INSERT INTO mykeyspace.mytable (s, i, l) VALUES (?, ?, ?)"
val insertRes: ResultSet = sSession.rawStatement(insertQuery, "asdf", Int.box(123), Long.box(1234L))

val deleteQuery = "DELETE FROM mykeyspace.mytable WHERE s=? AND i=?"
val deleteRes: ResultSet = sSession.rawStatement(deleteQuery, "asdf", Int.box(123))

val updateQuery = "UPDATE mykeyspace.mytable SET l=? WHERE str=? AND i=?"
val updateRes: Future[ResultSet] = sSession.rawStatementAsync(updateQuery, Long.box(5678L), "asdf", Int.box(123))

val selectQuery = "SELECT * FROM mykeyspace.mytable WHERE s=? AND i=?"
val selectRes: Iterator[Row] = sSession.rawSelect(selectQuery, "asdf", Int.box(123))
val selectResFut: Future[Iterator[Row]] = sSession.rawSelectAsync(selectQuery, "asdf", Int.box(123))

val selectOneRes: Option[Row] = sSession.rawSelectOne(selectQuery, "asdf", Int.box(123))
val selectOneRes: Future[Option[Row]] = sSession.rawSelectOneAsync(selectQuery, "asdf", Int.box(123))
```

and for batch statements,

```scala
import com.weather.scalacass._

val deleteBatch = DeleteBatch("mytable", MyQuery("qwer", 1234))
val rawBatch = RawBatch(insertQuery, "asdf", Int.box(123), Long.box(1234L))

sSession.batch(Seq(deleteBatch, rawBatch))
```

## Type Mapping

### Cassandra 3.0+ on Java 8

| Cassandra Type |             Scala Type                 |
|:--------------:|:--------------------------------------:|
| varchar        | String                                 |
| uuid           | java.util.UUID                         |
| inet           | java.net.InetAddress                   |
| int            | Int                                    |
| bigint         | Long                                   |
| boolean        | Boolean                                |
| double         | Double                                 |
| varint         | BigInt                                 |
| decimal        | BigDecimal                             |
| float          | Float                                  |
| blob           | Array[Byte]                            |
| list           | List                                   |
| map            | Map                                    |
| set            | Set                                    |
| tuple          | Tuple*
| **timestamp**  | **java.util.Date**                     |
| **date**       | **com.datastax.driver.core.LocalDate** |
| **time**       | **Time**                               |

* Time is a type specific to this library so as not to conflict with `bigint` and `Long`. it is defined as
  
```scala
final case class Time(millis: Long)
```
  
* There are implicit overrides for both the Joda library and Jdk8 Time library that take advantage of Cassandra's new 
  codecs. These codecs have to be registered with your `Cluster` instance; there is a helper function that does this
* when using tuples, you must make the `Cluster` instance implicit, due to tuples' dependency on the specific codecs 
  registered with the cluster in Cassandra 3.0+.

#### Joda Implicits

```scala
// under the hood, DateTime uses a tuple, meaning the cluster must be implicit
implicit val c: Cluster = _ // your cluster
com.weather.scalacass.joda.register(c)
import com.weather.scalacass.joda.Implicits._

val r: Row = _ // some row from your cluster
r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
r.as[org.joda.time.LocalDate]("mydate") // cassandra "date"
r.as[org.joda.time.LocalTime]("mytime") // cassandra "time"
r.as[org.joda.time.DateTime]("mydt") // cassandra "tuple<timestamp,varchar>"
```

[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#joda-time) for information about the format of `DateTime`

#### Jdk8 Date Implicits

```scala
// under the hood ZonedDateTime uses a tuple, meaning the cluster must be implicit
implicit val c: Cluster = _ // your cluster
com.weather.scalacass.jdk8.register(c)
import com.weather.scalacass.jdk8.Implicits._

val r: Row = _ // some row from your cluster
r.as[java.time.Instant]("mytimestamp") // cassandra "timestamp"
r.as[java.time.LocalDate]("mydate") // cassandra "date"
r.as[java.time.LocalTime]("mytime") // cassandra "time"
r.as[java.time.ZonedDateTime]("myzdt") // cassandra "tuple<timestamp,varchar>"
```

[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#jdk-8) for information about the format of `ZonedDateTime`

### Cassandra 2.1 on Java 7

| Cassandra Type |      Scala Type      |
|:--------------:|:--------------------:|
| varchar        | String               |
| uuid           | java.util.UUID       |
| inet           | java.net.InetAddress |
| int            | Int                  |
| bigint         | Long                 |
| boolean        | Boolean              |
| double         | Double               |
| varint         | BigInt               |
| decimal        | BigDecimal           |
| float          | Float                |
| blob           | Array[Byte]          |
| list           | List                 |
| map            | Map                  |
| set            | Set                  |
| tuple          | Tuple*               |
| **timestamp**  | **java.util.Date**   |

* There is an implicit override for the Joda library. Unfortunately it transitively goes through `java.util.Date`,
  so any performance issues related to `java.util.Date` apply

```scala
import com.weather.scalacass.joda.Implicits._

val r: Row = _ // some row from your cluster
r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
```

## Custom Types

If you want to use a Scala type outside those listed above, you can provide a custom encoder/decoder for it in 2 ways:

### Map Over an Existing Type

This is the easier way to create a custom type since you only need to provide conversions to/from existing types

* **CassFormatDecoder** and **CassFormatEncoder** object `apply` methods summon an existing conversion as a starting
  point. (this is equivalent to using `implicitly[CassFormatDecoder[ExistingType]]`)
  
```scala
implicit val iDecoder = CassFormatDecoder[java.util.Date].map(d: java.util.Date => new org.joda.time.Instant(d))
val r: Row = _ // some row
r.as[org.joda.time.Instant]("mytimestamp") // reads from a timestamp

implicit val iEncoder = CassFormatEncoder[java.util.Date].map(i: org.joda.time.Instant => new java.util.Date(i.getMillis))
case class Person(name: String, birthday: org.joda.time.Instant)
val p = Person("newborn baby", org.joda.time.Instant.now)
val ss: ScalaSession = _ // your session instance
ss.insert("mytable", p) // writes a (string, timestamp)
```
if your conversion has a chance to fail, you can also use `flatMap` that utilizes the `Either[Throwable, T]` type. For
instance, let's say you are storing a `java.util.UUID` as a varchar instead of the built-in uuid:
```scala
implicit val uuidDecoder = CassFormatDecoder[String].flatMap(str: String => Try(java.util.UUID.fromString(str)) match {
  case scala.util.Success(uuid) => Right(uuid)
  case scala.util.Failure(exc) => Left(exc)
})
val r: Row = _ // some row
r.as[java.util.UUID]("myvarchar") // reads from a varchar, throws IllegalArgumentException if "myvarchar" is not a UUID

implicit val uuidEncoder = CassFormatEncoder[String].map(uuid: java.util.UUID => uuid.toString) // this one can't fail, so no flatMap
case class Item(uuid: java.util.UUID, name: String)
val i = Item(java.util.UUID.randomUUID, "my-item")
val ss: ScalaSession = _ // your session instance
ss.insert("mytable", i) // writes a (varchar, varchar)
}
```

### Create a New Type From Scratch

If there is any special logic not possible in a `map`/`flatMap`, you can construct an instance to handle it

```scala
implicit val iDecoder = new CassFormatDecoder[org.joda.time.Instant] {
  type From = java.util.Date // describes the type of the value as directly extracted from the Java driver
  val clazz = classOf[From] // just the Class of From. If you know a way to specify this inside the trait, let me know
  def f2t(f: From): Either[Throwable, T] = Right(new org.joda.time.Instant(f)) // failable conversion between From and T
  def extract(r: Row, name: String): From = r getTimestamp name // how to get an instance of From from Cassandra
  def tupleExtract(tup: TupleValue, pos: Int): From = tup getTimestamp pos // how to get an instance of From from a Cassandra TupleValue
}
val r: Row = _ // some row
r.as[org.joda.time.Instant]("mytimestamp") // reads from a timestamp

implicit val iEncoder = new CassFormatEncoder[org.joda.time.Instant] {
  type To = java.util.Date // describes the type of the value needed for the Java driver
  val cassDataType = com.datastax.driver.core.DataType.timestamp // the Cassandra data type
  def encode(t: org.joda.time.Instant): Either[Throwable, To] = Right(new java.util.Date(i.getMillis)) // can't fail
}
case class Person(name: String, birthday: org.joda.time.Instant)
val p = Person("newborn-baby", org.joda.time.Instant.now)
val ss: ScalaSession = _ // your session instance
ss.insert("mytable", p) // writes a (string, timestamp)
```

## Parsing Performance

performance is pretty decent.

Measurements taken by extracting a String 10,000 times with [Thyme](https://github.com/Ichoran/thyme)

compare the implementations of `as`:

```scala
// with ScalaCass
row.as[String]("str")

// native
if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str"))
```

and `getAs`:

```scala
// ScalaCass
row.getAs[String]("str")

// Java driver
if (row.getColumnDefinitions.contains("str") && !row.isNull("str")) Some(row.getString("str")) else None)
```

|           |   as   |  getAs |
|:---------:|:------:|:------:|
| ScalaCass | 6.92us | 6.71us |
|   Native  | 6.88us | 7.80us |

ScalaCass is 99.392% the speed of native for `as`, 106.209% the speed of native for `getAs`  

Measurements take by extracting to a case class 10,000 times with [Thyme](https://github.com/Ichoran/thyme)

given the case class:

```scala
case class Strings(str1: String, str2: String, str3: String, str4: Option[String])
```

compare the implementations of `as` for a case class:

```scala
// ScalaCass
row.as[Strings]

// Java driver
def get(name: String) = if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str")
def getAs(name: String) = if (row.getColumnDefinitions.contains(name) && !row.isNull(name)) Some(row.getString(name)) else None

Strings(get("str"), get("str2"), get("str3"), getAs("str4")
```

and `getAs`:

```scala
// ScalaCass
row.getAs[Strings]

// Java driver
for {
  s1 <- getAs("str")
  s2 <- getAs("str2")
  s3 <- getAs("str3")
  s4  = getAs("str4")
} yield Strings(s1, s2, s3, s4)
```

|                             |   as   |  getAs |
|:---------------------------:|:------:|:------:|
|          ScalaCass          | 68.1us | 65.7us |
| ScalaCass w/ cachedImplicit | 39.3us | 39.1us |
|            Native           | 30.5us | 36.5us |

ScalaCass alone is 44.844% the speed of native for `as`, 55.557% the speed of native for `getAs`

ScalaCass w/ `cachedImplicit` is 77.664% the speed of native for `as`, 93.372% the speed of native for `getAs`

* `cachedImplicit` is a feature of shapeless that caches the underlying representation of the case class so that it does
  not need to be recreated on every call. [see more here](https://github.com/milessabin/shapeless/blob/master/core/src/main/scala/shapeless/package.scala#L118) 
  (WARNING: source code)
