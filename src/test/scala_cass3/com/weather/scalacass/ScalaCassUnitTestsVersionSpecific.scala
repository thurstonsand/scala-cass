package com.weather.scalacass

object ScalaCassUnitTestsVersionSpecific {
  type BadTypeException = com.datastax.driver.core.exceptions.CodecNotFoundException
  val extraHeaders = List("ts timestamp", "dt date", "t time", "specialdt tuple<timestamp,varchar>")
}

trait ScalaCassUnitTestsVersionSpecific { this: ScalaCassUnitTests =>
  "date (datastax date)" should "be extracted correctly" in testType[com.datastax.driver.core.LocalDate, Int]("dt", com.datastax.driver.core.LocalDate.fromDaysSinceEpoch(1000), com.datastax.driver.core.LocalDate.fromDaysSinceEpoch(10000))
  "time (long)" should "be extracted correctly" in testType[Time, Int]("t", Time(12345L), Time(54321L))
  "timestamp (java util date)" should "be extracted correctly" in testType[java.util.Date, Int]("ts", new java.util.Date(56565L), new java.util.Date(65656L))
}

class JodaScalaCassUnitTests extends ScalaCassUnitTests {
  override def beforeAll(): Unit = {
    super.beforeAll()
    com.weather.scalacass.joda.register(client.cluster)
  }
  import com.weather.scalacass.joda.Implicits._

  "date (joda date)" should "be extracted correctly" in testType[org.joda.time.LocalDate, Int]("dt", org.joda.time.LocalDate.now, org.joda.time.LocalDate.now.plusDays(1))
  "time (joda time)" should "be extracted correctly" in testType[org.joda.time.LocalTime, Int]("t", org.joda.time.LocalTime.MIDNIGHT, org.joda.time.LocalTime.MIDNIGHT.plusMinutes(4))
  "timestamp (joda instant)" should "be extracted correctly" in testType[org.joda.time.Instant, Int]("ts", org.joda.time.Instant.now, org.joda.time.Instant.now.plus(12345L))
  "datetime (joda datetime)" should "be extracted correctly" in testType[org.joda.time.DateTime, Int]("specialdt", org.joda.time.DateTime.now, org.joda.time.DateTime.now.plusHours(4))
}

class Jdk8ScalaCassUnitTests extends ScalaCassUnitTests {
  override def beforeAll(): Unit = {
    super.beforeAll()
    com.weather.scalacass.jdk8.register(client.cluster)
  }
  import com.weather.scalacass.jdk8.Implicits._

  "date (jdk8 date)" should "be extracted correctly" in testType[java.time.LocalDate, Int]("dt", java.time.LocalDate.now, java.time.LocalDate.now.plusDays(1))
  "time (jdk8 time)" should "be extracted correctly" in testType[java.time.LocalTime, Int]("t", java.time.LocalTime.NOON, java.time.LocalTime.MIDNIGHT)
  "timestamp (jdk8 instant)" should "be extracted correctly" in testType[java.time.Instant, Int]("ts", java.time.Instant.now, java.time.Instant.now.plusSeconds(56L))
  "datetime (jdk8 datetime)" should "be extracted correctly" in testType[java.time.ZonedDateTime, Int]("specialdt", java.time.ZonedDateTime.now, java.time.ZonedDateTime.now.plusHours(4))
}