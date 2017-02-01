---
layout: docs
title: "Creating a Table"
section: "cthree"
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




```scala
scala> case class MyTable(s: String, i: Int, l: Long)
defined class MyTable

scala> val createStatement = sSession.createTable[MyTable]("mytable", 1, 0)
createStatement: com.weather.scalacass.scsession.SCCreateTableStatement = SCCreateTableStatement(CREATE TABLE mykeyspace.mytable (s varchar, i int, l bigint, PRIMARY KEY ((s))))

scala> createStatement.execute()
res1: com.weather.scalacass.Result[com.datastax.driver.core.ResultSet] = Right(ResultSet[ exhausted: true, Columns[]])
```

and you can use the `` `with` `` builder to specify anything after the `WITH`




```scala
scala> val createStatementWith = createStatement.`with`("compaction = { 'class' : 'SizeTieredCompactionStrategy', 'min_threshold' : 6 }")
createStatementWith: com.weather.scalacass.scsession.SCCreateTableStatement = SCCreateTableStatement(CREATE TABLE mykeyspace.mytable (s varchar, i int, l bigint, PRIMARY KEY ((s))) WITH compaction = { 'class' : 'SizeTieredCompactionStrategy', 'min_threshold' : 6 })

scala> createStatementWith.execute()
res3: com.weather.scalacass.Result[com.datastax.driver.core.ResultSet] = Right(ResultSet[ exhausted: true, Columns[]])
```

Finally, you can truncate and drop the table using the `truncateTable` and `dropTable` commands

```scala
scala> val truncateStatement = sSession.truncateTable("mytable")
truncateStatement: com.weather.scalacass.scsession.SCTruncateTableStatement = SCTruncateTableStatement(TRUNCATE TABLE mykeyspace.mytable)

scala> truncateStatement.execute()
res4: com.weather.scalacass.Result[com.datastax.driver.core.ResultSet] = Right(ResultSet[ exhausted: true, Columns[]])
```

```scala
scala> val dropStatement = sSession.dropTable("mytable")
dropStatement: com.weather.scalacass.scsession.SCDropTableStatement = SCDropTableStatement(DROP TABLE mykeyspace.mytable)

scala> dropStatement.execute()
res5: com.weather.scalacass.Result[com.datastax.driver.core.ResultSet] = Right(ResultSet[ exhausted: true, Columns[]])
```



