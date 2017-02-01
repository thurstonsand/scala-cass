---
layout: docs
title: "Creating a Table"
section: "c3"
---
# Creating a Table

Like creating a keyspace, this is likely only going to be useful to those who are writing tests. Table creation uses a 
case class for the table columns' names and type definitions (see [Type Mappings](/docs/cass3/type-mappings.html) to compare 
Scala and Cassandra types).

## Characteristics

* the `createTable` method takes 4 properties
  * name of table
  * number of partition keys
  * number of clustering keys
* You can pass an optional parameter for the right hand side of the table definition (after the `WITH`) using the 
  `` `with` `` builder
* parameters wrapped in `Option` or `Nullable` take the underlying type parameter as its type for table creation
* you must have at least 1 partition key
* the number of partition keys + clustering keys must be less than the number of fields in the case class
* any rules associated with cassandra semantics for data types must be followed (eg no counters in the primary key)

```tut:invisible
import com.datastax.driver.core.{Cluster, Session}
import com.weather.scalacass.ScalaSession

implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
implicit val session: Session = cluster.connect()

val sSession: ScalaSession = ScalaSession("mykeyspace")
sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 3}").execute()
```

```tut
case class MyTable(s: String, i: Int, l: Long)
val createStatement = sSession.createTable[MyTable]("mytable", 1, 0)
createStatement.execute()
```

and you can use the `` `with` `` builder to specify anything after the `WITH`

```tut:invisible
sSession.dropTable("mytable").execute()
```

```tut
val createStatementWith = createStatement.`with`("compaction = { 'class' : 'SizeTieredCompactionStrategy', 'min_threshold' : 6 }")
createStatementWith.execute()
```

Finally, you can truncate and drop the table using the `truncateTable` and `dropTable` commands

```tut
val truncateStatement = sSession.truncateTable("mytable")
truncateStatement.execute()
```

```tut
val dropStatement = sSession.dropTable("mytable")
dropStatement.execute()
```

```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```