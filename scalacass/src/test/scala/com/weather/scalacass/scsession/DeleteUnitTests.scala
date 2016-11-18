package com.weather.scalacass.scsession

import com.google.common.util.concurrent.UncheckedExecutionException
import com.weather.scalacass.ScalaSession

class DeleteUnitTests extends ActionUnitTests {
  case class SelectiveDelete(i: Int)
  case class Query(str: String)
  case class IfS(l: Long)

  "delete" should "use selective columns" in {
    val query = ss.delete[SelectiveDelete](table, Query("asdf"))
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "not use selective columns" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf"))
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "use timestamp" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf")).usingTimestamp(12345L)
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "use if exists" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf")).ifExists
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "use if statement" in {
    val query = ss.delete[ScalaSession.NoQuery](table, Query("asdf")).`if`(IfS(1234L))
    println(query.getStringRepr)
    println(query.execute)
  }
  it should "use everything" in {
    val query = ss.delete[SelectiveDelete](table, Query("asdf")).`if`(IfS(1234L)).usingTimestamp(12345L)
    an[UncheckedExecutionException] should be thrownBy query.execute
    println(s"broke: ${query.getStringRepr}")
    val fixedQuery = query.noTimestamp
    println(fixedQuery.getStringRepr)
    println(fixedQuery.execute)
  }
}
