---
layout: docs
title: "Delete"
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
# Delete

Deletes are executed with a query and optional columns, both of which are represented via case classes.

For deletes that will delete the entire row:

```tut
case class Query(s: String)
val deleteStatement = sSession.deleteRow("mytable", Query("some str"))
deleteStatement.execute()
```

For deletes that only delete certain columns of that row, specify the columns as a case class. However, you will not
actually use an instance of the case class in the statement, just pass it in as type parameter:

```tut
case class ColumnsToRemove(i: Int)
val deleteColumnsStatement = sSession.delete[ColumnsToRemove]("mytable", Query("some str"))
deleteColumnsStatement.execute()
```

## If Statment

You can use case classes to model If statements. For now, only equivalency is possible. This means that the values
in the if statement are translated to an `=` comparison:

```tut
case class If(l: Long)
val deleteWithIf = deleteStatement.`if`(If(5678L))
deleteWithIf.execute()
```

You can just specify `IF EXISTS` as well:
 
```tut
val deleteWithIfExists = deleteStatement.ifExists
deleteWithIfExists.execute()
```

You can remove any if clause:

```tut
val deleteWithoutIf = deleteWithIf.noConditional
deleteWithoutIf.execute()
```

## Timestamp

You can specify a timestamp:

```tut
val timestampDelete = deleteStatement.usingTimestamp(System.currentTimeMillis)
timestampDelete.execute()
```

or use shorthand for current time:

```tut
val timestampNowDelete = deleteStatement.usingTimestampNow
timestampNowDelete.execute()
```

and finally, remove a timestamp from the statement:

```tut
val noTimestampDelete = timestampDelete.noTimestamp
noTimestampDelete.execute()
```
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```