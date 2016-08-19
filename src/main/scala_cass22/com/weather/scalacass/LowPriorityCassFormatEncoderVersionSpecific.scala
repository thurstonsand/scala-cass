package com.weather.scalacass

trait LowPriorityCassFormatEncoderVersionSpecific {
  import CassFormatEncoder.sameTypeCassFormatEncoder

  implicit val dateFormat: CassFormatEncoder[java.util.Date] = sameTypeCassFormatEncoder("timestamp")
}

object LowPriorityCassFormatEncoderVersionSpecific extends LowPriorityCassFormatEncoderVersionSpecific