package com.weather.scalacass.scsession

class InsertUnitTests extends ActionUnitTests {
  "insert" should "use IF NOT EXISTS" in {
    val query = ss.insert(table, Table("some str", 1234, None)).ifNotExists
    println(query.getStringRepr)
    println(query.execute.right.toOption.value)
  }

  it should "use TIMESTAMP" in {
    val query = ss.insert(table, Table("some str", 1234, Some(123))).usingTimestamp(System.currentTimeMillis)
    println(query.getStringRepr)
    println(query.execute.right.toOption.value)
  }

  it should "use TTL" in {
    val query = ss.insert(table, Table("some str", 1234, Some(123))).usingTTL(12345)
    println(query.getStringRepr)
    println(query.execute.right.toOption.value)
  }

  it should "use everything" in {
    val query = ss.insert(table, Table("some str", 1234, Some(123))).ifNotExists.usingTimestampNow.usingTTL(12345)
    println(query.getStringRepr)
    println(query.execute.right.toOption.value)
  }
}
