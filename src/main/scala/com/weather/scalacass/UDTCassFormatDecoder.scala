package com.weather.scalacass

import com.datastax.driver.core.UDTValue
import shapeless.labelled.{ FieldType, field }
import shapeless.{ ::, HList, HNil, LabelledGeneric, Lazy, Witness }

abstract class DerivedUDTCassFormatDecoder[T] extends UDTCassFormatDecoder[T]

object DerivedUDTCassFormatDecoder {
  implicit val hNilDecoder: DerivedUDTCassFormatDecoder[HNil] = new DerivedUDTCassFormatDecoder[HNil] {
    def decode(udt: UDTValue): Result[HNil] = Right(HNil)
  }
  implicit def hConsDecoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormatDecoder[H]], tdT: Lazy[DerivedUDTCassFormatDecoder[T]]): DerivedUDTCassFormatDecoder[FieldType[K, H] :: T] =
    new DerivedUDTCassFormatDecoder[FieldType[K, H] :: T] {
      def decode(udt: UDTValue) = for {
        h <- tdH.value.udtDecode(udt, w.value.name.toString).right
        t <- tdT.value.decode(udt).right
      } yield field[K](h) :: t
    }

  implicit def ccConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], hListDecoder: Lazy[DerivedUDTCassFormatDecoder[Repr]]): DerivedUDTCassFormatDecoder[T] =
    new DerivedUDTCassFormatDecoder[T] {
      def decode(udt: UDTValue): Result[T] = hListDecoder.value.decode(udt).right.map(gen.from)
    }
}

trait UDTCassFormatDecoder[T] {
  def decode(udt: UDTValue): Result[T]
}

object UDTCassFormatDecoder {
  implicit def derive[T](implicit derived: Lazy[DerivedUDTCassFormatDecoder[T]]): UDTCassFormatDecoder[T] = derived.value
  def apply[T](implicit decoder: DerivedUDTCassFormatDecoder[T]) = decoder
}
