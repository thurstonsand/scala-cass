package com.weather.scalacass

import com.datastax.driver.core.DataType
import shapeless.{ ::, Generic, HList, HNil, IsTuple, Lazy }

abstract class DerivedTupleCassFormatEncoder[T] extends TupleCassFormatEncoder[T]

object DerivedTupleCassFormatEncoder {
  implicit val hNilEncoder: DerivedTupleCassFormatEncoder[HNil] = new DerivedTupleCassFormatEncoder[HNil] {
    def encode(tup: HNil) = Right(Nil)
    def types = Nil
    def dataTypes = Nil
  }

  implicit def hConsEncoder[H, T <: HList](implicit tdH: CassFormatEncoder[H], tdT: DerivedTupleCassFormatEncoder[T]): DerivedTupleCassFormatEncoder[H :: T] =
    new DerivedTupleCassFormatEncoder[H :: T] {
      def encode(tup: H :: T): Result[List[AnyRef]] = for {
        h <- tdH.encode(tup.head).right
        t <- tdT.encode(tup.tail).right
      } yield h :: t
      def types = tdH.cassType :: tdT.types
      def dataTypes = tdH.cassDataType :: tdT.dataTypes
    }

  implicit def tupleEncoder[T <: Product : IsTuple, Repr <: HList](implicit gen: Generic.Aux[T, Repr], hListEncoder: DerivedTupleCassFormatEncoder[Repr]): DerivedTupleCassFormatEncoder[T] =
    new DerivedTupleCassFormatEncoder[T] {
      def encode(tup: T): Result[List[AnyRef]] = hListEncoder.encode(gen.to(tup))
      def types = hListEncoder.types
      def dataTypes = hListEncoder.dataTypes
    }
}

trait TupleCassFormatEncoder[T] {
  def encode(tup: T): Result[List[AnyRef]]
  def types: List[String]
  def dataTypes: List[DataType]
}

object TupleCassFormatEncoder {
  implicit def derive[T](implicit derived: Lazy[DerivedTupleCassFormatEncoder[T]]): TupleCassFormatEncoder[T] = derived.value
  def apply[T](implicit encoder: TupleCassFormatEncoder[T]) = encoder
}
