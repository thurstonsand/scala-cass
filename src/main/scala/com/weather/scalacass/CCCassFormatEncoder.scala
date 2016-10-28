package com.weather.scalacass

import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

trait CCCassFormatEncoder[F] { self =>
  def encode(f: F): Either[Throwable, List[(String, String, AnyRef)]]
  def names: List[String]
  def types: List[String]
  def namesAndTypes: List[(String, String)] = names zip types

  final def map[G](fn: G => F): CCCassFormatEncoder[G] = new CCCassFormatEncoder[G] {
    def encode(f: G): Either[Throwable, List[(String, String, AnyRef)]] = self.encode(fn(f))
    def names = self.names
    def types = self.types
  }
  final def flatMap[G](fn: G => Either[Throwable, F]): CCCassFormatEncoder[G] = new CCCassFormatEncoder[G] {
    def encode(f: G): Either[Throwable, List[(String, String, AnyRef)]] = fn(f).right.flatMap(self.encode)
    def names = self.names
    def types = self.types
  }
}

object CCCassFormatEncoder {
  def apply[T: CCCassFormatEncoder] = implicitly[CCCassFormatEncoder[T]]

  implicit val hNilEncoder = new CCCassFormatEncoder[HNil] {
    def encode(f: HNil) = Right(Nil)
    val names = Nil
    val types = Nil
    val asQueries = Nil
  }

  implicit def hConsEncoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormatEncoder[H]], tdT: Lazy[CCCassFormatEncoder[T]]) =
    new CCCassFormatEncoder[FieldType[K, H] :: T] {
      def encode(f: FieldType[K, H] :: T) = for {
        h <- tdH.value.encode(f.head).right
        t <- tdT.value.encode(f.tail).right
      } yield {
        val name = w.value.name.toString
        (name, tdH.value.withQuery(f.head, name), h) :: t
      }
      def names = w.value.name.toString :: tdT.value.names
      def types = tdH.value.cassType :: tdT.value.types
    }

  implicit def ccConverter[T, Repr <: HList](implicit gen: LabelledGeneric.Aux[T, Repr], hListDecoder: Lazy[CCCassFormatEncoder[Repr]]) =
    new CCCassFormatEncoder[T] {
      def encode(f: T) = hListDecoder.value.encode(gen.to(f))
      def names = hListDecoder.value.names
      def types = hListDecoder.value.types
    }
}