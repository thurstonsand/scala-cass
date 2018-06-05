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

case class MyTable(s: String, i: Int, l: Long, li: List[String])
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

## Add/Subtract

There is a special class available to specify that you would like to either add or subtract elements
to a Casssandra collection. Namely, `UpdateBehavior.Add` and `UpdateBehavior.Subtract`.

```tut
import com.weather.scalacass.syntax._
case class NewValueList(li: UpdateBehavior[List, String])
val updateStatementAdd = sSession.update("mytable", NewValueList(UpdateBehavior.Add(List("a", "b", "c"))), Query("some str"))
updateStatementAdd.execute()
sSession.selectOneStar("mytable", Query("some str")).execute()
```

```tut
val updateStatementSubtract = sSession.update("mytable", NewValueList(UpdateBehavior.Subtract(List("a", "b"))), Query("some str"))
updateStatementSubtract.execute()
sSession.selectOneStar("mytable", Query("some str")).execute()
```

For parity, there is also `UpdateBehavior.Replace`, but simply using a class directly will act in the same way.

Using `UpdateBehavior.Replace`:

```tut
val updateStatementReplace = sSession.update("mytable", NewValueList(UpdateBehavior.Replace(List("d", "e", "f"))), Query("some str"))
updateStatementReplace.execute()
sSession.selectOneStar("mytable", Query("some str")).execute()
```

Using regular `List`:

```tut
case class NewValueListRegular(li: List[String])
val updateStatementRegular = sSession.update("mytable", NewValueListRegular(List("g", "h", "i")), Query("some str"))
updateStatementRegular.execute()
sSession.selectOneStar("mytable", Query("some str")).execute()
```

## If Statment

You can use case classes to model If statements. For now, only equivalency is possible, meaning values
in the if statement are translated to an `=` comparison. If you need a different comparison operation,
see [raw statements](/cass21/scalasession/raw.html):

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