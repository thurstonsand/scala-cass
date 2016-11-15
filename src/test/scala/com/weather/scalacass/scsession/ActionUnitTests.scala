package com.weather.scalacass.scsession

import com.weather.scalacass.ScalaSession
import com.weather.scalacass.util.CassandraUnitTester

trait ActionUnitTests extends CassandraUnitTester {
  protected val keyspace = "mykeyspace"
  private var _table: String = _
  private var _ss: ScalaSession = _

  case class Table(str: String, l: Long, i: Option[Int])

  protected def table = _table

  protected def ss = _ss

  override def beforeAll(): Unit = {
    super.beforeAll()
    _ss = ScalaSession(keyspace, "replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
    _table = "mytable" // s"mytable_${java.util.UUID.randomUUID.toString.take(5)}"
    ss.createTable[Table](table, 1, 0)
    ()
  }
  override def beforeEach(): Unit = {
    super.beforeEach()
    ss.truncateTable(table)
    ()
  }
  override def afterEach(): Unit = {}
}
