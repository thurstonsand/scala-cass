---
layout: docs
title: "Update"
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
# Update

Use case classes to model both the query and new value for updates:

```tut
case class Query(s: String)
case class NewValue(i: Int, l: Long)
val updateStatement = sSession.update("mytable", NewValue(1234, 5678L), Query("some str"))
updateStatement.execute()
```

## If Statment

You can use case classes to model If statements. For now, only equivalency is possible. This means that the values
in the if statement are translated to an `=` comparison:

```tut
case class If(l: Long)
val updateWithIf = updateStatement.`if`(If(5678L))
updateWithIf.execute()
```

You can just specify `IF EXISTS` as well:
 
```tut
val updateWithIfExists = updateStatement.ifExists
updateWithIfExists.execute()
```

You can remove any if clause:

```tut
val updateWithoutIf = updateWithIf.noConditional
updateWithoutIf.execute()
```

## TTL

you can add a TTL:

```tut
val ttlUpdate = updateStatement.usingTTL(12345)
ttlUpdate.execute()
```

and remove the TTL:

```tut
val noTtlUpdate = ttlUpdate.noTTL
noTtlUpdate.execute()
```

## Timestamp

You can specify a timestamp:

```tut
val timestampUpdate = updateStatement.usingTimestamp(System.currentTimeMillis)
timestampUpdate.execute()
```

or use shorthand for current time:

```tut
val timestampNowUpdate = updateStatement.usingTimestampNow
timestampNowUpdate.execute()
```

and finally, remove a timestamp from the statement:

```tut
val noTimestampUpdate = timestampUpdate.noTimestamp
noTimestampUpdate.execute()
```
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```