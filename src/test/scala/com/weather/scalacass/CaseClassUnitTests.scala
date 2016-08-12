package com.weather.scalacass

import com.weather.scalacass.util.CassandraWithTableTester
import ScalaCass._
import org.scalatest.OptionValues

class CaseClassUnitTests extends CassandraWithTableTester("testDB", "testTable", List("str varchar", "str2 ascii", "b blob",
  "d decimal", "f float", "net inet", "tid timeuuid", "vi varint", "i int", "bi bigint", "bool boolean", "dub double",
  "l list<varchar>", "m map<varchar, bigint>", "s set<double>", "ts timestamp", "id uuid", "sblob set<blob>"), List("str")) with OptionValues {
  case class Everything(str: String, d: BigDecimal, f: Float, net: java.net.InetAddress, l: Option[List[String]])
  case class Everything2(str2: String, d: BigDecimal, f: Float, net: java.net.InetAddress, l: Option[List[String]])

  "case class with Everything" should "materialize" in {
    val e = Everything("asdf", BigDecimal(0), 12.0f, java.net.InetAddress.getByName("localhost"), None)
    val e2 = Everything2(e.str, e.d, e.f, e.net, e.l)
    insert(Seq(("str", e.str), ("d", e.d.underlying), ("f", Float.box(e.f)), ("net", e.net)))
    getOne.as[Everything] shouldBe e
    getOne.getAs[Everything] shouldBe Some(e)
    getOne.getOrElse(e.copy(str = "asdf2")) shouldBe e
    getOne.getOrElse(e2) shouldBe e2
  }
}
