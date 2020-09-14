package com.weather.scalacass

import com.weather.scalacass.util.{ CassandraUnitTester, CassandraWithTableTester }
import syntax._

class UDTUnitTests extends CassandraUnitTester {
  val dbName = "udtDB"
  val udtName = "myudt"
  val tableName = "udtTable"
  override def beforeAll(): Unit = {
    super.beforeAll()
    client.session.execute(s"CREATE KEYSPACE $dbName WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    client.session.execute(s"CREATE TYPE $dbName.$udtName (str varchar, str2 ascii, b blob, d decimal, f float, net inet, tid timeuuid, vi varint, i int, bi bigint, bool boolean, dub double, l list<varchar>, m map<varchar, bigint>, s set<double>, ts timestamp, id uuid, sblob set<blob>")
    client.session.execute(s"CREATE TABLE $dbName.$tableName (k varchar, u frozen<$udtName> PRIMARY KEY ((k)))")
    ()
  }

  case class MyUDT(str: String, b: Array[Byte], i: Int, l: Option[List[String]])
  case class UDTTable(k: String, u: MyUDT)

  "udt case class" should "be extracted correctly" in {
    val udt = MyUDT("asdf", "aaff".getBytes, 1, None)
    insert(Seq(("str", udt.str), ("b", java.nio.ByteBuffer.wrap(udt.b)), ("i", Int.box(udt.i))))

    getOne.attemptAs[UDTTable] shouldBe Right(udt)
  }

  private def insert(pairs: Seq[(String, AnyRef)]) = {
    val (strs, objs) = pairs.unzip
    client.session.execute(s"INSERT INTO $dbName.$tableName ${strs.mkString("(", ",", ")")} VALUES ${objs.map(_ => "?").mkString("(", ",", ")")}", objs: _*)
  }
  private def getOne = client.session.execute(s"SELECT * FROM $dbName.$tableName").one()
}
