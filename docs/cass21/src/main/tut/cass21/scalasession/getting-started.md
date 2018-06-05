---
layout: docs
title: "Getting Started"
section: "c21"
---
# Getting Started with ScalaSession

Using a `ScalaSession` follows the same general rules as creating the Java driver's `Session`. 

```tut:silent
import com.datastax.driver.core.Cluster

val cluster = Cluster.builder.addContactPoint("localhost").build()
```

## Characteristics

* `PreparedStatement` caching
* acts on a single keyspace
* can optionally create a keyspace on instantiation
* can pick up Java `Session` implicitly
* provides an `execute`, which blocks for the result, and `executeAsync`, which returns a `Future` of the result

The `ScalaSession` itself is a class that you must keep around, much like you would a Cassandra Java `Session`. This is 
because the ScalaSession caches PreparedStatements from every executed command, so if you are calling the same command 
multiple times, it will use an existing PreparedStatement instead of generating a new statement every time.

```tut
import com.datastax.driver.core.Session, com.weather.scalacass.ScalaSession

implicit val session: Session = cluster.connect()

val sSession: ScalaSession = ScalaSession("mykeyspace") // picks up session implicitly
```

If the keyspace has not been created yet (namely in tests), you can create it using `createKeyspace` and passing in 
parameters included after the `WITH` statement:

```tut
val createStatement = sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 3}")
createStatement.execute()
```

Additionally, you can specify `IF NOT EXISTS` using the `ifNotExists` builder

```tut
val createStatementIfNotExists = createStatement.ifNotExists
val result = createStatementIfNotExists.execute()
```

Finally, you can drop the keyspace if you are done using it, although this will render the `ScalaSession` unusable until
the keyspace is created again

```tut
sSession.dropKeyspace.execute()
```
```tut:invisible
sSession.close()
cluster.close()
```