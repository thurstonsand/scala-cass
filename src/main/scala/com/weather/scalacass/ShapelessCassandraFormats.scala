package com.weather.scalacass

import com.datastax.driver.core.Row
import shapeless._, labelled._
import com.weather.scalacass.CassandraFormats.CassFormat

object ShapelessCassandraFormats {
  trait CCCassFormat[T] {
    def decode(r: Row): Either[Throwable, T]
  }
  def ccCassFormat[T](_decode: Row => Either[Throwable, T]) = new CCCassFormat[T] {
    def decode(r: Row) = _decode(r)
  }

  implicit val hNilDecoder = ccCassFormat[HNil](_ => Right(HNil))

  implicit def hConsDecoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormat[H]], tdT: Lazy[CCCassFormat[T]]) =
    ccCassFormat[FieldType[K, H] :: T](r => for {
      h <- tdH.value.decode(r, w.value.name.toString).right
      t <- tdT.value.decode(r).right
    } yield field[K](h) :: t)

  implicit def hListConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], sg: Lazy[CCCassFormat[Repr]]) =
    ccCassFormat[T](r => sg.value.decode(r).right.map(gen.from))
}