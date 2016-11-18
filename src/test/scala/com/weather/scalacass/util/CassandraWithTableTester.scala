package com.weather.scalacass.util

abstract class CassandraWithTableTester(val dbName: String, protected val tableName: String, tableColumns: List[String], primaryKeys: List[String]) extends CassandraUnitTester {
  override def beforeAll(): Unit = {
    super.beforeAll()
    client.session.execute(s"CREATE KEYSPACE $dbName WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    client.session.execute(s"CREATE TABLE $dbName.$tableName ${tableColumns.mkString("(", ", ", ",")} PRIMARY KEY ${primaryKeys.mkString("((", ", ", "))")})")
    ()
  }
  override def afterEach(): Unit = {
    client.session.execute(s"TRUNCATE TABLE $dbName.$tableName")
    super.afterEach()
  }

  protected def insert(pairs: Seq[(String, AnyRef)]) = {
    val (strs, objs) = pairs.unzip
    client.session.execute(s"INSERT INTO $dbName.$tableName ${strs.mkString("(", ",", ")")} VALUES ${objs.map(_ => "?").mkString("(", ",", ")")}", objs: _*)
  }
  protected def getOne = client.session.execute(s"SELECT * FROM $dbName.$tableName").one()
}
