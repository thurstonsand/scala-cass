package com.weather.scalacass

import com.datastax.driver.core.{DataType, TupleType, TupleValue}

trait LowPriorityCassFormatEncoderVersionSpecific {
  implicit def tupleFormat[TUP <: Product](implicit underlying: TupleCassFormatEncoder[TUP]): CassFormatEncoder[TUP] = new CassFormatEncoder[TUP] {
    type To = TupleValue
    val cassDataType = TupleType.of(underlying.dataTypes: _*)
    def encode(f: TUP): Either[Throwable, To] = underlying.encode(f).right.map(ar => cassDataType.newValue(ar: _*))
  }
}

trait CassFormatEncoderVersionSpecific extends LowPriorityCassFormatEncoderVersionSpecific {
  import CassFormatEncoder.sameTypeCassFormatEncoder

  implicit val dateFormat: CassFormatEncoder[java.util.Date] = sameTypeCassFormatEncoder(DataType.timestamp)
}

object CassFormatEncoderVersionSpecific extends CassFormatEncoderVersionSpecific