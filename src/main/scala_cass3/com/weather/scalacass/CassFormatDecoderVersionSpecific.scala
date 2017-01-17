package com.weather.scalacass

import com.datastax.driver.core.{Row, TupleValue}
import com.google.common.reflect.TypeToken

object CassFormatDecoderVersionSpecific extends CassFormatDecoderVersionSpecific {
  def codecCassFormatDecoder[T <: AnyRef](_typeToken: TypeToken[T]) = new CassFormatDecoder[T] {
    type From = T
    val typeToken = _typeToken
    def f2t(f: From) = Right(f)
    def extract(r: Row, name: String) = r get (name, typeToken)
    def tupleExtract(tup: TupleValue, pos: Int) = tup get (pos, typeToken)
  }
}
trait CassFormatDecoderVersionSpecific extends LowPriorityCassFormatDecoder {
  import CassFormatDecoder.{sameTypeCassFormatDecoder, safeConvertCassFormatDecoder}
  implicit val dateFormat: CassFormatDecoder[java.util.Date] =
    sameTypeCassFormatDecoder[java.util.Date](TypeToken.of(classOf[java.util.Date]), _ getTimestamp _, _ getTimestamp _)
  implicit val datastaxLocalDateFormat: CassFormatDecoder[com.datastax.driver.core.LocalDate] =
    sameTypeCassFormatDecoder[com.datastax.driver.core.LocalDate](TypeToken.of(classOf[com.datastax.driver.core.LocalDate]), _ getDate _, _ getDate _)
  implicit val timeFormat: CassFormatDecoder[Time] = safeConvertCassFormatDecoder[Time, java.lang.Long](TypeToken.of(classOf[java.lang.Long]), Time(_), _ getTime _, _ getTime _)
}