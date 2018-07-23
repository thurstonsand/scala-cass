package com.weather.scalacass.scsession

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.weather.scalacass.Result

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

class UpdateUnitTests extends ActionUnitTests {

  case class Query(str: String)
  case class Update(l: Long, i: Option[Int])
  case class IfS(l: Long)

  def executeAsync[T](q: SCStatement[T], shouldSucceed: Boolean = true)(implicit ec: ExecutionContext): Result[T] = {
    val res = Await.result(q.executeAsync()(ec), 3.seconds)
    res.isRight shouldBe shouldSucceed
    res
  }

  "update" should "use IF EXISTS" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).ifExists
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "use ttl" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).usingTTL(1234)
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "use timestamp" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).usingTimestamp(12345L)
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "use if statement" in {
    val query = ss.update(table, Update(123, Some(123)), Query("asdf")).`if`(IfS(123L))
    println(query.getStringRepr)
    println(executeAsync(query))
  }
  it should "combine all of them" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).usingTTL(1234).`if`(IfS(123L)).usingTimestamp(12345L)
    val executed = query.executeAsync()
    Await.ready(executed, 3.seconds)
    executed.value.value.failure.exception shouldBe an[InvalidQueryException]
    query.execute().left.value shouldBe an[InvalidQueryException]

    println(s"broke: ${query.getStringRepr}")
    val fixedQuery = query.noTimestamp
    println(fixedQuery.getStringRepr)
    println(executeAsync(fixedQuery.noTimestamp))
  }
}
