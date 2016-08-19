package com.weather.scalacass

trait LowPriorityCassFormatDecoderVersionSpecific {
  import CassFormatDecoder.sameTypeCassFormatDecoder

  implicit val dateFormat: CassFormatDecoder[java.util.Date] =
    sameTypeCassFormatDecoder[java.util.Date](classOf[java.util.Date], _ getDate _)
}

object LowPriorityCassFormatDecoderVersionSpecific extends LowPriorityCassFormatDecoderVersionSpecific