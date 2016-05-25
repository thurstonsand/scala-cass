package com.weather.scalacass

import com.datastax.driver.core.Row
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}
import CassFormatDecoder._

trait CCCassFormatDecoder[T] {
  def decode(r: Row): Either[Throwable, T]
}

object CCCassFormatDecoder {
  implicit val hNilDecoder = new CCCassFormatDecoder[HNil] {
    def decode(r: Row) = Right(HNil)
  }

  implicit def hConsDecoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormatDecoder[H]], tdT: Lazy[CCCassFormatDecoder[T]]) =
    new CCCassFormatDecoder[FieldType[K, H] :: T] {
      def decode(r: Row) = for {
        h <- tdH.value.decode(r, w.value.name.toString).right
        t <- tdT.value.decode(r).right
      } yield field[K](h) :: t
    }

  implicit def ccConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], hListDecoder: Lazy[CCCassFormatDecoder[Repr]]): CCCassFormatDecoder[T] =
    new CCCassFormatDecoder[T] {
      def decode(r: Row): Either[Throwable, T] = hListDecoder.value.decode(r).right.map(gen.from)
    }

  def apply[T: CCCassFormatDecoder] = implicitly[CCCassFormatDecoder[T]]
}