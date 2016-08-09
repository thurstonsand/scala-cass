package com.weather.scalacass

trait LowPriorityCassFormatDecoderVersionSpecific {
  import CassFormatDecoder.sameTypeCassFormat

  implicit val dateFormat: CassFormatDecoder[java.util.Date] =
    sameTypeCassFormat[java.util.Date](classOf[java.util.Date], _ getDate _)
}
