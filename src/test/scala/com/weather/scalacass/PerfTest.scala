package com.weather.scalacass

import com.datastax.driver.core.Session
import util.EmbedCassandra
import org.scalatest.FlatSpec
import ScalaCass.RichRow
import scalaz._, Scalaz._
import ScalaCass._

class PerfTest extends FlatSpec with EmbedCassandra {
  var session: Session = null
  val db = "perfdb"

  override def beforeAll() {
    super.beforeAll()
    session = client.session
  }


  def time[T](fn: => T) = {
    val start = System.currentTimeMillis()
    val res = fn
    (res, System.currentTimeMillis() - start)
  }

  def runTest[T](testName: String, fn: => T) = {
    val (_, duration) = time(List.fill(10000)(fn))
    println(s"$testName implementation took $duration ms")
  }

  "string repeats" should "be decent" in {
    session.execute(s"CREATE KEYSPACE $db WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    session.execute(s"CREATE TABLE $db.strperf (str varchar, PRIMARY KEY ((str)))")
    session.execute(s"INSERT INTO $db.strperf (str) VALUES (?)", java.util.UUID.randomUUID.toString)
    val row = session.execute(s"SELECT * FROM $db.strperf").one()
    val rr = RichRow(row)
    List.fill(10000)(row.as[String]("str")) // warm something up
    runTest("RichRow", row.as[String]("str"))
    runTest("RichRow no implicit", rr.as[String]("str"))
    runTest("native", row.getString("str"))
    println

    runTest("RichRow Some", row.getAs[String]("str"))
    runTest("RichRow no implicit Some", rr.getAs[String]("str"))
    runTest("native some", (row.getColumnDefinitions.contains("str") && !row.isNull("str")) option row.getString("str"))
    println

    runTest("RichRow not in table", row.getAs[String]("fdfdfd"))
    runTest("RichRow no implicit not in table", rr.getAs[String]("fdfdfd"))
    runTest("native not in table", (row.getColumnDefinitions.contains("fdfdfd") && !row.isNull("fdfdfd")) option row.getString("fdfdfd"))
    println

    runTest("RichRow wrong type", row.getAs[Int]("str"))
    runTest("RichRow no implicit wrong type", rr.getAs[Int]("str"))
    println("(no way to represent native wrong type without forcing error)")
  }
}