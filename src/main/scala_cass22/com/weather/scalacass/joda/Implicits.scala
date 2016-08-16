package com.weather.scalacass.joda

import com.weather.scalacass.{
  CassFormatDecoder,
  CassFormatEncoder,
  LowPriorityCassFormatDecoderVersionSpecific,
  LowPriorityCassFormatEncoderVersionSpecific
}
import org.joda.time.Instant

object Implicits {
  implicit val instantEncoder: CassFormatEncoder[Instant] =
    LowPriorityCassFormatEncoderVersionSpecific.dateFormat.map(i => new java.util.Date(i.getMillis))
  implicit val instantDecoder: CassFormatDecoder[Instant] =
    LowPriorityCassFormatDecoderVersionSpecific.dateFormat.map(d => new Instant(d))
}
