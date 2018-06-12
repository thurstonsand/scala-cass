package com.weather.scalacass

import com.datastax.driver.core.{ ConsistencyLevel, ResultSet }
import com.weather.scalacass.scsession.SCStatement
import com.weather.scalacass.util.CassandraWithTableTester
import org.scalatest.{ Assertion, OptionValues }

object SessionActionsUnitTest {
  val db = "actionsdb"
  val table = "actionstable"
}

class SessionActionsUnitTest extends CassandraWithTableTester(SessionActionsUnitTest.db, SessionActionsUnitTest.table,
  List("str varchar", "otherstr varchar", "d double"),
  List("str")) with OptionValues {
  import SessionActionsUnitTest.table
  lazy val ss = ScalaSession(SessionActionsUnitTest.db)(client.session)

  case class Query(str: String)
  case class Insert(str: String, otherstr: String, d: Double)
  case class Update(otherstr: String, d: Double)

  val insertValue = Insert("str", "otherstr", 1234.0)
  val queryValue = Query(insertValue.str)
  val updateValue = Update("updatedStr", 4321.0)

  def checkConsistency(statement: SCStatement[ResultSet], clOpt: Option[ConsistencyLevel]): Assertion = {
    clOpt match {
      case Some(cl) => statement.toString should include(s"<CONSISTENCY $cl>")
      case None     => statement.toString should not include "<CONSISTENCY"
    }
    val bound = statement.prepareAndBind().toOption.value
    bound.preparedStatement.getConsistencyLevel shouldBe clOpt.orNull
  }
  "setting consistency" should "work as expected" in {
    val statement = ss.insert(table, insertValue)
    val statementWithConsistency = statement.consistency(ConsistencyLevel.ONE)
    val statementWithDiffConsistency = statementWithConsistency.consistency(ConsistencyLevel.THREE)
    val statementWithNoConsistency = statementWithDiffConsistency.defaultConsistency

    checkConsistency(statement, None)
    checkConsistency(statementWithConsistency, Some(ConsistencyLevel.ONE))
    checkConsistency(statement, None)
    checkConsistency(statementWithConsistency, Some(ConsistencyLevel.ONE))
    checkConsistency(statementWithDiffConsistency, Some(ConsistencyLevel.THREE))
    checkConsistency(statementWithNoConsistency, None)
  }

  it should "work with updates too" in {
    val statement = ss.update(table, updateValue, queryValue)
    val statementWithConsistency = statement.consistency(ConsistencyLevel.LOCAL_ONE)
    val statementWithNoConsistency = statementWithConsistency.defaultConsistency

    checkConsistency(statement, None)
    checkConsistency(statementWithConsistency, Some(ConsistencyLevel.LOCAL_ONE))
    checkConsistency(statement, None)
    checkConsistency(statementWithConsistency, Some(ConsistencyLevel.LOCAL_ONE))
    checkConsistency(statementWithNoConsistency, None)
  }

}
