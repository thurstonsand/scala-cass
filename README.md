# fast-cass
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
performance is not very good. Measurements taken from getting a String 10,000 times and averaging.

|                     |    as   |  getAs  | getAs not in table | getAs wrong type |
|:-------------------:|:-------:|:-------:|:------------------:|:----------------:|
|       RichRow       | 167.5us | 169.5us |        .9us        |      118.7us     |
| RichRow no implicit | 163.6us | 167.5us |        .7us        |      133.7us     |
|        Native       |   .8us  |  1.5us  |        .3us        |        n/a       |
`RichRow no implicit` means that I created a RichRow instance and used it directly.  
Caching could potentially speed up results. Will have to test further
