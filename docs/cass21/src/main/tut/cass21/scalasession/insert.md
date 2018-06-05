---
layout: docs
title: "Insert"
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
# Insert

Use case classes to model the data to insert

```tut
case class Insertable(s: String, i: Int, l: Long) // same as the table
val insertStatement = sSession.insert("mytable", Insertable("some str", 1234, 5678L))
insertStatement.execute()
```

## If Statement

You can also specify existence of the row:

```tut
val ifNotExistsInsert = insertStatement.ifNotExists
ifNotExistsInsert.execute()
```

Or remove the existence check:

```tut
val noIfNotExistsInsert = ifNotExistsInsert.noConditional
noIfNotExistsInsert.execute()
```

## TTL

You can add a TTL:

```tut
val ttlInsert = insertStatement.usingTTL(12345)
ttlInsert.execute()
```

And remove the TTL:

```tut
val noTtlInsert = ttlInsert.noTTL
noTtlInsert.execute()
```

## Timestamp

You can specify a timestamp:

```tut
val timestampInsert = insertStatement.usingTimestamp(System.currentTimeMillis)
timestampInsert.execute()
```

Or use shorthand for current time:

```tut
val timestampNowInsert = insertStatement.usingTimestampNow
timestampNowInsert.execute()
```

And finally, remove a timestamp from the statement:

```tut
val noTimestampInsert = timestampInsert.noTimestamp
noTimestampInsert.execute()
```
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```