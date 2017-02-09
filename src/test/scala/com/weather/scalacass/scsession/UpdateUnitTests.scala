package com.weather.scalacass.scsession

import com.google.common.util.concurrent.UncheckedExecutionException

class UpdateUnitTests extends ActionUnitTests {

  case class Query(str: String)
  case class Update(l: Long, i: Option[Int])
  case class IfS(l: Long)

  "update" should "use IF EXISTS" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).ifExists
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "use ttl" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).usingTTL(1234)
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "use timestamp" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).usingTimestamp(12345L)
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "use if statement" in {
    val query = ss.update(table, Update(123, Some(123)), Query("asdf")).`if`(IfS(123L))
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "combine all of them" in {
    val query = ss.update(table, Update(123, None), Query("asdf")).usingTTL(1234).`if`(IfS(123L)).usingTimestamp(12345L)
    an[UncheckedExecutionException] should be thrownBy query.execute()
    println(s"broke: ${query.getStringRepr}")
    val fixedQuery = query.noTimestamp
    println(fixedQuery.getStringRepr)
    println(fixedQuery.noTimestamp.execute)
  }
}
