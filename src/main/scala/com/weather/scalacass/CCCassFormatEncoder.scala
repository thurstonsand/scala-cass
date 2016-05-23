package com.weather.scalacass

import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

trait CCCassFormatEncoder[F] {
  def encode(f: F): List[(String, AnyRef)]
  def namesAndTypes: List[(String, String)]
}

object CCCassFormatEncoder {
  implicit val hNilEncoder = new CCCassFormatEncoder[HNil] {
    def encode(f: HNil) = Nil
    val namesAndTypes = Nil
  }

  implicit def hConsEncoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormatEncoder[H]], tdT: Lazy[CCCassFormatEncoder[T]]) =
    new CCCassFormatEncoder[FieldType[K, H] :: T] {
      def encode(f: FieldType[K, H] :: T) = (w.value.name.toString, tdH.value.encode(f.head)) :: tdT.value.encode(f.tail)
      def namesAndTypes = (w.value.name.toString, tdH.value.cassType) :: tdT.value.namesAndTypes
    }

  implicit def ccConverter[T, Repr <: HList](implicit gen: LabelledGeneric.Aux[T, Repr], hListDecoder: Lazy[CCCassFormatEncoder[Repr]]) =
    new CCCassFormatEncoder[T] {
      def encode(f: T) = hListDecoder.value.encode(gen.to(f))
      def namesAndTypes = hListDecoder.value.namesAndTypes
    }
}