package com.weather.scalacass

trait LowPriorityCassFormatDecoderVersionSpecific {
  import CassFormatDecoder.{sameTypeCassFormat, safeConvertCassFormat}
  implicit val dateFormat: CassFormatDecoder[java.util.Date] =
    sameTypeCassFormat[java.util.Date](classOf[java.util.Date], _ getTimestamp _)
  implicit val datastaxLocalDateFormat: CassFormatDecoder[com.datastax.driver.core.LocalDate] =
    sameTypeCassFormat[com.datastax.driver.core.LocalDate](classOf[com.datastax.driver.core.LocalDate], _ getDate _)
  implicit val timeFormat: CassFormatDecoder[Time] = safeConvertCassFormat[Time, java.lang.Long](classOf[java.lang.Long], Time.apply(_), (r, name) => Time(r.getTime(name)))
}