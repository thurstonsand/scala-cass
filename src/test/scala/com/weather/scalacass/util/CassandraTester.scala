package com.weather.scalacass.util

import com.datastax.driver.core.Session
import org.scalatest.{Matchers, FlatSpec}

abstract class CassandraTester(val dbName: String, tableName: String, tableColumns: List[String], primaryKeys: String) extends FlatSpec with Matchers with EmbedCassandra {
  protected var session: Session = null

  override def beforeAll(): Unit = {
    super.beforeAll()
    session = client.session
  }

  before {
    session.execute(s"CREATE KEYSPACE $dbName WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    session.execute(s"CREATE TABLE $dbName.$tableName ${tableColumns.mkString("(", ", ", ", ")} PRIMARY KEY $primaryKeys)")
  }

  protected def insert(pairs: Seq[(String, AnyRef)]) = {
    val (strs, objs) = pairs.foldLeft(Seq.empty[String], Seq.empty[AnyRef]) { case ((accStr, acc), (nStr, n)) =>
      (nStr +: accStr, n +: acc)
    }
    session.execute(s"INSERT INTO $dbName.$tableName ${strs.mkString("(", ",", ")")} VALUES ${objs.map(_ => "?").mkString("(", ",", ")")}", objs: _*)
  }
  protected def getOne = session.execute(s"SELECT * FROM $dbName.$tableName").one()
}
