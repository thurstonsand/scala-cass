package com.weather.scalacass

import com.google.common.reflect.TypeToken

object CassFormatDecoderVersionSpecific extends CassFormatDecoderVersionSpecific

trait CassFormatDecoderVersionSpecific extends LowPriorityCassFormatDecoder {
  import CassFormatDecoder.sameTypeCassFormatDecoder

  implicit val dateFormat: CassFormatDecoder[java.util.Date] =
    sameTypeCassFormatDecoder(TypeToken.of(classOf[java.util.Date]), _ getDate _, _ getDate _)
}
