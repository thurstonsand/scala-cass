package com.weather.scalacass.scsession

import com.weather.scalacass.ScalaSession

class SelectUnitTests extends ActionUnitTests {
  case class SelectiveSelect(i: Int)
  case class Query(str: String)

  "select" should "be selective" in {
    val query = ss.select[SelectiveSelect](table, Query("asdf"))
    println(query.getStringRepr.right.toOption.value)
    println(query.execute.right.toOption.value)
  }
  it should "not need to be selective" in {
    val query = ss.select[ScalaSession.Star](table, Query("asdf"))
    println(query.getStringRepr.right.toOption.value)
    println(query.execute.right.toOption.value)
  }
  it should "limit" in {
    val query = ss.select[ScalaSession.Star](table, Query("asdf")).limit(100)
    println(query.getStringRepr.right.toOption.value)
    println(query.execute.right.toOption.value)
  }
  it should "allow filtering" in {
    val query = ss.select[ScalaSession.Star](table, Query("asdf")).allowFiltering
    println(query.getStringRepr.right.toOption.value)
    println(query.execute.right.toOption.value)
  }
  it should "do everything" in {
    val query = ss.select[SelectiveSelect](table, Query("asdf")).limit(100).allowFiltering
    println(query.getStringRepr.right.toOption.value)
    println(query.execute.right.toOption.value)
  }
}
