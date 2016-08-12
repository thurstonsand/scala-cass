package com.weather.scalacass

trait LowPriorityCassFormatEncoderVersionSpecific {
  import CassFormatEncoder.{sameTypeCassFormatEncoder, transCassFormatEncoder}

  implicit val dateFormat: CassFormatEncoder[java.util.Date] = sameTypeCassFormatEncoder("timestamp")
  implicit val datastaxLocalDateFormat: CassFormatEncoder[com.datastax.driver.core.LocalDate] =
    sameTypeCassFormatEncoder("date")
  implicit val timeFormat: CassFormatEncoder[Time] = transCassFormatEncoder("time", time => Long.box(time.millis))
}
