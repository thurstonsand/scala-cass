package com.weather.scalacass

import com.datastax.driver.core.Row
import com.datastax.driver.core.exceptions.{InvalidTypeException, QueryExecutionException}
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.language.higherKinds
import scala.util.{Try, Failure => TFailure, Success => TSuccess}

trait CassFormatDecoder[T] { self =>
  type From <: AnyRef
  def clazz: Class[From]
  def f2t(f: From): Either[Throwable, T]
  def decode(r: Row, name: String): Either[Throwable, T]
  def map[U](fn: T => U): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val clazz = self.clazz
    def f2t(f: From): Either[Throwable, U] = self.f2t(f).right.map(fn)
    def decode(r: Row, name: String): Either[Throwable, U] = self.decode(r, name).right.map(fn)
  }
  def flatMap[U](fn: T => Either[Throwable, U]): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val clazz = self.clazz
    def f2t(f: From): Either[Throwable, U] = self.f2t(f).right.flatMap(fn)
    def decode(r: Row, name: String): Either[Throwable, U] = self.decode(r, name).right.flatMap(fn)
  }
}

trait LowPriorityCassFormatDecoder {
  import CassFormatDecoder.{TryEither, ValueNotDefinedException}
  private def tryDecode[T](r: Row, name: String, decode: (Row, String) => T) = Try[Either[Throwable, T]](
    if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
    else Right(decode(r, name))
  ) match {
      case TSuccess(v) => v
      case TFailure(e) => Left(e)
    }
  private def tryDecodeE[T](r: Row, name: String, decode: (Row, String) => Either[Throwable, T]) = Try[Either[Throwable, T]](
    if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
    else decode(r, name)
  ) match {
      case TSuccess(v) => v
      case TFailure(e) => Left(e)
    }

  def sameTypeCassFormat[T <: AnyRef](_clazz: Class[T], _decode: (Row, String) => T) = new CassFormatDecoder[T] {
    type From = T
    val clazz = _clazz
    def f2t(f: From) = Right(f)
    def decode(r: Row, name: String) = tryDecode(r, name, _decode)
  }
  implicit val stringFormat = sameTypeCassFormat(classOf[String], _ getString _)
  implicit val uuidFormat = sameTypeCassFormat(classOf[java.util.UUID], _ getUUID _)
  implicit val iNetFormat = sameTypeCassFormat[java.net.InetAddress](classOf[java.net.InetAddress], _ getInet _)

  def noConvertCassFormat[T, F <: AnyRef](_clazz: Class[F], _f2t: F => T, _decode: (Row, String) => T) = new CassFormatDecoder[T] {
    type From = F
    val clazz = _clazz
    def f2t(f: From) = Right(_f2t(f))
    def decode(r: Row, name: String) = tryDecode(r, name, _decode)
  }
  implicit val intFormat = noConvertCassFormat(classOf[java.lang.Integer], Int.unbox, _ getInt _)

  implicit val longFormat = noConvertCassFormat(classOf[java.lang.Long], Long.unbox, _ getLong _)
  implicit val booleanFormat = noConvertCassFormat(classOf[java.lang.Boolean], Boolean.unbox, _ getBool _)
  implicit val doubleFormat = noConvertCassFormat(classOf[java.lang.Double], Double.unbox, _ getDouble _)
  implicit val floatFormat = noConvertCassFormat(classOf[java.lang.Float], Float.unbox, _ getFloat _)
  implicit val bigIntegerFormat = noConvertCassFormat[BigInt, java.math.BigInteger](classOf[java.math.BigInteger], BigInt.javaBigInteger2bigInt, _ getVarint _)
  implicit val bigDecimalFormat = noConvertCassFormat[BigDecimal, java.math.BigDecimal](classOf[java.math.BigDecimal], BigDecimal.javaBigDecimal2bigDecimal, _ getDecimal _)

  def collectionCassFormat1[Coll[_], T, F <: AnyRef, JColl[_] <: java.util.Collection[_]](
    _clazz: Class[JColl[F]],
    _f2t: F => Either[Throwable, T],
    j2s: JColl[F] => Iterable[F],
    lb2Coll: ListBuffer[T] => Coll[T],
    _decode: (Row, String) => JColl[F]
  ) =
    new CassFormatDecoder[Coll[T]] {
      type From = JColl[F]
      val clazz = _clazz
      def f2t(f: JColl[F]): Either[Throwable, Coll[T]] = {
        val acc = ListBuffer.empty[T]
        @scala.annotation.tailrec
        def process(l: Iterable[F]): Either[Throwable, Coll[T]] = l.headOption.map(_f2t) match {
          case Some(Left(ff)) => Left(ff)
          case Some(Right(n)) =>
            acc += n
            process(l.tail)
          case None => Right(lb2Coll(acc))
        }
        process(j2s(f))
      }
      def decode(r: Row, name: String): Either[Throwable, Coll[T]] = tryDecodeE(r, name, (rr, nn) => f2t(_decode(rr, nn)))
    }

