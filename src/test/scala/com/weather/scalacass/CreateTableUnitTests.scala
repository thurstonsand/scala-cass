package com.weather.scalacass

import com.weather.scalacass.util.CassandraUnitTester

import scala.collection.JavaConverters._
import scala.language.reflectiveCalls

class CreateTableUnitTests extends CassandraUnitTester {
  val dbName = "testDB"
  val tableColumns = List("str varchar, str2 varchar, i int")
  val primaryKeys = List("str")

  override def beforeAll(): Unit = {
    super.beforeAll()
    client.session.execute(s"CREATE KEYSPACE $dbName WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    ()
  }

  def ssFixture = new {
    val ss = new ScalaSession(dbName)(client.session)
    val tname = s"createTableTest${java.util.UUID.randomUUID.toString.take(5)}"
  }
  case class A(str: String, str2: String, i: Int)

  "createTable" should "reject a table without primary key" in {
    case class AA(str: String, str2: String, i: Int)
    ssFixture.ss.createTable[AA]("createTableTest", 0, 0).execute.left.toOption.value shouldBe a[WrongPrimaryKeySizeException]
  }

  def getpk[T : CCCassFormatDecoder : CCCassFormatEncoder](tname: String, pkCount: Int, clustCount: Int) = {
    ssFixture.ss.createTable[T](tname, pkCount, clustCount).execute()
    val table = cluster.getMetadata.getKeyspace(dbName).getTable(tname)
    val parts = table.getPartitionKey.asScala.map(_.getName)
    val clust = table.getClusteringColumns.asScala.map(_.getName)
    (parts, clust)
  }

  "createTable10" should "create a table" in {
    val f = ssFixture
    val (parts, clust) = getpk[A](f.tname, 1, 0)
    parts should contain("str")
    parts should not contain "str2"
    parts should not contain "i"

    clust shouldBe empty

    f.ss.dropTable(f.tname).execute()
  }
  "createTable11" should "create a table" in {
    val f = ssFixture
    val (parts, clust) = getpk[A](f.tname, 1, 1)

    parts should contain("str")
    parts should not contain "str2"
    parts should not contain "i"

    clust should not contain "str"
    clust should contain("str2")
    clust should not contain "i"

    f.ss.dropTable(f.tname).execute()
  }
  "createTable20" should "create a table" in {
    val f = ssFixture
    val (parts, clust) = getpk[A](f.tname, 2, 0)

    parts should contain("str")
    parts should contain("str2")
    parts should not contain "i"

    clust shouldBe empty

    f.ss.dropTable(f.tname).execute()
  }
  "createTable21" should "create a table" in {
    val f = ssFixture
    val (parts, clust) = getpk[A](f.tname, 2, 1)

    parts should contain("str")
    parts should contain("str2")
    parts should not contain "i"

    clust should not contain "str"
    clust should not contain "str2"
    clust should contain("i")

    f.ss.dropTable(f.tname).execute()
  }
  "createTable12" should "create a table" in {
    val f = ssFixture
    val (parts, clust) = getpk[A](f.tname, 1, 2)

    parts should contain("str")
    parts should not contain "str2"
    parts should not contain "i"

    clust should not contain "str"
    clust should contain("str2")
    clust should contain("i")

    f.ss.dropTable(f.tname).execute()
  }
}
