package com.weather.scalacass

object NotRecoverable extends NotRecoverableVersionSpecific {
  def unapply(t: Throwable): Option[Throwable] = if (apply(t)) Some(t) else None

  implicit class Try2Either[T](val t: scala.util.Try[T]) extends AnyVal {
    def unwrap[S](implicit ev: T =:= Result[S]): Result[S] = t match {
      case scala.util.Success(res)                 => res
      case scala.util.Failure(NotRecoverable(exc)) => throw exc
      case scala.util.Failure(exc)                 => Left(exc)
    }
    def toEither: Result[T] = t match {
      case scala.util.Success(res)                 => Right(res)
      case scala.util.Failure(NotRecoverable(exc)) => throw exc
      case scala.util.Failure(exc)                 => Left(exc)
    }
  }
}
