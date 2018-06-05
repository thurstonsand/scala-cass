---
layout: docs
title: "Select"
section: "c21"
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

val insertStatement = sSession.insert("mytable", MyTable("a str", 1234, 5678L))
insertStatement.execute()
```
# Select

Selects can retrieve the entire row, or only specific columns of the row. The query and the column specifier, if needed,
are both represented via case classes.

In addition, you can choose to retrieve only a single row from Cassandra, represented by an `Option[Row]` response, 
where the Option will be `None` if no values were found that matched the query.

## Select Whole Row

For selects that will return the entire row:

```tut
case class Query(s: String)
```
##### Retrieve all rows matching the query
```tut
val selectStatement = sSession.selectStar("mytable", Query("a str"))
selectStatement.execute()
```
##### Retrieve a single row matching the query
```tut
val selectOneStatement = sSession.selectOneStar("mytable", Query("a str"))
selectOneStatement.execute()
```

## Select Columns

For selects that only retrieve certain columns of that row, specify the columns as a case class. However, you will not
actually use an instance of the case class in the statement, just pass it in as type parameter:

```tut
case class ColumnsToRetrieve(s: String, l: Long)
```
```tut
val selectColumnsStatement = sSession.select[ColumnsToRetrieve]("mytable", Query("a str"))
selectColumnsStatement.execute()

val selectColumnsOneStatement = sSession.selectOne[ColumnsToRetrieve]("mytable", Query("a str"))
selectColumnsOneStatement.execute()
```

## Allow Filtering

You can `ALLOW FILTERING` on the request (read more about ["allow filtering" here](https://www.datastax.com/dev/blog/allow-filtering-explained-2))

```tut
val selectAllowFiltering = selectStatement.allowFiltering
selectAllowFiltering.execute()

val selectOneAllowFiltering = selectOneStatement.allowFiltering
selectOneAllowFiltering.execute()
```

You can remove the allow filtering option:

```tut
val selectNoAllowFiltering = selectAllowFiltering.noAllowFiltering
selectNoAllowFiltering.execute()

val selectOneNoAllowFiltering = selectOneAllowFiltering.noAllowFiltering
selectOneNoAllowFiltering.execute()
```

## Limit

For queries that will return an iterator of responses (ie, not `selectOne` statements), you can impose a limit on the
number of responses:

```tut
val selectLimit = selectStatement.limit(100)
selectLimit.execute()
```

Finally, you can disable the imposed limit:

```tut
val selectNoLimit = selectLimit.noLimit
selectNoLimit.execute()
```

## Reading from the `Row`s

Scala-Cass provides a Scala-style method of extraction for `Row`, either into Scala values, or directly into case 
classes.

* [Click here](/cass3/row-extraction.html) for a tutorial on how to extract values from `Row`
* [Click here](/cass3/type-mappings.html) for a mapping of Cassandra types to Scala types
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```