---
layout: docs
title: "Getting Started"
section: "cthree"
---
# Getting Started with ScalaSession

Using a `ScalaSession` follows the same general rules as creating the Java driver's `Session`. The major difference is 
that this library requires a cluster instance in implicit scope when working with tuples. This is because `tuple` types 
are defined based on the specific codecs associated with a cluster instance.

This means that you need to make the cluster implicit if you are using cassandra's `tuple` types

```scala
import com.datastax.driver.core.Cluster

// implicit is only necessary if using tuple types
implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
```

## Characteristics

* `PreparedStatement` caching
* acts on a single keyspace
* can optionally create a keyspace on instantiation
* can pick up Java `Session` implicitly

The `ScalaSession` itself is a class that you must keep around, much like you would a Cassandra Java `Session`. This is 
because the ScalaSession caches PreparedStatements from every executed command, so if you are calling the same command 
multiple times, it will use an existing PreparedStatement instead of generating a new statement every time.

```scala
scala> import com.datastax.driver.core.Session, com.weather.scalacass.ScalaSession
import com.datastax.driver.core.Session
import com.weather.scalacass.ScalaSession

scala> implicit val session: Session = cluster.connect()
session: com.datastax.driver.core.Session = com.datastax.driver.core.SessionManager@570e481a

scala> val sSession: ScalaSession = ScalaSession("mykeyspace") // picks up session implicitly
sSession: com.weather.scalacass.ScalaSession = ScalaSession(mykeyspace)
```

If the keyspace has not been created yet (namely in tests), you can create it using `createKeyspace` and passing in 
parameters included after the `WITH` statement:

```scala
scala> val createStatement = sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 3}")
createStatement: com.weather.scalacass.scsession.SCCreateKeyspaceStatement = SCCreateKeyspaceStatement(CREATE KEYSPACE mykeyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 3})

scala> createStatement.execute()
res2: com.weather.scalacass.Result[com.datastax.driver.core.ResultSet] = Right(ResultSet[ exhausted: true, Columns[]])
```

Additionally, you can specify `IF NOT EXISTS` using the `ifNotExists` builder

```scala
scala> val createStatementIfNotExists = createStatement.ifNotExists
createStatementIfNotExists: com.weather.scalacass.scsession.SCCreateKeyspaceStatement = SCCreateKeyspaceStatement(CREATE KEYSPACE IF NOT EXISTS mykeyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 3})

scala> val result = createStatementIfNotExists.execute()
result: com.weather.scalacass.Result[com.datastax.driver.core.ResultSet] = Right(ResultSet[ exhausted: true, Columns[]])
```

Finally, you can drop the keyspace if you are done using it, although this will render the `ScalaSession` unusable until
the keyspace is created again

```scala
scala> sSession.dropKeyspace.execute()
res3: com.weather.scalacass.Result[com.datastax.driver.core.ResultSet] = Right(ResultSet[ exhausted: true, Columns[]])
```



