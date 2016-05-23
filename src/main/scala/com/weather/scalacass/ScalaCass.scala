package com.weather.scalacass

import com.datastax.driver.core.Row

object ScalaCass {
  private implicit class RichEither[+A <: Throwable, +B](val e: Either[A, B]) extends AnyVal {
    def getOrThrow = e match {
      case Right(v)  => v
      case Left(exc) => throw exc
    }
    def getRightOpt = e.right.toOption
  }

  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T: CassFormatDecoder](name: String): T = implicitly[CassFormatDecoder[T]].decode(r, name).getOrThrow
    def getAs[T: CassFormatDecoder](name: String): Option[T] = implicitly[CassFormatDecoder[T]].decode(r, name).getRightOpt
    def getOrElse[T: CassFormatDecoder](name: String, default: => T): T = getAs[T](name).getOrElse(default)

    def as[T: CCCassFormatDecoder]: T = implicitly[CCCassFormatDecoder[T]].decode(r).getOrThrow
    def getAs[T: CCCassFormatDecoder]: Option[T] = implicitly[CCCassFormatDecoder[T]].decode(r).getRightOpt
    def getOrElse[T: CCCassFormatDecoder](default: => T): T = getAs[T].getOrElse(default)
  }
}
