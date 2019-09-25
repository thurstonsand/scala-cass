package com.weather.scalacass

import com.datastax.driver.core.{DataType, Row, TupleValue}
import com.google.common.reflect.TypeToken

sealed trait Comparable[+A] {
  def than: A
}
final case class Equal[+A](than: A) extends Comparable[A]
final case class Less[+A](than: A) extends Comparable[A]
final case class LessEqual[+A](than: A) extends Comparable[A]
final case class Greater[+A](than: A) extends Comparable[A]
final case class GreaterEqual[+A](than: A) extends Comparable[A]

object Comparable {

  implicit def comparableToA[A](l: Comparable[A]): A = l.than

  implicit def encoder[A](
    implicit
    underlying: CassFormatEncoder[A]
  ): CassFormatEncoder[Comparable[A]] = new CassFormatEncoder[Comparable[A]] {
    type From = Comparable[underlying.From]

    def cassDataType: DataType = underlying.cassDataType

    def encode(f: Comparable[A]): Result[Comparable[underlying.From]] =
      f match {
        case Equal(x) =>
          underlying.encode(x).right.map(Less.apply)
        case Less(x) =>
          underlying.encode(x).right.map(Less.apply)
        case LessEqual(x) =>
          underlying.encode(x).right.map(LessEqual.apply)
        case Greater(x) =>
          underlying.encode(x).right.map(Greater.apply)
        case GreaterEqual(x) =>
          underlying.encode(x).right.map(GreaterEqual.apply)
      }

    override def withQuery(instance: Comparable[A], name: String): String =
      instance match {
        case _: Equal[A]        => s"$name=?"
        case _: Less[A]         => s"$name<?"
        case _: LessEqual[A]    => s"$name<=?"
        case _: Greater[A]      => s"$name>?"
        case _: GreaterEqual[A] => s"$name>=?"
      }
  }

  implicit def decoder[A](
    implicit
    underlying: CassFormatDecoder[A]
  ): CassFormatDecoder[Comparable[A]] = new CassFormatDecoder[Comparable[A]] {
    type From = underlying.From
    val typeToken: TypeToken[underlying.From] = underlying.typeToken
    def f2t(f: From): Result[Comparable[A]] =
      underlying.f2t(f).right.map(Equal.apply)
    def extract(r: Row, name: String): From = underlying.extract(r, name)

    def tupleExtract(tup: TupleValue, pos: Int): From =
      underlying.tupleExtract(tup, pos)
  }
}
