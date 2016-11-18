package com.weather.scalacass

import com.datastax.driver.core.Row
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

abstract class DerivedCCCassFormatDecoder[T] extends CCCassFormatDecoder[T]

object DerivedCCCassFormatDecoder {
  implicit val hNilDecoder = new DerivedCCCassFormatDecoder[HNil] {
    def decode(r: Row) = Right(HNil)
  }

  implicit def hConsDecoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormatDecoder[H]], tdT: Lazy[DerivedCCCassFormatDecoder[T]]) =
    new DerivedCCCassFormatDecoder[FieldType[K, H] :: T] {
      def decode(r: Row) = for {
        h <- tdH.value.decode(r, w.value.name.toString).right
        t <- tdT.value.decode(r).right
      } yield field[K](h) :: t
    }

  implicit def ccConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], hListDecoder: Lazy[DerivedCCCassFormatDecoder[Repr]]): DerivedCCCassFormatDecoder[T] =
    new DerivedCCCassFormatDecoder[T] {
      def decode(r: Row): Result[T] = hListDecoder.value.decode(r).right.map(gen.from)
    }
}

trait CCCassFormatDecoder[T] { self =>
  private[scalacass] def decode(r: Row): Result[T]
  final def map[U](f: T => U): CCCassFormatDecoder[U] = new CCCassFormatDecoder[U] {
    def decode(r: Row): Result[U] = self.decode(r).right.map(f)
  }
  final def flatMap[U](f: T => Result[U]): CCCassFormatDecoder[U] = new CCCassFormatDecoder[U] {
    def decode(r: Row): Result[U] = self.decode(r).right.flatMap(f)
  }

  final def as(r: Row): T = decode(r) match {
    case Right(v)  => v
    case Left(exc) => throw exc
  }
  final def getAs(r: Row): Option[T] = decode(r).right.toOption
  final def getOrElse(r: Row)(default: => T): T = decode(r).right.getOrElse(default)
  final def attemptAs(r: Row): Result[T] = decode(r)
}

object CCCassFormatDecoder {
  implicit def derive[T](implicit derived: Lazy[DerivedCCCassFormatDecoder[T]]): CCCassFormatDecoder[T] = derived.value
  def apply[T](implicit decoder: CCCassFormatDecoder[T]) = decoder
}