package com.weather.scalacass.joda

import com.weather.scalacass.{CassFormatDecoder, CassFormatEncoder}
import com.weather.scalacass.CassFormatEncoder.sameTypeCassFormatEncoder
import com.weather.scalacass.LowPriorityCassFormatDecoderVersionSpecific.codecCassFormatDecoder
import org.joda.time.{DateTime, Instant, LocalDate, LocalTime}

object Implicits {
  implicit val timeEncoder: CassFormatEncoder[LocalTime] = sameTypeCassFormatEncoder("time")
  implicit val timeDecoder: CassFormatDecoder[LocalTime] = codecCassFormatDecoder(classOf[LocalTime])

  implicit val dateEncoder: CassFormatEncoder[LocalDate] = sameTypeCassFormatEncoder("date")
  implicit val dateDecoder: CassFormatDecoder[LocalDate] = codecCassFormatDecoder(classOf[LocalDate])

  implicit val instantEncoder: CassFormatEncoder[Instant] = sameTypeCassFormatEncoder("timestamp")
  implicit val instantDecoder: CassFormatDecoder[Instant] = codecCassFormatDecoder(classOf[Instant])

  implicit val timestampEncoder: CassFormatEncoder[DateTime] = sameTypeCassFormatEncoder("tuple<timestamp,varchar>")
  implicit val timestampDecoder: CassFormatDecoder[DateTime] = codecCassFormatDecoder(classOf[DateTime])
}
