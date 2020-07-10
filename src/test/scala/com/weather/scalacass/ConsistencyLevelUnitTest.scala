package com.weather.scalacass

import com.datastax.driver.core.ConsistencyLevel
import com.weather.scalacass.scsession.{SCBatchStatement, SCStatement},
SCStatement.RightBiasedEither
import com.weather.scalacass.util.CassandraWithTableTester
import org.scalatest.{Assertion, OptionValues}

object ConsistencyLevelUnitTest {
  val db = "actionsdb"
  val table = "actionstable"
}

class ConsistencyLevelUnitTest
    extends CassandraWithTableTester(
      ConsistencyLevelUnitTest.db,
      ConsistencyLevelUnitTest.table,
      List("str varchar", "otherstr varchar", "d double"),
      List("str")
    )
    with OptionValues {
  import ConsistencyLevelUnitTest.{db, table}
  lazy val ss = ScalaSession(ConsistencyLevelUnitTest.db)(client.session)

  case class Query(str: String)
  case class Insert(str: String, otherstr: String, d: Double)
  case class Update(otherstr: String, d: Double)

  val insertValue = Insert("str", "otherstr", 1234.0)
  val queryValue = Query(insertValue.str)
  val updateValue = Update("updatedStr", 4321.0)

  def checkConsistency[T <: SCStatement[_]](
    statement: T,
    clOpt: Option[ConsistencyLevel]
  ): Assertion = {
    clOpt match {
      case Some(cl) => statement.toString should include(s"<CONSISTENCY $cl>")
      case None     => statement.toString should not include "<CONSISTENCY"
    }
    statement.prepareAndBind() match {
      case Left(ex) => fail(ex)
      case Right(bound) =>
        bound.preparedStatement.getConsistencyLevel shouldBe clOpt.orNull
    }
  }

  def fullCheck[T <: SCStatement[_]](statement: T)(
    plusConsistency: (T, ConsistencyLevel) => T,
    minusConsistency: T => T,
    cl: ConsistencyLevel
  ): Assertion = {
    val statementWithConsistency = plusConsistency(statement, cl)
    val statementWithNoConsistency = minusConsistency(statement)

    checkConsistency(statement, None)
    checkConsistency(statementWithConsistency, Some(cl))
    checkConsistency(statement, None)
    checkConsistency(statementWithConsistency, Some(cl))
    checkConsistency(statementWithNoConsistency, None)
  }

  "setting consistency" should "work with inserts" in {
    fullCheck(ss.insert(table, insertValue))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.ONE
    )
  }

  it should "work with updates" in {
    fullCheck(ss.update(table, updateValue, queryValue))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.LOCAL_ONE
    )
  }

  it should "work with selects" in {
    fullCheck(ss.selectStar(table, queryValue))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.SERIAL
    )
    fullCheck(ss.select[Update](table, queryValue))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.SERIAL
    )
    fullCheck(ss.selectOneStar(table, queryValue))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.SERIAL
    )
    fullCheck(ss.selectOne[Update](table, queryValue))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.SERIAL
    )
  }

  it should "work with deletes" in {
    fullCheck(ss.deleteRow(table, queryValue))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.ANY
    )
  }

  it should "work with raw" in {
    fullCheck(
      ss.rawStatement(
        s"INSERT INTO $db.$table (str, otherstr, d) VALUES (?, ?, ?)"
      )
    )(_ consistency _, _.defaultConsistency, ConsistencyLevel.LOCAL_QUORUM)
    fullCheck(ss.rawSelectOne(s"SELECT * FROM $db.$table WHERE str=? LIMIT 1"))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.LOCAL_SERIAL
    )
    fullCheck(ss.rawSelect(s"SELECT * FROM $db.$table WHERE str=?"))(
      _ consistency _,
      _.defaultConsistency,
      ConsistencyLevel.LOCAL_SERIAL
    )
  }

  it should "work with batches" in {
    def checkConsistencyBatch(statement: SCBatchStatement,
                              clOpt: Option[ConsistencyLevel]): Assertion = {
      clOpt match {
        case Some(cl) => statement.toString should include(s"<CONSISTENCY $cl>")
        case None     => statement.toString should not include "<CONSISTENCY"
      }
      statement.mkBatch match {
        case Left(value) => fail(value)
        case Right(bound) =>
          bound.getSerialConsistencyLevel shouldBe clOpt.getOrElse(
            cluster.getConfiguration.getQueryOptions.getSerialConsistencyLevel
          )
      }
    }

    val statement = ss.batchOf(ss.insert(table, insertValue))
    val statementWithConsistency =
      statement.consistency(ConsistencyLevel.LOCAL_SERIAL)
    val statementWithNoConsistency = statementWithConsistency.defaultConsistency

    checkConsistencyBatch(statement, None)
    checkConsistencyBatch(
      statementWithConsistency,
      Some(ConsistencyLevel.LOCAL_SERIAL)
    )
    checkConsistencyBatch(statement, None)
    checkConsistencyBatch(
      statementWithConsistency.consistency(ConsistencyLevel.SERIAL),
      Some(ConsistencyLevel.SERIAL)
    )
    checkConsistencyBatch(statementWithNoConsistency, None)
  }
}
