package com.weather.scalacass

import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

trait CCCassFormatEncoder[F] { self =>
  def encodeWithName(f: F): Either[Throwable, List[(String, AnyRef)]]
  def encodeWithQuery(f: F): Either[Throwable, List[(String, AnyRef)]]
  def names: List[String]
  def types: List[String]
  def namesAndTypes: List[(String, String)] = names zip types

  final def map[G](fn: G => F): CCCassFormatEncoder[G] = new CCCassFormatEncoder[G] {
    def encodeWithName(f: G): Either[Throwable, List[(String, AnyRef)]] = self.encodeWithName(fn(f))
    def encodeWithQuery(f:G): Either[Throwable, List[(String, AnyRef)]] = self.encodeWithQuery(fn(f))
    def names = self.names
    def types = self.types
  }
  final def flatMap[G](fn: G => Either[Throwable, F]): CCCassFormatEncoder[G] = new CCCassFormatEncoder[G] {
    def encodeWithName(f: G): Either[Throwable, List[(String, AnyRef)]] = fn(f).right.flatMap(self.encodeWithName)
    def encodeWithQuery(f: G): Either[Throwable, List[(String, AnyRef)]] = fn(f).right.flatMap(self.encodeWithQuery)
    def names = self.names
    def types = self.types
  }
}

object CCCassFormatEncoder {
  def apply[T](implicit encoder: Lazy[CCCassFormatEncoder[T]]) = encoder.value

  implicit val hNilEncoder = new CCCassFormatEncoder[HNil] {
    def encodeWithName(f: HNil) = Right(Nil)
    def encodeWithQuery(f: HNil) = Right(Nil)

    val names = Nil
    val types = Nil
  }

  implicit def hConsEncoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormatEncoder[H]], tdT: Lazy[CCCassFormatEncoder[T]]) =
    new CCCassFormatEncoder[FieldType[K, H] :: T] {
      def encodeWithName(f: FieldType[K, H] :: T) = for {
        h <- tdH.value.encode(f.head).right
        t <- tdT.value.encodeWithName(f.tail).right
      } yield (w.value.name.toString, h) :: t
      def encodeWithQuery(f: FieldType[K, H] :: T) = for {
        h <- tdH.value.encode(f.head).right
        t <- tdT.value.encodeWithQuery(f.tail).right
      } yield (tdH.value.withQuery(f.head, w.value.name.toString), h) :: t
      def names = w.value.name.toString :: tdT.value.names
      def types = tdH.value.cassType :: tdT.value.types
    }

  implicit def ccConverter[T, Repr <: HList](implicit gen: LabelledGeneric.Aux[T, Repr], hListDecoder: Lazy[CCCassFormatEncoder[Repr]]): CCCassFormatEncoder[T] =
    new CCCassFormatEncoder[T] {
      def encodeWithName(f: T) = hListDecoder.value.encodeWithName(gen.to(f))
      def encodeWithQuery(f: T) = hListDecoder.value.encodeWithQuery(gen.to(f))
      def names = hListDecoder.value.names
      def types = hListDecoder.value.types
    }
}