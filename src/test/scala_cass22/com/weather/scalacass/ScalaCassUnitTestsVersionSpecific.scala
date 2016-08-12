package com.weather.scalacass

object ScalaCassUnitTestsVersionSpecific {
  type BadTypeException = com.datastax.driver.core.exceptions.InvalidTypeException
  val extraHeaders = List("ts timestamp")
}

trait ScalaCassUnitTestsVersionSpecific { this: ScalaCassUnitTests =>
  "timestamp (java util date)" should "be extracted correctly" in testType[java.util.Date, Int]("ts", new java.util.Date(56565L), new java.util.Date(65656L))
}