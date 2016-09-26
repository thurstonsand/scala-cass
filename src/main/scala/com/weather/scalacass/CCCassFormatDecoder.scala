package com.weather.scalacass

import com.datastax.driver.core.Row
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

trait CCCassFormatDecoder[T] { self =>
  private[scalacass] def decode(r: Row): Either[Throwable, T]
  final def map[U](f: T => U): CCCassFormatDecoder[U] = new CCCassFormatDecoder[U] {
    def decode(r: Row): Either[Throwable, U] = self.decode(r).right.map(f)
  }
  final def flatMap[U](f: T => Either[Throwable, U]): CCCassFormatDecoder[U] = new CCCassFormatDecoder[U] {
    def decode(r: Row): Either[Throwable, U] = self.decode(r).right.flatMap(f)
  }

  final def as(r: Row): T = decode(r) match {
    case Right(v)  => v
    case Left(exc) => throw exc
  }
  final def getAs(r: Row): Option[T] = decode(r).right.toOption
  final def getOrElse(r: Row)(default: T): T = decode(r).right.getOrElse(default)
  final def attemptAs(r: Row): Either[Throwable, T] = decode(r)
}

object CCCassFormatDecoder {
  def apply[T: CCCassFormatDecoder] = implicitly[CCCassFormatDecoder[T]]

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
}