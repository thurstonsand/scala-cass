package com.weather.scalacass

import com.datastax.driver.core.Row

object ScalaCass {
  private[scalacass] implicit class RichEither[+A <: Throwable, +B](val e: Either[A, B]) extends AnyVal {
    def getOrThrow = e match {
      case Right(v) => v
      case Left(exc) => throw exc
    }
    def getRightOpt = e.right.toOption
  }

  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T: CassFormatDecoder](name: String): T = CassFormatDecoder[T].decode(r, name).getOrThrow
    def getAs[T: CassFormatDecoder](name: String): Option[T] = CassFormatDecoder[T].decode(r, name).getRightOpt
    def getOrElse[T: CassFormatDecoder](name: String, default: => T): T = getAs[T](name).getOrElse(default)

    def as[T: CCCassFormatDecoder]: T = CCCassFormatDecoder[T].decode(r).getOrThrow
    def getAs[T: CCCassFormatDecoder]: Option[T] = CCCassFormatDecoder[T].decode(r).getRightOpt
    def getOrElse[T: CCCassFormatDecoder](default: => T): T = getAs[T].getOrElse(default)
  }

  implicit class RichIteratorRow(val ir: Iterator[Row]) extends AnyVal {
    def as[T: CassFormatDecoder](name: String): Iterator[T] = ir.map(_.as[T](name))
    def getAs[T: CassFormatDecoder](name: String): Iterator[T] = ir.flatMap(_.getAs[T](name))
    def getOrElse[T: CassFormatDecoder](name: String, default: => T): Iterator[T] = {
      lazy val d = default
      ir.map(_.getOrElse[T](name, d))
    }

    def as[T: CCCassFormatDecoder]: Iterator[T] = ir.map(_.as[T])
    def getAs[T: CCCassFormatDecoder]: Iterator[T] = ir.flatMap(_.getAs[T])
    def getOrElse[T: CCCassFormatDecoder](default: => T): Iterator[T] = {
      lazy val d = default
      ir.map(_.getOrElse[T](d))
    }
  }

  implicit class RichOptionRow(val or: Option[Row]) extends AnyVal {
    def as[T: CassFormatDecoder](name: String): Option[T] = or.map(_.as[T](name))
    def getAs[T: CassFormatDecoder](name: String): Option[T] = or.flatMap(_.getAs[T](name))
    def getOrElse[T: CassFormatDecoder](name: String, default: => T) = or.map(_.getOrElse[T](name, default))

    def as[T: CCCassFormatDecoder]: Option[T] = or.map(_.as[T])
    def getAs[T: CCCassFormatDecoder]: Option[T] = or.flatMap(_.getAs[T])
    def getOrElse[T: CCCassFormatDecoder](default: => T): Option[T] = or.map(_.getOrElse(default))
  }
}
