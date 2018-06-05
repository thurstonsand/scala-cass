package com.weather.scalacass.scsession

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.weather.scalacass.{ Result, ScalaSession }

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

class DeleteUnitTests extends ActionUnitTests {
  case class SelectiveDelete(i: Int)
  case class Query(str: String)
  case class IfS(l: Long)

  def executeAsync[T](q: SCStatement[T], shouldSucceed: Boolean = true)(implicit ec: ExecutionContext): Result[T] = {
    val res = Await.result(q.executeAsync()(ec), 3.seconds)
    res.isRight shouldBe shouldSucceed
    res
  }

  "delete" should "use selective columns" in {
    val query = ss.delete[SelectiveDelete](table, Query("asdf"))
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "not use selective columns" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf"))
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "use timestamp" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf")).usingTimestamp(12345L)
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "use if exists" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf")).ifExists
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "use if statement" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf")).`if`(IfS(1234L))
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "use everything" in {
    val query = ss.delete[SelectiveDelete](table, Query("asdf")).`if`(IfS(1234L)).usingTimestamp(12345L)
    val executed = query.executeAsync()
    Await.ready(executed, 3.seconds)
    executed.value.value.failed.toOption.value shouldBe an[InvalidQueryException]
    query.execute().left.toOption.value shouldBe an[InvalidQueryException]

    println(s"broke: ${query.getStringRepr}")
    val fixedQuery = query.noTimestamp
    println(fixedQuery.getStringRepr)
    println(fixedQuery.execute())
  }
}
