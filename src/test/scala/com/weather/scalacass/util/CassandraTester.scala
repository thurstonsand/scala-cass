package com.weather.scalacass.util

abstract class CassandraTester(val dbName: String, protected val tableName: String, tableColumns: List[String], primaryKeys: List[String]) extends EmbedCassandra {
  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  before {
    client.session.execute(s"CREATE KEYSPACE $dbName WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    client.session.execute(s"CREATE TABLE $dbName.$tableName ${tableColumns.mkString("(", ", ", ",")} PRIMARY KEY ${primaryKeys.mkString("((", ", ", "))")})")
  }

  protected def insert(pairs: Seq[(String, AnyRef)]) = {
    val (strs, objs) = pairs.unzip
    client.session.execute(s"INSERT INTO $dbName.$tableName ${strs.mkString("(", ",", ")")} VALUES ${objs.map(_ => "?").mkString("(", ",", ")")}", objs: _*)
  }
  protected def getOne = client.session.execute(s"SELECT * FROM $dbName.$tableName").one()
}
