package com.weather.scalacass

import com.datastax.driver.core.DataType
import shapeless.{::, Generic, HList, HNil}

trait TupleCassFormatEncoder[T] {
  def encode(tup: T): Either[Throwable, List[AnyRef]]
  def types: List[String]
  def dataTypes: List[DataType]
}

object TupleCassFormatEncoder {
  def apply[T: TupleCassFormatEncoder] = implicitly[TupleCassFormatEncoder[T]]

  implicit val hNilEncoder: TupleCassFormatEncoder[HNil] = new TupleCassFormatEncoder[HNil] {
    def encode(tup: HNil) = Right(Nil)
    def types = Nil
    def dataTypes = Nil
  }

  implicit def hConsEncoder[H, T <: HList](implicit tdH: CassFormatEncoder[H], tdT: TupleCassFormatEncoder[T]): TupleCassFormatEncoder[H :: T] =
    new TupleCassFormatEncoder[H :: T] {
      def encode(tup: H :: T): Either[Throwable, List[AnyRef]] = for {
        h <- tdH.encode(tup.head).right
        t <- tdT.encode(tup.tail).right
      } yield h :: t
      def types = tdH.cassType :: tdT.types
      def dataTypes = tdH.cassDataType :: tdT.dataTypes
    }

  implicit def tupleEncoder[T <: Product, Repr <: HList](implicit gen: Generic.Aux[T, Repr], hListEncoder: TupleCassFormatEncoder[Repr]): TupleCassFormatEncoder[T] =
    new TupleCassFormatEncoder[T] {
      override def encode(tup: T): Either[Throwable, List[AnyRef]] = hListEncoder.encode(gen.to(tup))
      def types = hListEncoder.types
      def dataTypes = hListEncoder.dataTypes
    }
}