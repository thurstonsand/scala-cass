package com.weather.scalacass

import com.datastax.driver.core.Row
import shapeless._, labelled._
import TypeClasses.RowDecoder

object ShapelessTypeClasses {
  trait CCRowDecoder[T] {
    def decode(r: Row): Either[Throwable, T]
  }
  def ccRowDecoder[T](_decode: Row => Either[Throwable, T]) = new CCRowDecoder[T] {
    def decode(r: Row) = _decode(r)
  }

  object RowDecoder {
    def apply[T](implicit f: Lazy[CCRowDecoder[T]]) = f.value
  }

  implicit val hNilDecoder = ccRowDecoder[HNil](_ => Right(HNil))

  implicit def hConsDecoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[RowDecoder[H]], tdT: Lazy[CCRowDecoder[T]]) =
    ccRowDecoder[FieldType[K, H] :: T](r => for {
      h <- tdH.value.decode(r, w.value.name.toString).right
      t <- tdT.value.decode(r).right
    } yield field[K](h) :: t)

  implicit def hListConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], sg: Lazy[CCRowDecoder[Repr]]) =
    ccRowDecoder[T](r => sg.value.decode(r).right.map(gen.from))
}