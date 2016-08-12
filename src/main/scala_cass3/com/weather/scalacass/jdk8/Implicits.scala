package com.weather.scalacass.jdk8

import com.weather.scalacass.{CassFormatEncoder, CassFormatDecoder}
import com.weather.scalacass.LowPriorityCassFormatDecoderVersionSpecific.codecCassFormatDecoder
import CassFormatEncoder.sameTypeCassFormatEncoder
import java.time.{LocalTime, LocalDate, ZonedDateTime, Instant}

object Implicits {
  implicit val timeEncoder: CassFormatEncoder[LocalTime] = sameTypeCassFormatEncoder("time")
  implicit val timeDecoder: CassFormatDecoder[LocalTime] = codecCassFormatDecoder(classOf[LocalTime])

  implicit val dateEncoder: CassFormatEncoder[LocalDate] = sameTypeCassFormatEncoder("date")
  implicit val dateDecoder: CassFormatDecoder[LocalDate] = codecCassFormatDecoder(classOf[LocalDate])

  implicit val instantEncoder: CassFormatEncoder[Instant] = sameTypeCassFormatEncoder("timestamp")
  implicit val instantDecoder: CassFormatDecoder[Instant] = codecCassFormatDecoder(classOf[Instant])

  implicit val zonedDateTimeEncoder: CassFormatEncoder[ZonedDateTime] = sameTypeCassFormatEncoder("tuple<timestamp,varchar>")
  implicit val zonedDateTimeDecoder: CassFormatDecoder[ZonedDateTime] = codecCassFormatDecoder(classOf[ZonedDateTime])
}
