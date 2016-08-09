package com.weather.scalacass

trait LowPriorityCassFormatEncoderVersionSpecific {
  import CassFormatEncoder.{sameTypeCassFormatEncoder, transCassFormatEncoder}

  implicit val datastaxLocalDateFormat: CassFormatEncoder[com.datastax.driver.core.LocalDate] =
    transCassFormatEncoder("timestamp", (ld: com.datastax.driver.core.LocalDate) => Long.box(ld.getMillisSinceEpoch))
  implicit val localDateTimeFormat: CassFormatEncoder[java.time.LocalDateTime] =
    transCassFormatEncoder("timestamp", (ldt: java.time.LocalDateTime) => java.util.Date.from(ldt.atZone(java.time.ZoneId.systemDefault).toInstant))
}
