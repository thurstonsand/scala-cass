package com.weather.scalacass

object ScalaCassUnitTestsVersionSpecific {
  type BadTypeException = com.datastax.driver.core.exceptions.CodecNotFoundException
}

trait ScalaCassUnitTestsVersionSpecific { this: ScalaCassUnitTests =>
  "timestamp (datastax date)" should "be extracted correctly" in testType[com.datastax.driver.core.LocalDate, Int]("ts", com.datastax.driver.core.LocalDate.fromDaysSinceEpoch(1000), com.datastax.driver.core.LocalDate.fromDaysSinceEpoch(10000))
  "timestamp (java 8 date)" should "be extracted correctly" in testType[java.time.LocalDateTime, String]("ts", java.time.LocalDateTime.now, java.time.LocalDateTime.now.minusDays(1))
}