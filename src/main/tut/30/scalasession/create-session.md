---
layout: docs
title: "Create Session"
section: "c30"
---
# Create Session

using a ScalaSession follows the same general rules as creating the regular Java Session. The major difference is that
this library requires a cluster instance in implicit scope when working with tuples. This is because tuple types are 
defined based on the specific codecs associated with a cluster instance. 

effectively, this means that,

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

The session itself is a class that you must keep around, much like you would a Cassandra Java Session. This is because
the ScalaSession caches PreparedStatements automatically, so if you are calling the same request multiple times, it will
use an existing PreparedStatement instead of generating a new statement every time. the instantiation takes an optional
String with the right hand side of the keyspace definition, if you want it to automatically create the keyspace for you
(this will likely only really be used for testing purposes).

```scala
import com.datastax.driver.core.Session
import com.weather.scalacass.ScalaSession

implicit val session: Session = cluster.connect()

val sSessionCreateKeyspace = ScalaSession("mykeyspace").createKeyspace(
  "replication = {'class':'SimpleStrategy', 'replication_factor' : 3}"
)
val sSession = ScalaSession("mykeyspace")(session) // if mykeyspace already exists
```

In addition to creating a keyspace, a `ScalaSession` can delete the keyspace. This would make any subsequent calls via
that `ScalaSession` invalid

```scala
sSession.dropKeyspace()
```

Finally, you can close the associated `Session` for shutdown.

```scala
sSession.close()
```