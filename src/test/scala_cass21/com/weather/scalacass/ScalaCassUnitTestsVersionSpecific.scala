package com.weather.scalacass

object ScalaCassUnitTestsVersionSpecific {
  type BadTypeException = com.datastax.driver.core.exceptions.InvalidTypeException
  val extraHeaders = List("ts timestamp")
}

trait ScalaCassUnitTestsVersionSpecific { this: ScalaCassUnitTests =>
  "timestamp (java util date)" should "be extracted correctly" in testType[java.util.Date, Int]("ts", new java.util.Date(56565L), new java.util.Date(65656L))
}

class JodaScalaCassUnitTests extends ScalaCassUnitTests {
  import com.weather.scalacass.joda.Implicits._

  "timestamp (joda instant)" should "be extracted correctly" in testType[org.joda.time.Instant, Int]("ts", org.joda.time.Instant.now, org.joda.time.Instant.now.plus(12345L))
}

class Jdk8ScalaCassUnitTests extends ScalaCassUnitTests {
  import com.weather.scalacass.jdk8.Implicits._

  "timestamp (jdk instant)" should "be extracted correctly" in testType[java.time.Instant, Int]("ts", java.time.Instant.now, java.time.Instant.now.plus(12345L, java.time.temporal.ChronoUnit.MILLIS))
}
