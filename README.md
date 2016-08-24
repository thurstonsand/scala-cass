# ScalaCass
## [Cassandra Java Driver](https://github.com/datastax/java-driver) wrapper that makes retrieval from Rows a little easier
[![Build Status](https://travis-ci.org/thurstonsand/scala-cass.svg?branch=master)](https://travis-ci.org/thurstonsand/scala-cass)
[![Join the chat at https://gitter.im/scala-cass/Lobby](https://badges.gitter.im/scala-cass/Lobby.svg)](https://gitter.im/scala-cass/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

### Getting ScalaCass
[you can find it on bintray](https://bintray.com/thurstonsand/maven/scalacass).

Supports **scala 2.10** and **scala 2.11** and
- Cassandra 2.2 on Java 7
- Cassandra 3.0+ on Java 8

#### SBT
Add the jcenter resolver
```scala
resolvers += Resolver.jcenterRepo
```
Add the appropriate version of the library
##### Cassandra 3.0+ with Java 8
```scala
libraryDependencies += "com.github.thurstonsand" %% "scalacass" % "0.4.6"
```
##### Cassandra 2.2 with Java 7
```scala
libraryDependencies += "com.github.thurstonsand" %% "scalacass" % "0.3.6"
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
##### Cassandra 2.2 with Java 7
```xml
<properties>
    <scalaCass.version>0.3.6</scalaCassVersion>
</properties>
```
##### Cassandra 3.0+ with Java 8
```xml
<properties>
    <scalaCass.version>0.4.6</scalaCassVersion>
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
* [row parsing](#row-parsing)
* [parsing performance](#parsing-performance)
* [type mappings](#type-mapping)
* [custom types](#custom-types)
* [session utilities](#session-utilities)
  * [`ScalaSession` creation](#creating-a-scalasession)
  * [creating tables and table representation](#creating-tables-and-table-representation)
  * [inserts](#insertinsertasync)
  * [updates](#updateupdateasync)
  * [selects](#selectselectasyncselectoneselectoneasync)
  * [selects for only specific columns](#selectcolumnsselectcolumnsasyncselectcolumnsoneselectcolumnsoneasync)
  * [deletes](#deletedeleteasync)
  * [raw queries](#selectrawselectrawasyncselectonerawselectonerawasync)
  * [batch statements](#batch-statements)

### Row Parsing
usage is simple: `import com.weather.scalacass.ScalaCass._` and you're on your way  
```scala
import com.weather.scalacass.ScalaCass._
r: Row = getARow()
val myStr: String = r.as[String]("mystr")
val myMap: Map[String, Long] = r.as[Map[String, Long]]("mymap")
val myBoolOpt: Option[Boolean] = r.getAs[Boolean]("mybool")
val myBlobOpt: Option[Array[Byte]] = r.getAs[Array[Byte]]("myblob")
val myInt: Int = r.getOrElse[Int]("myint", 5)
```
etc

and you can extract with case classes directly from a `Row`:
```scala
case class Person(name: String, age: Int, job: Option[String])
val person: Person = r.as[Person]
val person_?: Option[Person] = r.getAs[Person]
val personWithDefault: Person = r.getOrElse[Person](Person("default name", 24, None))
```
#### Option
in the same way that `getAs` will return a `None` if it does not exist, using `Option[SomeType]` will only extract the value if there are no errors.
### Parsing Performance
performance is pretty decent.

Measurements taken by extracting a String/Case Class 10,000 times with [Thyme](https://github.com/Ichoran/thyme)  
compare the implementation of `as`:
```scala
// with scala-cass
row.as[String]("str")
row.getAs[String]("str")
// native
if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str"))
if (row.getColumnDefinitions.contains("str") && !row.isNull("str")) Some(row.getString("str")) else None)
```

|           |   as   |  getAs |
|:---------:|:------:|:------:|
| ScalaCass | 6.92us | 6.71us |
|   Native  | 6.88us | 7.80us |

ScalaCass is 99.392% the speed of native for `as`, 106.209% the speed of native for `getAs`  

compare the implementation of `as` for a case class:
```scala
case class Strings(str1: String, str2: String, str3: String, str4: Option[String])
// with scala-cass
row.as[Strings]
row.getAs[Strings]
// native as
def get(name: String) = if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str")
Strings(get("str"), get("str2"), get("str3"), if (row.getColumnDefinitions.contains("str") && !row.isNull("str")) Some(row.getString("str"))
// native getAs
def ga(name: String) = if (row.getColumnDefinitions.contains(name) && !row.isNull(name)) Some(row.getString(name)) else None
    def getAs = for {
      s1 <- ga("str")
      s2 <- ga("str2")
      s3 <- ga("str3")
      s4  = ga("str4")
    } yield Strings(s1, s2, s3, s4)
```
|                             |   as   |  getAs |
|:---------------------------:|:------:|:------:|
|          ScalaCass          | 68.1us | 65.7us |
| ScalaCass w/ cachedImplicit | 39.3us | 39.1us |
|            Native           | 30.5us | 36.5us |

ScalaCass alone is 44.844% the speed of native for `as`, 55.557% the speed of native for `getAs`  
ScalaCass w/ `cachedImplicit` is 77.664% the speed of native for `as`, 93.372% the speed of native for `getAs`
* `cachedImplicit` is a feature of shapeless that caches the underlying representation of a case class so that it does not need to be recreated on every call.

### Type Mapping
#### Cassandra 3.0+ on Java 8
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
| **timestamp**  | **java.util.Date**                     |
| **date**       | **com.datastax.driver.core.LocalDate** |
| **time**       | **Time**                               |

* Time is a type specific to this library so as not to conflict with `bigint` and `Long`. it is defined as
```scala
final case class Time(millis: Long)
```
* There are implicit overrides for both the Joda library and Jdk8 Time library that take advantage of Cassandra's new 
codecs. These codecs have to be registered with your `Cluster` instance, which is included as a helper function

##### Joda Implicits
```scala
val c: Cluster = _ // your cluster
com.scalacass.joda.register(c)
import com.scalacass.joda.Implicits._

val r: Row = _ // some row from your cluster
r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
r.as[org.joda.time.LocalDate]("mydate") // cassandra "date"
r.as[org.joda.time.LocalTime]("mytime") // cassandra "time"
r.as[org.joda.time.DateTime]("mydt") // cassandra "tuple<timestamp,varchar>"
```
[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#joda-time) for information about the format of `DateTime`
##### Jdk8 Date Implicits
```scala
val c: Cluster = _ // your cluster
com.scalacass.jdk8.register(c)
import com.scalacass.jdk8.Implicits._

val r: Row = _ // some row from your cluster
r.as[java.time.Instant]("mytimestamp") // cassandra "timestamp"
r.as[java.time.LocalDate]("mydate") // cassandra "date"
r.as[java.time.LocalTime]("mytime") // cassandra "time"
r.as[java.time.ZonedDateTime]("myzdt") // cassandra "tuple<timestamp,varchar>"
```
[See here](https://datastax.github.io/java-driver/manual/custom_codecs/extras/#jdk-8) for information about the format of `ZonedDateTime`
####Cassandra 2.2 on Java 7
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
| **timestamp**  | **java.util.Date**   |

* There is an implicit override the Joda library. Unfortunately it still goes through `java.util.Date`,
so there might be performance issues in parallel execution
```scala
import com.scalacass.joda.Implicits._

val r: Row = _ // some row from your cluster
r.as[org.joda.time.Instant]("mytimestamp") // cassandra "timestamp"
```

### Custom Types
If you have a Scala type outside those listed above, you can provide a custom encoder/decoder for it in 2 ways:
#### Map Over an Existing Type
This is the easier way to create a custom type since you only need to provide conversions to/from existing types
```scala
implicit val iDecoder = CassFormatDecoder[java.util.Date].map(d: java.util.Date => new org.joda.time.Instant(d))
val r: Row = _ // some row
r.as[org.joda.time.Instant]("mytimestamp") // reads from a timestamp

implicit val iEncoder = CassFormatEncoder[java.util.Date].map(i: org.joda.time.Instant => new java.util.Date(i.getMillis))
case class Person(name: String, birthday: org.joda.time.Instant)
val p = Person("newborn-baby", org.joda.time.Instant.now)
val ss: ScalaSession = _ // your session instance
ss.insert("mytable", p) // writes a (string, timestamp)
```
* **CassFormatDecoder** and **CassFormatEncoder** `apply` methods summon an existing implicit conversion, which is done above
(`CassFormatDecoder[java.util.Date]`) to utilize the `map` function. It is equivalent to `implicitly[CassFormatDecoder[T]]`
* for more about `ScalaSession`, [see below](#creating-a-scalasession)

if your conversion has a chance to fail, you can also use `flatMap` that utilizes the `Either[Throwable, T]` type
```scala
// let's imagine you are storing java.util.UUID as a varchar instead of uuid in cassandra...

implicit val uuidDecoder = CassFormatDecoder[String].flatMap(str: String => Try(java.util.UUID.fromString(str)) match {
  case scala.util.Success(uuid) => Right(uuid)
  case scala.util.Failure(exc) => Left(exc)
})
val r: Row = _ // some row
r.as[java.util.UUID]("myvarchar") // reads from a varchar, throws IllegalArgumentException if "str" is not a UUID

implicit val uuidEncoder = CassFormatEncoder[String].map(uuid: java.util.UUID => uuid.toString) // this one can't fail, so no flatMap
case class Item(uuid: java.util.UUID, name: String)
val i = Item(java.util.UUID.randomUUID, "my-item")
val ss: ScalaSession = _ // your session instance
ss.insert("mytable", i) // writes a (varchar, varchar)
}
```
#### Create a New Type From Scratch
If there is any special logic not possible in a `map`/`flatMap`, you can construct an instance to handle it
```scala
implicit val iDecoder = new CassFormatDecoder[org.joda.time.Instant] {
  type From = java.util.Date // describes the type of the value as directly extracted from the Java driver
  val clazz = classOf[From] // just the Class of From. If you know a way to specify this inside the trait, let me know
  def f2t(f: From): Either[Throwable, T] = Right(new org.joda.time.Instant(f)) // failable conversion between From and T
  def extract(r: Row, name: String): From = r getTimestamp name // how to get an instance of From from Cassandra
}
val r: Row = _ // some row
r.as[org.joda.time.Instant]("mytimestamp") // reads from a timestamp

implicit val iEncoder = new CassFormatEncoder[org.joda.time.Instant] {
  type To = java.util.Date // describes the type of the value needed for the Java driver
  val cassType = "timestamp" // the Cassandra data type
  def encode(t: org.joda.time.Instant): Either[Throwable, To] = Right(new java.util.Date(i.getMillis)) // can't fail
}
case class Person(name: String, birthday: org.joda.time.Instant)
val p = Person("newborn-baby", org.joda.time.Instant.now)
val ss: ScalaSession = _ // your session instance
ss.insert("mytable", p) // writes a (string, timestamp)

```

### Session utilities
#### Creating a ScalaSession
```scala
implicit val s: Session = someCassSession
val ss1 = ScalaSession("mykeyspace")(s) // either pass explicitly
val ss2 = ScalaSession("mykeyspace") // or pick up implicitly
```
* `ScalaSession` caches `PreparedStatement`s, and therefore needs a concrete instantiation vs an implicit conversion
* `ScalaSession`s scope to the keyspace level. To access more than 1 keyspace, create multiple `ScalaSession`s with the same `Session`

It is also possible to create the keyspace when instantiating a ScalaSession by providing a non-empty "WITH" statement
```scala
val ss = ScalaSession("mykeyspace", "REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 })"
```
* the String field is prepended with " WITH ", meaning the above cassandra call will look like `CREATE KEYSPACE mykeyspace WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };`

#### Creating Tables and Table Representation
case class representations of a table are ordered left-to-right, partition keys -> clustering keys -> remaining columns
```scala
case class MyTable(str: String, i: Option[Int])
ss.createTable[MyTable]("mytable", 1, 0)
```
* `createTable` takes parameters (tableName, number of partition keys, number of clustering keys)
* The above table takes `str` as a partition key, no clustering keys, and `i` as a regular field
* The underlying Cassandra call looks like `CREATE TABLE mykeyspace.mytable (str varchar, i int, PRIMARY KEY ((str)));`
* an error is thrown if number of partition keys is set to 0 or number of partition and clustering keys is greater than the number of fields in the case class  
* createTable is intended to be used primarily for testing frameworks

it is also possible to pass parameters to the create table function by providing a non-empty String
```scala
ss.createTable[MyTable]("mytable", 1, 0, "COMPACT STORAGE")
```
* like keyspace creation, " WITH " is appended to the String, meaning the above call looks like `CREATE TABLE mykeyspace.mytable (str varchar, i int, PRIMARY KEY ((str))) WITH COMPACT STORAGE;`

#### `insert`/`insertAsync`
```scala
case class MyTable(str: String, i: Option[Int])
// generates """INSERT INTO mykeyspace.mytable (str, i) VALUES ("asdf", 1234)"""
ss.insert("mytable", MyTable("asdf", Some(1234)))
// generates """INSERT INTO mykeyspace.mytable (str) VALUES ("asdf")"""
ss.insertAsync("mytable", MyTable("asdf2", None)).unsafePerformSync
```
* `PreparedStatement`s are created and cached
* In the case of `None` instances, they are removed before the caching phase, so `null` is never written to Cassandra

#### `update`/`updateAsync`
requires both a query case class and update case class
```scala
case class MyTable(str: String, i: Int, f: Float)
case class Query(str: String, i: Int)
case class Update(f: Float)
// table with 2 primary keys, 'str' and 'i', and 1 column 'f'
ss.createTable[MyTable]("mytable", 2, 0)
// generates """UPDATE mykeyspace.mytable SET asdf=4.0 WHERE str="asdf" AND i=2"""
ss.update[Update, Query]("mytable", Update(4.f), Query("asdf", 2))
```

#### `select`/`selectAsync`/`selectOne`/`selectOneAsync`
`select` functions return a Cassandra `Row`, which can be converted into case classes with the `.as` family of implicit functions
```scala
case class MyTable(str: String, i: Option[Int], otherStr: String)
ss.createTable[MyTable]("mytable", 1, 0)

ss.insert("mytable", MyTable("asdf", None, "otherasdf")

case class MyQuery(str: String)
// generates """SELECT * FROM mykeyspace.mytable WHERE str="asdf""""
val res = ss.selectOne("mytable", MyQuery("asdf")) // Some(Row)
res.flatMap(_.getAs[MyTable]) // Some(MyTable("asdf", None, "otherasdf")
```
* In the case of `None` instances, they are removed from the query before searching
* If the `None` is part of a partition key, the query will fail and the underlying Cassandra driver will throw the appropriate error 

`select` and `selectAsync` also take an optional `limit` parameter:
```scala
ss.select("mytable", MyQuery("asdf"), limit=100) // will return a max of 100 rows
```
all 4 functions also take an optional `allowFiltering` parameter
```scala
case class MyTable(str: String, i: Int, b: Boolean)
ss.createTable[MyTable]("mytable", 2, 0) // str and i are partition keys
case class MyQuery(str: String)
ss.select("mytable", MyQuery("asdf")) // will fail with exception, requires "allow filtering"
ss.select("mytable", MyQuery("asdf"), allowFiltering=true) // succeeds
```
if you want to select everything in table, `NoQuery` is provided
 ```scala
 // generates """SELECT * FROM mykeyspace.mytable"""
 ss.select("mytable", ScalaSession.NoQuery()) // returns everything in "mytable"
 ```
#### `selectColumns`/`selectColumnsAsync`/`selectColumnsOne`/`selectColumnsOneAsync`
function signatures are similar to their non-column counterparts, but with an extra type parameter describing which columns to extract
```scala
case class MyTable(str: String, i: Option[Int], otherStr: String)
ss.createTable[MyTable]("mytable", 1, 0)

ss.insert("mytable", MyTable("asdf", None, "otherasdf")

case class MyInterestingFields(i: Option[Int], otherStr: String)
case class MyQuery(str: String)
// generates """SELECT i, otherstr FROM mykeyspace.mytable WHERE str="asdf""""
val res = ss.selectColumnsOne[MyInterestingFields, MyQuery]("mytable", MyQuery("asdf")) // Some(Row)
res.as[MyTable] // throws exception: missing "str" column
res.as[MyInterestingFields] // MyInterestingFields(None, "otherasdf")
```
if you want to query with `*`, `Star` is provided:
```scala
// generates """SELECT * FROM mykeyspace.mytable WHERE str="asdf""""
ss.selectColumns[ScalaSession.Star, MyQuery]("mytable", MyQuery("asdf"))
```
`Star` is impossible to instantiate because it takes an argument of type `Nothing`, but is useful as a type parameter to
the "Columns" family of functions
#### `delete`/`deleteAsync`
```scala
case class MyTable(str: String, str2: Option[String], i: Option[Int])
ss.createTable[MyTable]("mytable", 1, 1)

ss.insert("mytable", MyTable("asdf", Some("zxcv"), Some(1234))
ss.insert("mytable", MyTable("asdf", Some("zxcv2"), None)
// generates """DELETE * FROM mykeyspace.mytable WHERE str="asdf" AND str2="zxcv"""
ss.delete("mytable", MyTable("asdf", Some("zxcv"), None))
ss.select("mytable", MyTable("asdf", None, None)).map(_.as[MyTable]) // returns Iterator(MyTable("asdf", Some("zxcv2"), Some(1234)))

ss.insert("mytable", MyTable("asdf", Some("zxcv"), Some(1234))
// generates """DELETE * FROM mykeyspace.mytable WHERE str="asdf""""
ss.delete("mytable", MyTable("asdf", Some("zxcv"), None))
ss.select("mytable", MyTable("asdf", None, None)) // returns Iterator.empty[Row]
```

if a value is None, it is removed from the query
```scala
ss.insert("mytable", MyTable("asdf", Some("zxcv"), None)
ss.insert("mytable", MyTable("asdf", Some("zxcv2"), None)
// generates """DELETE * FROM mykeyspace.mytable WHERE str="asdf""""
ss.delete("mytable", MyTable("asdf", None, None))
ss.select("mytable", MyTable("asdf", None, None)) // returns Iterator.empty[MyTable]
```
#### `selectRaw`/`selectRawAsync`/`selectOneRaw`/`selectOneRawAsync`
simply takes the query string and AnyRef args, with the benefit of prepared statement caching
```scala
case class MyTable(str: String, i: Option[Int], otherStr: Str)
ss.createTable[MyTable]("mytable", 1, 0)

ss.insert("mytable", MyTable("asdf", None, "otherasdf")
ss.selectOneRaw("SELECT * FROM mykeyspace.mytable WHERE str=?", "asdf").as[MyTable] // returns MyTable("asdf", None, "otherasdf")
```

#### insertRaw, insertRawAsync, deleteRaw, or deleteRawAsync
as with `selectRaw`, uses direct query string with benefits of caching
```scala
ss.insertRaw("INSERT INTO mykeyspace.mytable (str str2) VALUES (?,?)", "asdf", "zxcv")
ss.insertRaw("INSERT INTO mykeyspace.mytable (str str2) VALUES (?,?)", "asdf", "zxcv2")
ss.deleteRaw("DELETE * FROM mykeyspace.mytable WHERE str=? AND str2=?", "asdf", "zxcv")
ss.select("mytable", MyTable("asdf", None, None)) // returns Iterator(MyTable("asdf", Some("zxcv2"), Some(1234)))
```

#### Batch statements
batch statements are now supported via a handful of case classes:
```scala
case class MyTable(str: String, i: Int, f: Float)
case class Query(str: String, i: Int)
case class Update(f: Float)

ss.createTable[MyTable]("mytable", 1, 1)

val updateBatch = UpdateBatch("mytable", Update(4.f), Query("asdf", 2))
val deleteBatch = DeleteBatch("mytable", Query("asdf", 2)
val insertBatch = InsertBatch("mytable", MyTable("fdsa", 1234, 43.21f))
// results in a single row with ("fdsa", 1234, 43.21)
ss.batch(Seq(updateBatch, deleteBatch, insertBatch))
```

the session utilities are experimental, and suggestions/pull requests are welcome
