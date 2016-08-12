package com.weather.scalacass

object ScalaCassUnitTestsVersionSpecific {
  type BadTypeException = com.datastax.driver.core.exceptions.CodecNotFoundException
  val extraHeaders = List("ts timestamp", "dt date", "t time")
}

trait ScalaCassUnitTestsVersionSpecific { this: ScalaCassUnitTests =>
  "date (datastax date)" should "be extracted correctly" in testType[com.datastax.driver.core.LocalDate, Int]("dt", com.datastax.driver.core.LocalDate.fromDaysSinceEpoch(1000), com.datastax.driver.core.LocalDate.fromDaysSinceEpoch(10000))
  "time (long)" should "be extracted correctly" in testType[Time, Int]("t", Time(12345L), Time(54321L))
  "timestamp (java util date)" should "be extracted correctly" in testType[java.util.Date, Int]("ts", new java.util.Date(56565L), new java.util.Date(65656L))
}