package com.weather.scalacass

import com.datastax.driver.core.UDTValue
import com.weather.scalacass.util.{ CassandraUnitTester, CassandraWithTableTester }
import syntax._

class UDTUnitTests extends CassandraUnitTester {
  val dbName = "udtDB"
  val udtName = "myudt"
  val tableName = "udtTable"
  override def beforeAll(): Unit = {
    super.beforeAll()
    client.session.execute(s"CREATE KEYSPACE $dbName WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    client.session.execute(s"CREATE TYPE $dbName.$udtName (str varchar, str2 ascii, b blob, d decimal, f float, net inet, tid timeuuid, vi varint, i int, bi bigint, bool boolean, dub double, l list<varchar>, m map<varchar, bigint>, s set<double>, ts timestamp, id uuid, sblob set<blob>);")
    client.session.execute(s"CREATE TABLE $dbName.$tableName (k varchar PRIMARY KEY, u frozen<$udtName>);")
    ()
  }

  case class MyUDT(str: String, b: Array[Byte], i: Int, l: Option[List[String]])
  case class UDTTable(k: String, u: UDT[MyUDT])

  "udt case class" should "be extracted correctly" in {
    val udt = MyUDT("asdf", "aaff".getBytes, 1, None)
    val table = UDTTable("key", UDT(udt))

    val udtV = client.cluster.getMetadata.getKeyspace(dbName).getUserType(udtName).newValue()
      .setString("str", udt.str)
      .setBytes("b", java.nio.ByteBuffer.wrap(udt.b))
      .setInt("i", Int.box(udt.i))
    insert(Seq(("k", table.k), ("u", udtV)))

    val one = getOne.getAs[UDTTable].value
    new String(one.u.value.b) shouldBe new String(udt.b)
    one.k shouldEqual table.k
    one.u.value.str shouldEqual table.u.value.str
    one.u.value.b should contain theSameElementsInOrderAs table.u.value.b
    one.u.value.i shouldEqual table.u.value.i
    one.u.value.l shouldEqual table.u.value.l
  }

  private def insert(pairs: Seq[(String, AnyRef)]) = {
    val (strs, objs) = pairs.unzip
    client.session.execute(s"INSERT INTO $dbName.$tableName ${strs.mkString("(", ",", ")")} VALUES ${objs.map(_ => "?").mkString("(", ",", ")")}", objs: _*)
  }
  private def getOne = client.session.execute(s"SELECT * FROM $dbName.$tableName").one()
}
