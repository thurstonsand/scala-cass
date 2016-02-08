package com.weather.scalacass

import com.datastax.driver.core.Row
import shapeless._, labelled._
import TypeClasses.RowDecoder

object ShapelessTypeClasses {
  trait CCRowDecoder[T] {
    def decode(r: Row): T
  }
  object RowDecoder {
    def apply[T](implicit f: Lazy[CCRowDecoder[T]]) = f.value
  }
  def ccRowDecoder[T](_decode: Row => T) = new CCRowDecoder[T] {
    def decode(r: Row) = _decode(r)
  }

  implicit val HNilDecoder = ccRowDecoder[HNil](_ => HNil)

  implicit def HConsDecoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[RowDecoder[H]], tdT: Lazy[CCRowDecoder[T]]) =
    ccRowDecoder[FieldType[K, H] :: T](r => field[K](tdH.value.decode(r, w.value.name.toString)) :: tdT.value.decode(r))

  implicit def HListConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], sg: Lazy[CCRowDecoder[Repr]]) =
    ccRowDecoder[T](r => gen.from(sg.value.decode(r)))
}