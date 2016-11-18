package com.weather.scalacass

import com.datastax.driver.core.{DataType, Row, TupleValue}

sealed trait Nullable[+A] {
  def toOption: Option[A]
}
final case class Is[+A](x: A) extends Nullable[A] {
  def toOption: Option[A] = Some(x)
}
case object IsNotNull extends Nullable[Nothing] {
  def toOption: Option[Nothing] = None
}
case object IsNull extends Nullable[Nothing] {
  def toOption: Option[Nothing] = None
}

object Nullable {
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Null"))
  def apply[A](x: A): Nullable[A] = if (x == null) IsNull else Is(x)
  def empty[A]: Nullable[A] = IsNull
  implicit def nullable2iterable[A](xo: Nullable[A]): Iterable[A] = xo.toOption.toList

  implicit class NullableOption[+A](val opt: Option[A]) extends AnyVal {
    def toNullable: Nullable[A] = opt.fold[Nullable[A]](IsNull)(Is.apply)
  }
  implicit def option2nullable[A](opt: Option[A]): Nullable[A] = opt.toNullable
  implicit def nullable2option[A](nullable: Nullable[A]): Option[A] = nullable.toOption

  implicit def encoder[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[Nullable[A]] = new CassFormatEncoder[Nullable[A]] {
    type To = Nullable[underlying.To]

    def cassDataType: DataType = underlying.cassDataType

    def encode(f: Nullable[A]): Result[Nullable[underlying.To]] = f match {
      case Is(x)     => underlying.encode(x).right.map(Is.apply)
      case IsNotNull => Right(IsNotNull)
      case IsNull    => Right(IsNull)
    }

    override def withQuery(instance: Nullable[A], name: String): String = instance match {
      case v: Is[A]  => super.withQuery(v, name)
      case IsNotNull => s"$name!=NULL"
      case IsNull    => s"$name=NULL"
    }
  }

  implicit def decoder[A](implicit underlying: CassFormatDecoder[A]): CassFormatDecoder[Nullable[A]] = new CassFormatDecoder[Nullable[A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From): Result[Nullable[A]] = underlying.f2t(f).right.map(Is.apply)
    def extract(r: Row, name: String): From = underlying.extract(r, name)

    override def decode(r: Row, name: String): Result[Nullable[A]] = super.decode(r, name) match {
      case Left(_: ValueNotDefinedException) => Right(IsNull)
      case other                             => other
    }
    def tupleExtract(tup: TupleValue, pos: Int): From = underlying.tupleExtract(tup, pos)

    override def tupleDecode(tup: TupleValue, pos: Int): Result[Nullable[A]] = super.tupleDecode(tup, pos) match {
      case Left(_: ValueNotDefinedException) => Right(IsNull)
      case other                             => other
    }
  }
}
