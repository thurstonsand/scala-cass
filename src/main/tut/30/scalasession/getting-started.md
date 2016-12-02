---
layout: docs
title: "Getting Started"
section: "c30"
---
# Getting Started with ScalaSession

using a `ScalaSession` follows the same general rules as creating the Java driver's `Session`. The major difference is 
that this library requires a cluster instance in implicit scope when working with tuples. This is because tuple types 
are defined based on the specific codecs associated with a cluster instance. 

effectively, this means that,

```tut:silent
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

```tut:silent
import com.datastax.driver.core.Session
import com.weather.scalacass.ScalaSession

implicit val session: Session = cluster.connect()

val sSession: ScalaSession = ScalaSession("createdkeyspace") // picks up session implicitly
```

You can create the keyspace by passing in parameters included after the WITH:

```tut
val createStatement = sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 3}")
createStatement.execute()
```

Additionally, you can specify `IF NOT EXISTS` like

```tut
createStatement.ifNotExists.execute()
```

```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```