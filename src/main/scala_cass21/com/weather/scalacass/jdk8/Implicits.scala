package com.weather.scalacass.jdk8

import com.weather.scalacass.{ CassFormatDecoder, CassFormatEncoder }
import CassFormatEncoder.transCassFormatEncoder
import CassFormatDecoder.safeConvertCassFormatDecoder
import java.time.Instant

import com.datastax.driver.core.DataType
import com.google.common.reflect.TypeToken

object Implicits {
  implicit val instantEncoder: CassFormatEncoder[Instant] = transCassFormatEncoder(DataType.timestamp, java.util.Date.from)
  implicit val instantDecoder: CassFormatDecoder[Instant] = safeConvertCassFormatDecoder[Instant, java.util.Date](TypeToken.of(classOf[java.util.Date]), _.toInstant, _ getDate _, _ getDate _)
}
