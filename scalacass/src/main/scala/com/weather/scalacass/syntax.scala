package com.weather.scalacass

import com.datastax.driver.core.Row

object syntax {
  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T](name: String)(implicit d: CassFormatDecoder[T]): T = d.as(r)(name)
    def getAs[T](name: String)(implicit d: CassFormatDecoder[T]): Option[T] = d.getAs(r)(name)
    def getOrElse[T](name: String, default: => T)(implicit d: CassFormatDecoder[T]): T = d.getOrElse(r)(name, default)
    def attemptAs[T](name: String)(implicit d: CassFormatDecoder[T]): Result[T] = d.attemptAs(r)(name)

    def as[T](implicit ccd: CCCassFormatDecoder[T]): T = ccd.as(r)
    def getAs[T](implicit ccd: CCCassFormatDecoder[T]): Option[T] = ccd.getAs(r)
    def getOrElse[T](default: => T)(implicit ccd: CCCassFormatDecoder[T]): T = ccd.getOrElse(r)(default)
    def attemptAs[T](implicit ccd: CCCassFormatDecoder[T]): Result[T] = ccd.attemptAs(r)
  }

  implicit class RichIterator(val it: Iterator[Row]) extends AnyVal {
    def as[T](name: String)(implicit d: CassFormatDecoder[T]): Iterator[T] = it.map(r => d.as(r)(name))
    def getAs[T](name: String)(implicit d: CassFormatDecoder[T]): Iterator[Option[T]] = it.map(r => d.getAs(r)(name))
    def getOrElse[T](name: String, default: => T)(implicit d: CassFormatDecoder[T]): Iterator[T] = it.map(r => d.getOrElse(r)(name, default))
    def attemptAs[T](name: String)(implicit d: CassFormatDecoder[T]): Iterator[Result[T]] = it.map(r => d.attemptAs(r)(name))

    def as[T](implicit ccd: CCCassFormatDecoder[T]): Iterator[T] = it.map(r => ccd.as(r))
    def getAs[T](implicit ccd: CCCassFormatDecoder[T]): Iterator[Option[T]] = it.map(r => ccd.getAs(r))
    def getOrElse[T](default: => T)(implicit ccd: CCCassFormatDecoder[T]): Iterator[T] = it.map(r => ccd.getOrElse(r)(default))
    def attemptAs[T](implicit ccd: CCCassFormatDecoder[T]): Iterator[Result[T]] = it.map(r => ccd.attemptAs(r))
  }

  implicit class RichOption(val opt: Option[Row]) extends AnyVal {
    def as[T](name: String)(implicit d: CassFormatDecoder[T]): Option[T] = opt.map(r => d.as(r)(name))
    def getAs[T](name: String)(implicit d: CassFormatDecoder[T]): Option[Option[T]] = opt.map(r => d.getAs(r)(name))
    def getOrElse[T](name: String, default: => T)(implicit d: CassFormatDecoder[T]): Option[T] = opt.map(r => d.getOrElse(r)(name, default))
    def attemptAs[T](name: String)(implicit d: CassFormatDecoder[T]): Option[Result[T]] = opt.map(r => d.attemptAs(r)(name))

    def as[T](implicit ccd: CCCassFormatDecoder[T]): Option[T] = opt.map(r => ccd.as(r))
    def getAs[T](implicit ccd: CCCassFormatDecoder[T]): Option[Option[T]] = opt.map(r => ccd.getAs(r))
    def getOrElse[T](default: => T)(implicit ccd: CCCassFormatDecoder[T]): Option[T] = opt.map(r => ccd.getOrElse(r)(default))
    def attemptAs[T](implicit ccd: CCCassFormatDecoder[T]): Option[Result[T]] = opt.map(r => ccd.attemptAs(r))
  }
}
