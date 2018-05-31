package com.weather.scalacass.joda

import com.weather.scalacass.{ CassFormatDecoder, CassFormatEncoder, CassFormatDecoderVersionSpecific, CassFormatEncoderVersionSpecific }
import org.joda.time.Instant

object Implicits {
  implicit val instantEncoder: CassFormatEncoder[Instant] =
    CassFormatEncoderVersionSpecific.dateFormat.map(i => new java.util.Date(i.getMillis))
  implicit val instantDecoder: CassFormatDecoder[Instant] =
    CassFormatDecoderVersionSpecific.dateFormat.map(new Instant(_))
}
