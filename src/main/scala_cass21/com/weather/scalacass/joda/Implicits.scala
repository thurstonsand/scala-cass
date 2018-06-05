package com.weather.scalacass.joda

import com.weather.scalacass.{ CassFormatDecoder, CassFormatEncoder }
import CassFormatEncoder.transCassFormatEncoder
import CassFormatDecoder.safeConvertCassFormatDecoder
import com.datastax.driver.core.DataType
import com.google.common.reflect.TypeToken
import org.joda.time.Instant

object Implicits {
  implicit val instantEncoder: CassFormatEncoder[Instant] =
    transCassFormatEncoder(DataType.timestamp, i => new java.util.Date(i.getMillis))
  implicit val instantDecoder: CassFormatDecoder[Instant] =
    safeConvertCassFormatDecoder[Instant, java.util.Date](TypeToken.of(classOf[java.util.Date]), new Instant(_), _ getDate _, _ getDate _)
}
