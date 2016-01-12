# scala-cass
### light Cassandra wrapper that makes retrieval from Rows a little easier

usage is simple: `import CassandraHelper.RichRow` and you're on your way  
```scala
import com.weather.fastcass.CassandraHelper.RichRow
r: Row = getARow()
val myStr: String = r.as[String]("mystr")
val myMap: Map[String, Long] = r.as[Map[String, Long]]("mymap")
val myBoolOpt: Option[Boolean] = r.getAs[Boolean]("mybool")
val myBlobOpt: Option[Array[Byte]] = r.getAs[Array[Byte]]("myblob")
```
etc
### Performance
performance is decent. Measurements taken from getting a String 10,000 times.

|                     |  as  | getAs | getAs not in row | getAs wrong type |
|:-------------------:|:----:|:-----:|:----------------:|:----------------:|
|       RichRow       | 23ms | 19ms  |       7ms        |       171ms      |
| RichRow no implicit | 9ms  | 11ms  |       6ms        |       54ms       |
|        Native       | 2ms  | 10ms  |       3ms        |       n/a        |
`RichRow no implicit` means that I created a RichRow instance and used it directly.  
Caching could potentially speed up results. Will have to test further
