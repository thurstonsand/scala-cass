---
layout: docs
title: "Batch"
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
```
# Batch Statements

Insert, updates, and deletes can be batched into a single statement sent to Cassandra, and, using a batch type of 
`Logged`, will either all succeed or all fail. There are performance implications to using batch statements, which you 
can read about [here](https://docs.datastax.com/en/cql/3.1/cql/cql_using/useBatch.html).

In the Scala-Cass library, you can model a batch statement by passing prepared statements to a batch statement, which
can be accomplished in a number of ways:

#### batchOf

```tut
case class Query(s: String)
case class NewValue(i: Int, l: Long)
val updateStatement = sSession.update("mytable", NewValue(1234, 5678L), Query("some str"))

case class Insertable(s: String, i: Int, l: Long)
val insertStatement = sSession.insert("mytable", Insertable("some other str", 4321, 8765L))

val deleteStatement = sSession.deleteRow("mytable", Query("a third str"))

val batchOfStatement = sSession.batchOf(updateStatement, insertStatement, deleteStatement)
batchOfStatement.execute()
```

#### batch

```tut
val statementsToBatch = List(updateStatement, insertStatement, deleteStatement)
val batchStatement = sSession.batch(statementsToBatch)
batchStatement.execute()
```


#### + (build)
```tut
val oneBatchStatement = sSession.batchOf(updateStatement)
val twoBatchStatement = oneBatchStatement + insertStatement
val threeBatchStatement = twoBatchStatement + deleteStatement
threeBatchStatement.execute()
```

#### ++ (append)

```tut
val fromListBatchStatement = oneBatchStatement ++ List(insertStatement, deleteStatement)
fromListBatchStatement.execute()
```
```tut
val otherBatchStatement = sSession.batchOf(insertStatement, deleteStatement)
val fromOtherBatchStatement = oneBatchStatement ++ otherBatchStatement
fromOtherBatchStatement.execute()
```

#### and (build multiple)

```tut
val andBatchStatement = oneBatchStatement and (insertStatement, deleteStatement)
andBatchStatement.execute()
```

## Batch Type

You can additionally specify the batch type of the statement, but it defaults to `LOGGED`.

```tut
import com.datastax.driver.core.BatchStatement
val withTypeBatchStatement = batchStatement.withBatchType(BatchStatement.Type.LOGGED)
withTypeBatchStatement.execute()
```
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```