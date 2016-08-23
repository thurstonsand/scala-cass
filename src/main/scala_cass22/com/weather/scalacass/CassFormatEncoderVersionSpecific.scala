package com.weather.scalacass

trait CassFormatEncoderVersionSpecific {
  import CassFormatEncoder.sameTypeCassFormatEncoder

  implicit val dateFormat: CassFormatEncoder[java.util.Date] = sameTypeCassFormatEncoder("timestamp")
}

object CassFormatEncoderVersionSpecific extends CassFormatEncoderVersionSpecific