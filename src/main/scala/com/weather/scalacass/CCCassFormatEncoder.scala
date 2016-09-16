package com.weather.scalacass

import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

trait CCCassFormatEncoder[F] { self =>
  def encode(f: F): Either[Throwable, List[(String, AnyRef)]]
  def namesAndTypes: List[(String, String)]

  final def map[G](fn: G => F): CCCassFormatEncoder[G] = new CCCassFormatEncoder[G] {
    def encode(f: G): Either[Throwable, List[(String, AnyRef)]] = self.encode(fn(f))
    def namesAndTypes: List[(String, String)] = self.namesAndTypes
  }
  final def flatMap[G](fn: G => Either[Throwable, F]): CCCassFormatEncoder[G] = new CCCassFormatEncoder[G] {
    def encode(f: G): Either[Throwable, List[(String, AnyRef)]] = fn(f).right.flatMap(self.encode)
    def namesAndTypes: List[(String, String)] = self.namesAndTypes
  }
}

object CCCassFormatEncoder {
  def apply[T: CCCassFormatEncoder] = implicitly[CCCassFormatEncoder[T]]

  implicit val hNilEncoder = new CCCassFormatEncoder[HNil] {
    def encode(f: HNil) = Right(Nil)
    val namesAndTypes = Nil
  }

  implicit def hConsEncoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormatEncoder[H]], tdT: Lazy[CCCassFormatEncoder[T]]) =
    new CCCassFormatEncoder[FieldType[K, H] :: T] {
      def encode(f: FieldType[K, H] :: T) = for {
        h <- tdH.value.encode(f.head).right
        t <- tdT.value.encode(f.tail).right
      } yield (w.value.name.toString, h) :: t
      def namesAndTypes = (w.value.name.toString, tdH.value.cassType) :: tdT.value.namesAndTypes
    }

  implicit def ccConverter[T, Repr <: HList](implicit gen: LabelledGeneric.Aux[T, Repr], hListDecoder: Lazy[CCCassFormatEncoder[Repr]]) =
    new CCCassFormatEncoder[T] {
      def encode(f: T) = hListDecoder.value.encode(gen.to(f))
      def namesAndTypes = hListDecoder.value.namesAndTypes
    }
}