package com.weather.scalacass

import com.weather.scalacass.util.CassandraTester
import org.scalatest.OptionValues
import ScalaCass._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class ScalaSessionUnitTests extends CassandraTester("testDB", "testTable", List("str varchar", "str2 ascii", "b blob",
  "d decimal", "f float", "net inet", "tid timeuuid", "vi varint", "i int", "bi bigint", "bool boolean", "dub double",
  "l list<varchar>", "m map<varchar, bigint>", "s set<double>", "ts timestamp", "id uuid", "sblob set<blob>"), List("str")) with OptionValues {

  case class A(str: String, str2: String, s: Set[Double])
  implicit val s = session
  "it" should "do something" in {
    val ss = new ScalaSession(dbName)
    val a = A("asdf", "fdsa", Set(123.4))
    ss.insert(tableName, a)
    ss.select(tableName, a, 1).toList.head shouldBe a

    val table2 = "testTable2"
    case class B(otherStr: String, aBool: Boolean, finalStr: String)
    val b = new B("asdf", false, "fdfd")
    ss.createTable[B](table2, 1, 0)
    ss.insert(table2, b)
    ss.selectOne(table2, b).value shouldBe new B("asdf", false, "fdfd")
    Await.result(ss.selectOneAsync(table2, b, 1), Duration.Inf).value shouldBe b
  }
  "createTable" should "create a table" in {
    val ss = new ScalaSession(dbName)

  }


//  it should "do more" in {
//    import scala.collection.JavaConverters._
//    val tablesMetadata = session.getCluster.getMetadata.getKeyspace("testDB").getTables.asScala
//    def getTableInfo(table: String) = {
//      val a = tablesMetadata.collectFirst {
//        case t if t.getName == table.toLowerCase =>
//          val tt = t.getPrimaryKey
//          tt
//          true
//      }
//      a
//    }
//    getTableInfo("testTable")
//  }
}
