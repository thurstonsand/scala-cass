package com.weather.scalacass

import com.datastax.driver.core.TupleValue
import com.datastax.driver.core.exceptions.InvalidTypeException
import shapeless.{::, Generic, HList, HNil}

trait TupleCassFormatDecoder[T] {
  def decode(tup: TupleValue, n: Int): Either[Throwable, T]
}

object TupleCassFormatDecoder {
  def apply[T: TupleCassFormatDecoder] = implicitly[TupleCassFormatDecoder[T]]

  implicit val hNilDecoder = new TupleCassFormatDecoder[HNil] {
    def decode(tup: TupleValue, n: Int) = {
      val arity = tup.getType.getComponentTypes.size
      if (arity != n) Left(new InvalidTypeException(s"tuple of wrong arity: expecting arity of $n but found $arity"))
      else Right(HNil)
    }
  }

  implicit def hConsDecoder[H, T <: HList](implicit tdH: CassFormatDecoder[H], tdT: TupleCassFormatDecoder[T]) =
    new TupleCassFormatDecoder[H :: T] {

      def decode(tup: TupleValue, n: Int) = for {
        h <- tdH.tupleDecode(tup, n).right
        t <- tdT.decode(tup, n + 1).right
      } yield h :: t
    }

  implicit def tupleDecoder[T <: Product, Repr <: HList](implicit gen: Generic.Aux[T, Repr], hListDecoder: TupleCassFormatDecoder[Repr]): TupleCassFormatDecoder[T] =
    new TupleCassFormatDecoder[T] {
      def decode(tup: TupleValue, n: Int): Either[Throwable, T] = {
        hListDecoder.decode(tup, n).right.map(gen.from)
      }
    }
}