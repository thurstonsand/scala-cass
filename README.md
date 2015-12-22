# fast-cass
### light Cassandra wrapper that makes retrieval from Rows a little easier

usage is simple: `import CassandraHelper.RichRow` and you're on your way  
```scala
r: Row = getARow()
val myStr: String = r.as[String]("mystr")
val myMap: Map[String, Long] = r.as[Map[String, Long]]("mymap")
val myBoolOpt: Option[Boolean] = r.getAs[Boolean]("mybool")
val myBlobOpt: Option[Array[Byte]] = r.getAs[Array[Byte]]("myblob")
```
etc
