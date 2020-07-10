package com.weather.scalacass.scsession

class InsertUnitTests extends ActionUnitTests {
  "insert" should "use IF NOT EXISTS" in {
    val query = ss.insert(table, Table("some str", 1234, None)).ifNotExists
    println(query.getStringRepr)
    println(query.execute().getOrElse(None))
  }

  it should "use TIMESTAMP" in {
    val query = ss
      .insert(table, Table("some str", 1234, Some(123)))
      .usingTimestamp(System.currentTimeMillis)
    println(query.getStringRepr)
    println(query.execute().getOrElse(None))
  }

  it should "use TTL" in {
    val query =
      ss.insert(table, Table("some str", 1234, Some(123))).usingTTL(12345)
    println(query.getStringRepr)
    println(query.execute().getOrElse(None))
  }

  it should "use everything" in {
    val query = ss
      .insert(table, Table("some str", 1234, Some(123)))
      .ifNotExists
      .usingTTL(12345)
    val query2 = ss
      .insert(table, Table("some str", 1234, Some(123)))
      .usingTimestampNow
      .usingTTL(12345)
    println(query.getStringRepr)
    println(query.execute().getOrElse(None))
    println(query2.getStringRepr)
    println(query2.execute().getOrElse(None))
  }

  it should "insert where a row has a string with a $ in it" in {
    val query =
      ss.insert(table, Table("""{ "$regex": /yeppers/ }""", 1234, Some(123)))
    println(query.getStringRepr)
    println(query.execute().getOrElse(None))
  }
}
