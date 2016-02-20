package com.weather.scalacass

import com.datastax.driver.core.{Row, Session}

object ScalaCass extends CassandraFormats with ShapelessCassandraFormats {
  private implicit class RichEither[+A <: Throwable, +B](val e: Either[A, B]) extends AnyVal {
    def getOrThrow = e match {
      case Right(v) => v
      case Left(exc) => throw exc
    }
    def getRightOpt = e.right.toOption
  }

  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T: CassFormat](name: String): T = implicitly[CassFormat[T]].decode(r, name).getOrThrow
    def getAs[T: CassFormat](name: String): Option[T] = implicitly[CassFormat[T]].decode(r, name).getRightOpt
    def getOrElse[T: CassFormat](name: String, default: => T): T = getAs[T](name).getOrElse(default)

    def as[T](implicit f: CCCassFormat[T]): T = f.decode(r).getOrThrow
    def getAs[T](implicit f: CCCassFormat[T]): Option[T] = f.decode(r).getRightOpt
    def getOrElse[T](default: => T)(implicit f: CCCassFormat[T]): T = getAs[T].getOrElse(default)
  }

  object ScalaSession {
    def apply(keyspace: String)(implicit session: Session): ScalaSession = new ScalaSession(keyspace)
  }
}

