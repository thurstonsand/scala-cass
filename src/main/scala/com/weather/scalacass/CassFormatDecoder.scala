package com.weather.scalacass

import com.datastax.driver.core.Row
import com.datastax.driver.core.exceptions.InvalidTypeException
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.util.{Failure => TFailure, Success => TSuccess, Try}

trait CassFormatDecoder[T] { self =>
  type From <: AnyRef
  def clazz: Class[From]
  def f2t(f: From): T
  def decode(r: Row, name: String): Either[Throwable, T]
  def map[U](fn: T => U) = new CassFormatDecoder[U] {
    type From = self.From
    val clazz = self.clazz
    def f2t(f: From): U = fn(self.f2t(f))
    def decode(r: Row, name: String): Either[Throwable, U] = self.decode(r, name).right.map(fn)
  }
}

trait LowPriorityCassFormatDecoder {
  private def tryDecode[T](r: Row, name: String, decode: (Row, String) => T) = Try[Either[Throwable, T]](
    if (r.isNull(name)) Left(new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
    else Right(decode(r, name))
  ) match {
      case TSuccess(v) => v
      case TFailure(e) => Left(e)
    }

  def sameTypeCassFormat[T <: AnyRef](_clazz: Class[T], _decode: (Row, String) => T) = new CassFormatDecoder[T] {
    type From = T
    val clazz = _clazz
    def f2t(f: From) = f
    def decode(r: Row, name: String) = tryDecode(r, name, _decode)
  }
  implicit val stringFormat = sameTypeCassFormat(classOf[String], _ getString _)
  implicit val uuidFormat = sameTypeCassFormat(classOf[java.util.UUID], _ getUUID _)
  implicit val iNetFormat = sameTypeCassFormat[java.net.InetAddress](classOf[java.net.InetAddress], _ getInet _)

  def noConvertCassFormat[T, F <: AnyRef](_clazz: Class[F], _f2t: F => T, _decode: (Row, String) => T) = new CassFormatDecoder[T] {
    type From = F
    val clazz = _clazz
    def f2t(f: From) = _f2t(f)
    def decode(r: Row, name: String) = tryDecode(r, name, _decode)
  }
  implicit val intFormat = noConvertCassFormat(classOf[java.lang.Integer], Int.unbox, _ getInt _)

  implicit val longFormat = noConvertCassFormat(classOf[java.lang.Long], Long.unbox, _ getLong _)
  implicit val booleanFormat = noConvertCassFormat(classOf[java.lang.Boolean], Boolean.unbox, _ getBool _)
  implicit val doubleFormat = noConvertCassFormat(classOf[java.lang.Double], Double.unbox, _ getDouble _)
  implicit val floatFormat = noConvertCassFormat(classOf[java.lang.Float], Float.unbox, _ getFloat _)
  implicit val bigIntegerFormat = noConvertCassFormat[BigInt, java.math.BigInteger](classOf[java.math.BigInteger], BigInt.javaBigInteger2bigInt, _ getVarint _)
  implicit val bigDecimalFormat = noConvertCassFormat[BigDecimal, java.math.BigDecimal](classOf[java.math.BigDecimal], BigDecimal.javaBigDecimal2bigDecimal, _ getDecimal _)

  def collectionCassFormat[T, F <: AnyRef](_clazz: Class[F], _f2t: F => T, _decode: (Row, String) => F) = new CassFormatDecoder[T] {
    type From = F
    val clazz = _clazz
    def f2t(f: F) = _f2t(f)
    def decode(r: Row, name: String) = tryDecode(r, name, (rr, nn) => _f2t(_decode(rr, nn)))
  }
  implicit def listFormat[T](implicit underlying: CassFormatDecoder[T]) = collectionCassFormat[List[T], java.util.List[underlying.From]](
    classOf[java.util.List[underlying.From]],
    (_: java.util.List[underlying.From]).asScala.map(underlying.f2t).toList,
    (r, name) => r.getList(name, underlying.clazz)
  )
  implicit def mapFormat[A, B](implicit underlyingA: CassFormatDecoder[A], underlyingB: CassFormatDecoder[B]) = collectionCassFormat(
    classOf[java.util.Map[underlyingA.From, underlyingB.From]],
    (_: java.util.Map[underlyingA.From, underlyingB.From]).asScala.map { case (k, v) => underlyingA.f2t(k) -> underlyingB.f2t(v) }.toMap,
    (r, name) => r.getMap(name, underlyingA.clazz, underlyingB.clazz)
  )
  implicit def setFormat[A](implicit underlying: CassFormatDecoder[A]) = collectionCassFormat(
    classOf[java.util.Set[underlying.From]],
    (_: java.util.Set[underlying.From]).asScala.map(underlying.f2t).toSet,
    (r, name) => r.getSet(name, underlying.clazz)
  )

  implicit val dateTimeFormat = new CassFormatDecoder[DateTime] {
    type From = java.util.Date
    val clazz = classOf[java.util.Date]
    def f2t(f: From) = new DateTime(f)
    def decode(r: Row, name: String) = tryDecode(r, name, (rr, nn) => f2t(r.getDate(name)))
  }

  implicit val blobFormat = new CassFormatDecoder[Array[Byte]] {
    type From = java.nio.ByteBuffer
    val clazz = classOf[From]
    def f2t(f: From) = com.datastax.driver.core.utils.Bytes.getArray(f).toIndexedSeq.toArray
    def decode(r: Row, name: String) = Try[Either[Throwable, Array[Byte]]] {
      val cassClass = r.getColumnDefinitions.getType(name).asJavaClass
      if (r.isNull(name)) Left(new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
      else if (cassClass != clazz)
        Left(new InvalidTypeException(s"Column $name is a $cassClass, cannot be retrieved as an Array[Byte]"))
      else Right(f2t(r.getBytes(name)))
    } match {
      case TSuccess(v) => v
      case TFailure(e) => Left(e)
    }
  }

  implicit def optionFormat[A](implicit underlying: CassFormatDecoder[A]) = new CassFormatDecoder[Option[A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From) = Some(underlying.f2t(f))
    def decode(r: Row, name: String) = Try(
      if (r.isNull(name)) None
      else underlying.decode(r, name).right.toOption
    ) match {
        case TSuccess(v) => Right(v)
        case TFailure(_) => Right(None)
      }
  }

  implicit def eitherFormat[A](implicit underlying: CassFormatDecoder[A]) = new CassFormatDecoder[Either[Throwable, A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From) = Right(underlying.f2t(f))
    def decode(r: Row, name: String) = Try(
      if (r.isNull(name)) Left(new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
      else underlying.decode(r, name)
    ) match {
        case TSuccess(v) => Right(v)
        case TFailure(f) => Right(Left(f))
      }
  }
}

object CassFormatDecoder extends LowPriorityCassFormatDecoder {
  type Aux[T, From0] = CassFormatDecoder[T] { type From = From0 }
  def apply[T: CassFormatDecoder] = implicitly[CassFormatDecoder[T]]
}