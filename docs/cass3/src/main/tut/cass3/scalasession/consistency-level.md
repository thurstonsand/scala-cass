---
layout: docs
title: "Consistency Level"
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
# Consistency Level

By default, statements will use the same consistency as set for the entire cluster (via the `QueryOptions`).
But, Cassandra also allows for statement-specific consistency levels to be used for insert, select, update, delete, batch, and raw statements.

They all act the same way, so while the examples below focus on insert, the same rules apply to all of the above.

```tut
import com.datastax.driver.core.ConsistencyLevel
case class Insertable(s: String, i: Int, l: Long) // same as the table
val insertStatement = sSession.insert("mytable", Insertable("some str", 1234, 5678L))
val consistencyInsert = insertStatement.consistency(ConsistencyLevel.ONE)
consistencyInsert.execute()
```

And remove it if necessary, which will mean the statement will be written with the consistency level set for the cluster:

```tut
val noConsistencyInsert = insertStatement.defaultConsistency
noConsistencyInsert.execute()
```
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```