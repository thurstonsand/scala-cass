package com.weather.scalacass

import com.datastax.driver.core.{ DataType, TupleType, TupleValue }

trait LowPriorityCassFormatEncoderVersionSpecific {
  implicit def tupleFormat[TUP <: Product](implicit underlying: TupleCassFormatEncoder[TUP]): CassFormatEncoder[TUP] = new CassFormatEncoder[TUP] {
    type From = TupleValue
    val cassDataType = TupleType.of(underlying.dataTypes: _*)
    def encode(f: TUP): Result[From] = underlying.encode(f).map(ar => cassDataType.newValue(ar: _*))
  }
}

trait CassFormatEncoderVersionSpecific extends LowPriorityCassFormatEncoderVersionSpecific {
  import CassFormatEncoder.sameTypeCassFormatEncoder

  implicit val dateFormat: CassFormatEncoder[java.util.Date] = sameTypeCassFormatEncoder(DataType.timestamp)
}

object CassFormatEncoderVersionSpecific extends CassFormatEncoderVersionSpecific
