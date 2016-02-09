# scala-cass
### Light Cassandra wrapper that makes retrieval from Rows a little easier

usage is simple: `import ScalaCass._` and you're on your way  
```scala
import com.weather.scalacass.ScalaCass._
r: Row = getARow()
val myStr: String = r.as[String]("mystr")
val myMap: Map[String, Long] = r.as[Map[String, Long]]("mymap")
val myBoolOpt: Option[Boolean] = r.getAs[Boolean]("mybool")
val myBlobOpt: Option[Array[Byte]] = r.getAs[Array[Byte]]("myblob")
```
etc

and now with case classes:
```scala
case class Person(name: String, age: Int, job: Option[String])
val person = r.realize[Person]
```
### Performance
performance is pretty decent.

Measurements taken by extracting a String/Case Class 10,000 times with [Thyme](https://github.com/Ichoran/thyme)  
compare the implementation of `as`:
```scala
// with scala-cass
row.as[String]("str")
// native
if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str"))
```

|           |  as  | getAs |
|:---------:|:----:|:-----:|
|  RichRow  | 9us  | 8us   |
|   Native  | 5us  | 6us   |
RichRow is 56.255% the speed of native for `as`, 79.617% the speed of native for `getAs`  
  
compare the implementation of `as`:
```scala
case class Strings(str1: String, str2: String, str3: String, str4: Option[String])
// with scala-cass
row.as[Strings]
// native
def get(name: String) = if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str")
Strings(get("str"), get("str2"), get("str3"), if (row.getColumnDefinitions.contains("str") && !row.isNull("str")) Some(row.getString("str"))
```
|                     |  as  | getAs |
|:-------------------:|:----:|:-----:|
|       RichRow       | 43us | 44us  |
|        Native       | 22us | 28us  |
RichRow is 50.455% the speed of native for `as`, 62.707% the speed of native for `getAs`  


Caching could potentially speed up results. Will have to test further