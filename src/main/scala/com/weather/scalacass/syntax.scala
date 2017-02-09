package com.weather.scalacass

import com.datastax.driver.core.Row

object syntax {
  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T](name: String)(implicit d: CassFormatDecoder[T]): T = d.as(r)(name)
    def getAs[T](name: String)(implicit d: CassFormatDecoder[Option[T]]): Option[T] = d.as(r)(name)
    def getOrElse[T](name: String, default: => T)(implicit d: CassFormatDecoder[Option[T]]): T = d.as(r)(name).getOrElse(default)
    def attemptAs[T](name: String)(implicit d: CassFormatDecoder[T]): Result[T] = d.attemptAs(r)(name)

    def as[T](implicit ccd: CCCassFormatDecoder[T]): T = ccd.as(r)
    def getAs[T](implicit ccd: CCCassFormatDecoder[Option[T]]): Option[T] = ccd.as(r)
    def getOrElse[T](default: => T)(implicit ccd: CCCassFormatDecoder[Option[T]]): T = ccd.as(r).getOrElse(default)
    def attemptAs[T](implicit ccd: CCCassFormatDecoder[T]): Result[T] = ccd.decode(r)
  }

  implicit class RichIterator(val it: Iterator[Row]) extends AnyVal {
    def as[T](name: String)(implicit d: CassFormatDecoder[T]): Iterator[T] = it.map(r => d.as(r)(name))
    def getAs[T](name: String)(implicit d: CassFormatDecoder[Option[T]]): Iterator[Option[T]] = it.map(r => d.as(r)(name))
    def getOrElse[T](name: String, default: => T)(implicit d: CassFormatDecoder[Option[T]]): Iterator[T] = it.map(r => d.as(r)(name).getOrElse(default))
    def attemptAs[T](name: String)(implicit d: CassFormatDecoder[T]): Iterator[Result[T]] = it.map(r => d.attemptAs(r)(name))

    def as[T](implicit ccd: CCCassFormatDecoder[T]): Iterator[T] = it.map(r => ccd.as(r))
    def getAs[T](implicit ccd: CCCassFormatDecoder[Option[T]]): Iterator[Option[T]] = it.map(r => ccd.as(r))
    def getOrElse[T](default: => T)(implicit ccd: CCCassFormatDecoder[Option[T]]): Iterator[T] = it.map(r => ccd.as(r).getOrElse(default))
    def attemptAs[T](implicit ccd: CCCassFormatDecoder[T]): Iterator[Result[T]] = it.map(r => ccd.attemptAs(r))
  }

  implicit class RichOption(val opt: Option[Row]) extends AnyVal {
    def as[T](name: String)(implicit d: CassFormatDecoder[T]): Option[T] = opt.map(r => d.as(r)(name))
    def getAs[T](name: String)(implicit d: CassFormatDecoder[Option[T]]): Option[Option[T]] = opt.map(r => d.as(r)(name))
    def getOrElse[T](name: String, default: => T)(implicit d: CassFormatDecoder[Option[T]]): Option[T] = opt.map(r => d.as(r)(name).getOrElse(default))
    def attemptAs[T](name: String)(implicit d: CassFormatDecoder[T]): Option[Result[T]] = opt.map(r => d.attemptAs(r)(name))

    def as[T](implicit ccd: CCCassFormatDecoder[T]): Option[T] = opt.map(r => ccd.as(r))
    def getAs[T](implicit ccd: CCCassFormatDecoder[Option[T]]): Option[Option[T]] = opt.map(r => ccd.as(r))
    def getOrElse[T](default: => T)(implicit ccd: CCCassFormatDecoder[Option[T]]): Option[T] = opt.map(r => ccd.as(r).getOrElse(default))
    def attemptAs[T](implicit ccd: CCCassFormatDecoder[T]): Option[Result[T]] = opt.map(r => ccd.attemptAs(r))
  }
}
