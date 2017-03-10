package com.weather.scalacass

import com.datastax.driver.core.TupleValue
import com.datastax.driver.core.exceptions.InvalidTypeException
import shapeless.{::, Generic, HList, HNil, IsTuple, Lazy}

abstract class DerivedTupleCassFormatDecoder[T] extends TupleCassFormatDecoder[T]

object DerivedTupleCassFormatDecoder {
  implicit val hNilDecoder: DerivedTupleCassFormatDecoder[HNil] = new DerivedTupleCassFormatDecoder[HNil] {
    def decode(tup: TupleValue, n: Int) = {
      val arity = tup.getType.getComponentTypes.size
      if (arity !== n) Left(new InvalidTypeException(s"tuple of wrong arity: expecting arity of $n but found $arity"))
      else Right(HNil)
    }
  }

  implicit def hConsDecoder[H, T <: HList](implicit tdH: CassFormatDecoder[H], tdT: DerivedTupleCassFormatDecoder[T]): DerivedTupleCassFormatDecoder[::[H, T]] =
    new DerivedTupleCassFormatDecoder[H :: T] {

      def decode(tup: TupleValue, n: Int) = for {
        h <- tdH.tupleDecode(tup, n).right
        t <- tdT.decode(tup, n + 1).right
      } yield h :: t
    }

  implicit def tupleDecoder[T <: Product: IsTuple, Repr <: HList](implicit gen: Generic.Aux[T, Repr], hListDecoder: DerivedTupleCassFormatDecoder[Repr]): DerivedTupleCassFormatDecoder[T] =
    new DerivedTupleCassFormatDecoder[T] {
      def decode(tup: TupleValue, n: Int): Result[T] = {
        hListDecoder.decode(tup, n).right.map(gen.from)
      }
    }
}

trait TupleCassFormatDecoder[T] {
  def decode(tup: TupleValue, n: Int): Result[T]
}

object TupleCassFormatDecoder {
  implicit def derive[T](implicit derived: Lazy[DerivedTupleCassFormatDecoder[T]]): TupleCassFormatDecoder[T] = derived.value
  def apply[T](implicit decoder: TupleCassFormatDecoder[T]) = decoder
}