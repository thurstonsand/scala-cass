package com.weather.scalacass

import com.datastax.driver.core.Row

object LowPriorityCassFormatDecoderVersionSpecific {
  import CassFormatDecoder.tryDecode
  def codecCassFormatDecoder[T <: AnyRef](_clazz: Class[T]) = new CassFormatDecoder[T] {
    type From = T
    val clazz = _clazz
    def f2t(f: From) = Right(f)
    def decode(r: Row, name: String) = tryDecode(r, name, (rr, nn) => rr.get(nn, _clazz))
  }
}
trait LowPriorityCassFormatDecoderVersionSpecific {
  import CassFormatDecoder.{sameTypeCassFormatDecoder, safeConvertCassFormatDecoder}
  implicit val dateFormat: CassFormatDecoder[java.util.Date] =
    sameTypeCassFormatDecoder[java.util.Date](classOf[java.util.Date], _ getTimestamp _)
  implicit val datastaxLocalDateFormat: CassFormatDecoder[com.datastax.driver.core.LocalDate] =
    sameTypeCassFormatDecoder[com.datastax.driver.core.LocalDate](classOf[com.datastax.driver.core.LocalDate], _ getDate _)
  implicit val timeFormat: CassFormatDecoder[Time] = safeConvertCassFormatDecoder[Time, java.lang.Long](classOf[java.lang.Long], Time.apply(_), (r, name) => Time(r.getTime(name)))
}