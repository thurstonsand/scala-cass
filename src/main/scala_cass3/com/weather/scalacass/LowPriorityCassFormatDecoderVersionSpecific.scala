package com.weather.scalacass

import scala.util.Try

trait LowPriorityCassFormatDecoderVersionSpecific {
  import CassFormatDecoder.{TryEither, sameTypeCassFormat}
  implicit val dateFormat: CassFormatDecoder[java.util.Date] =
    sameTypeCassFormat[java.util.Date](classOf[java.util.Date], _ getTimestamp _)
  implicit val datastaxLocalDateFormat: CassFormatDecoder[com.datastax.driver.core.LocalDate] =
    sameTypeCassFormat[com.datastax.driver.core.LocalDate](classOf[com.datastax.driver.core.LocalDate], _ getDate _)
  implicit val localDateTimeFormat: CassFormatDecoder[java.time.LocalDateTime] =
    dateFormat.flatMap(d => Try(java.time.LocalDateTime.ofInstant(d.toInstant, java.time.ZoneId.systemDefault)).toEither)
}