  implicit def listFormat[T](implicit underlying: CassFormatDecoder[T]) = collectionCassFormat1[List, T, underlying.From, java.util.List](
    classOf[java.util.List[underlying.From]],
    underlying.f2t,
    _.asScala,
    _.toList,
    (r, n) => r.getList(n, underlying.clazz)
  )

  implicit def setFormat[T](implicit underlying: CassFormatDecoder[T]) = collectionCassFormat1[Set, T, underlying.From, java.util.Set](
    classOf[java.util.Set[underlying.From]],
    underlying.f2t,
    _.asScala,
    _.toSet,
    (r, n) => r.getSet(n, underlying.clazz)
  )

  implicit def mapFormat[A, B](implicit underlyingA: CassFormatDecoder[A], underlyingB: CassFormatDecoder[B]) =
    new CassFormatDecoder[Map[A, B]] {
      type From = java.util.Map[underlyingA.From, underlyingB.From]
      val clazz = classOf[java.util.Map[underlyingA.From, underlyingB.From]]
      def f2t(f: java.util.Map[underlyingA.From, underlyingB.From]): Either[Throwable, Map[A, B]] = {
        val acc = ListBuffer.empty[(A, B)]
        @scala.annotation.tailrec
        def process(l: Iterable[(underlyingA.From, underlyingB.From)]): Either[Throwable, Map[A, B]] = l.headOption.map {
          case (ll, rr) => for {
            _l <- underlyingA.f2t(ll).right
            _r <- underlyingB.f2t(rr).right
          } yield (_l, _r)
        } match {
          case Some(Left(ff)) => Left(ff)
          case Some(Right(n)) =>
            acc += n
            process(l.tail)
          case None => Right(acc.toMap)
        }
        process(f.asScala)
      }
      def decode(r: Row, name: String): Either[Throwable, Map[A, B]] =
        tryDecodeE(r, name, (rr, nn) => f2t(rr.getMap(nn, underlyingA.clazz, underlyingB.clazz)))
    }

  implicit val dateFormat = new CassFormatDecoder[java.util.Date] {
    type From = java.util.Date
    val clazz = classOf[java.util.Date]
    def f2t(f: From) = Right(f)
    def decode(r: Row, name: String) = tryDecodeE(r, name, (rr, nn) => f2t(r.getDate(name)))
  }

  implicit val dateTimeFormat: CassFormatDecoder[DateTime] =
    dateFormat.flatMap(d => Try(new DateTime(d)).toEither)

  implicit val blobFormat = new CassFormatDecoder[Array[Byte]] {
    type From = java.nio.ByteBuffer
    val clazz = classOf[From]
    def f2t(f: From) = Try(com.datastax.driver.core.utils.Bytes.getArray(f).toIndexedSeq.toArray).toEither
    def decode(r: Row, name: String) = Try[Either[Throwable, Array[Byte]]] {
      val cassClass = r.getColumnDefinitions.getType(name).asJavaClass
      if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
      else if (cassClass != clazz)
        Left(new InvalidTypeException(s"Column $name is a $cassClass, cannot be retrieved as an Array[Byte]"))
      else f2t(r.getBytes(name))
    } match {
      case TSuccess(v) => v
      case TFailure(e) => Left(e)
    }
  }

  implicit def optionFormat[A](implicit underlying: CassFormatDecoder[A]): CassFormatDecoder[Option[A]] = new CassFormatDecoder[Option[A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From): Either[Throwable, Some[A]] = underlying.f2t(f).right.map(Some(_))
    def decode(r: Row, name: String) = Try(
      if (r.isNull(name)) None
      else underlying.decode(r, name).right.toOption
    ) match {
        case TSuccess(v) => Right(v)
        case TFailure(_) => Right(None)
      }
  }

  implicit def eitherFormat[A](implicit underlying: CassFormatDecoder[A]): CassFormatDecoder[Either[Throwable, A]] = new CassFormatDecoder[Either[Throwable, A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From) = Right(underlying.f2t(f))
    def decode(r: Row, name: String) = Try(
      if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
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

  private[scalacass] implicit class TryEither[T](val t: Try[T]) extends AnyVal {
    def toEither: Either[Throwable, T] = t match {
      case TSuccess(s) => Right(s)
      case TFailure(f) => Left(f)
    }
  }

  class ValueNotDefinedException(m: String) extends QueryExecutionException(m)
}