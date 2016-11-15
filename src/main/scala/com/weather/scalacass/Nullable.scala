package com.weather.scalacass

sealed trait Nullable[+A] {
  def isNull: Boolean
  def toOption: Option[A]
}
final case class NotNull[+A](x: A) extends Nullable[A] {
  def isNull = false
  def toOption: Option[A] = Some(x)
}
case object IsNull extends Nullable[Nothing] {
  def isNull = true
  def toOption: Option[Nothing] = None
}

object Nullable {
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Null"))
  def apply[A](x: A): Nullable[A] = if (x == null) IsNull else NotNull(x)
  def empty[A]: Nullable[A] = IsNull
  implicit def nullable2iterable[A](xo: Nullable[A]): Iterable[A] = xo.toOption.toList

  implicit class NullableOption[+A](val opt: Option[A]) extends AnyVal {
    def toNullable: Nullable[A] = opt.fold[Nullable[A]](IsNull)(NotNull.apply)
  }
  implicit def option2nullable[A](opt: Option[A]): Nullable[A] = opt.toNullable
  implicit def nullable2option[A](nullable: Nullable[A]): Option[A] = nullable.toOption
}
