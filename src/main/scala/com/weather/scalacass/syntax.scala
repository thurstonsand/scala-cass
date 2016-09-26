package com.weather.scalacass

import com.datastax.driver.core.Row

object syntax {
  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T](name: String)(implicit d: CassFormatDecoder[T]): T = d.as(r)(name)
    def getAs[T](name: String)(implicit d: CassFormatDecoder[T]): Option[T] = d.getAs(r)(name)
    def getOrElse[T](name: String, default: T)(implicit d: CassFormatDecoder[T]): T = d.getOrElse(r)(name, default)
    def attemptAs[T](name: String)(implicit d: CassFormatDecoder[T]): Either[Throwable, T] = d.attemptAs(r)(name)

    def as[T](implicit ccd: CCCassFormatDecoder[T]): T = ccd.as(r)
    def getAs[T](implicit ccd: CCCassFormatDecoder[T]): Option[T] = ccd.getAs(r)
    def getOrElse[T](default: T)(implicit ccd: CCCassFormatDecoder[T]): T = ccd.getOrElse(r)(default)
    def attemptAs[T](implicit ccd: CCCassFormatDecoder[T]): Either[Throwable, T] = ccd.attemptAs(r)
  }
}
