package com.weather.scalacass

import com.weather.scalacass.util.CassandraUnitTester
import org.scalactic.source.Position
import org.scalatest._

class ComparableSelectionTest extends CassandraUnitTester with Matchers {
  private val keyspace = "mykeyspace"
  private val table: String = "mytable"
  private val ss: ScalaSession = ScalaSession(keyspace)

  case class Table(str: String, l: Long, i: Option[Int])
  case class Query(str: String, l: Comparable[Long])

  override def beforeAll(): Unit = {
    super.beforeAll()
    ss.createKeyspace(
        "replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"
      )
      .execute()
    ss.createTable[Table](table, 1, 1).execute()
    ()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    ss.truncateTable(table).execute()
    ()
  }

  "for comparable query" should "allow to select using comparison" in {
    val maxRows = 10
    val maxKeysInRow = 10L
    (1 to maxRows) flatMap { rowIdx =>
      (1L to maxKeysInRow) map (rowIdx -> _)
    } map {
      case (rowIdx, idx) => Table(s"key-$rowIdx", idx, Some(rowIdx))
    } foreach { row =>
      ss.insert(table, row).execute()
    }

    def checkComparable(c: Comparable[Long],
                        expected: Int)(implicit pos: Position): Assertion = {
      val stmt = ss.selectStar(table, Query("key-1", c))
      val resultsE = stmt.execute()
      resultsE.right.value.toList.size shouldEqual expected
    }

    checkComparable(Equal(2), 1)
    checkComparable(Less(2), 1)
    checkComparable(LessEqual(2), 2)
    checkComparable(Greater(2), 8)
    checkComparable(GreaterEqual(2), 9)
  }
}
