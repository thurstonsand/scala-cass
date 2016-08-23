package com.weather.scalacass

import java.nio.ByteBuffer

import com.datastax.driver.core.{DataType, Row}
import com.datastax.driver.core.exceptions.{InvalidTypeException, QueryExecutionException}

import scala.collection.mutable.ListBuffer
import scala.language.higherKinds
import scala.util.{Try, Failure => TFailure, Success => TSuccess}

trait CassFormatDecoder[T] { self =>
  import CassFormatDecoder.ValueNotDefinedException

  type From <: AnyRef
  def clazz: Class[From]
  def f2t(f: From): Either[Throwable, T]
  def extract(r: Row, name: String): From
  def decode(r: Row, name: String): Either[Throwable, T] = Try[Either[Throwable, T]](
    if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
    else f2t(extract(r, name))
  ) match {
      case TSuccess(v) => v
      case TFailure(e) => Left(e)
    }
  final def map[U](fn: T => U): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val clazz = self.clazz
    def f2t(f: From): Either[Throwable, U] = self.f2t(f).right.map(fn)
    def extract(r: Row, name: String): From = self.extract(r, name)
  }
  final def flatMap[U](fn: T => Either[Throwable, U]): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val clazz = self.clazz
    def f2t(f: From): Either[Throwable, U] = self.f2t(f).right.flatMap(fn)
    def extract(r: Row, name: String): From = self.extract(r, name)
  }
}

object CassFormatDecoder extends CassFormatDecoderVersionSpecific {
  type Aux[T, From0] = CassFormatDecoder[T] { type From = From0 }
  def apply[T: CassFormatDecoder] = implicitly[CassFormatDecoder[T]]

  private[scalacass] implicit class TryEither[T](val t: Try[T]) extends AnyVal {
    def toEither: Either[Throwable, T] = t match {
      case TSuccess(s) => Right(s)
      case TFailure(f) => Left(f)
    }
  }

  class ValueNotDefinedException(m: String) extends QueryExecutionException(m)

  private[scalacass] def sameTypeCassFormatDecoder[T <: AnyRef](_clazz: Class[T], _extract: (Row, String) => T) = new CassFormatDecoder[T] {
    type From = T
    val clazz = _clazz
    def f2t(f: From) = Right(f)
    def extract(r: Row, name: String) = _extract(r, name)
  }
  def safeConvertCassFormatDecoder[T, F <: AnyRef](_clazz: Class[F], _f2t: F => T, _extract: (Row, String) => F) = new CassFormatDecoder[T] {
    type From = F
    val clazz = _clazz
    def f2t(f: From) = Right(_f2t(f))
    def extract(r: Row, name: String) = _extract(r, name)
  }

  // decoders

  implicit val stringFormat = sameTypeCassFormatDecoder(classOf[String], _ getString _)
  implicit val uuidFormat = sameTypeCassFormatDecoder(classOf[java.util.UUID], _ getUUID _)
  implicit val iNetFormat = sameTypeCassFormatDecoder[java.net.InetAddress](classOf[java.net.InetAddress], _ getInet _)

  implicit val intFormat = safeConvertCassFormatDecoder[Int, java.lang.Integer](classOf[java.lang.Integer], Int.unbox, _ getInt _)

  implicit val longFormat = safeConvertCassFormatDecoder[Long, java.lang.Long](classOf[java.lang.Long], Long.unbox, _ getLong _)
  implicit val booleanFormat = safeConvertCassFormatDecoder[Boolean, java.lang.Boolean](classOf[java.lang.Boolean], Boolean.unbox, _ getBool _)
  implicit val doubleFormat = safeConvertCassFormatDecoder[Double, java.lang.Double](classOf[java.lang.Double], Double.unbox, _ getDouble _)
  implicit val floatFormat = safeConvertCassFormatDecoder[Float, java.lang.Float](classOf[java.lang.Float], Float.unbox, _ getFloat _)
  implicit val bigIntegerFormat = safeConvertCassFormatDecoder[BigInt, java.math.BigInteger](classOf[java.math.BigInteger], BigInt.javaBigInteger2bigInt, _ getVarint _)
  implicit val bigDecimalFormat = safeConvertCassFormatDecoder[BigDecimal, java.math.BigDecimal](classOf[java.math.BigDecimal], BigDecimal.javaBigDecimal2bigDecimal, _ getDecimal _)

  implicit def listFormat[T](implicit underlying: CassFormatDecoder[T]) = new CassFormatDecoder[List[T]] {
    type From = java.util.List[underlying.From]
    val clazz = classOf[From]
    def f2t(f: From): Either[Throwable, List[T]] = {
      val acc = List.newBuilder[T]
      @scala.annotation.tailrec
      def process(it: java.util.ListIterator[underlying.From]): Either[Throwable, List[T]] = if (!it.hasNext) Right(acc.result)
      else {
        underlying.f2t(it.next()) match {
          case Right(h) =>
            acc += h; process(it)
          case Left(exc) => Left(exc)
        }
      }
      process(f.listIterator)
    }
    def extract(r: Row, name: String) = r getList (name, underlying.clazz)
  }

  implicit def setFormat[T](implicit underlying: CassFormatDecoder[T]) = new CassFormatDecoder[Set[T]] {
    type From = java.util.Set[underlying.From]
    val clazz = classOf[From]
    def f2t(f: From): Either[Throwable, Set[T]] = {
      val acc = Set.newBuilder[T]
      @scala.annotation.tailrec
      def process(it: java.util.Iterator[underlying.From]): Either[Throwable, Set[T]] = if (!it.hasNext) Right(acc.result)
      else {
        underlying.f2t(it.next()) match {
          case Right(h) =>
            acc += h; process(it)
          case Left(exc) => Left(exc)
        }
      }
      process(f.iterator)
    }
    def extract(r: Row, name: String) = r getSet (name, underlying.clazz)
  }

  implicit def mapFormat[K, V](implicit underlyingK: CassFormatDecoder[K], underlyingV: CassFormatDecoder[V]) =
    new CassFormatDecoder[Map[K, V]] {
      type From = java.util.Map[underlyingK.From, underlyingV.From]
      val clazz = classOf[From]
      def f2t(f: From): Either[Throwable, Map[K, V]] = {
        val acc = Map.newBuilder[K, V]
        @scala.annotation.tailrec
        def process(it: java.util.Iterator[java.util.Map.Entry[underlyingK.From, underlyingV.From]]): Either[Throwable, Map[K, V]] =
          if (!it.hasNext) Right(acc.result) else {
            val n = it.next()
            (for {
              k <- underlyingK.f2t(n.getKey).right
              v <- underlyingV.f2t(n.getValue).right
            } yield (k, v)) match {
              case Right((k, v)) =>
                acc += k -> v; process(it)
              case Left(exc) => Left(exc)
            }
          }
        process(f.entrySet.iterator)
      }
      def extract(r: Row, name: String) = r getMap (name, underlyingK.clazz, underlyingV.clazz)
    }

  implicit val blobFormat = new CassFormatDecoder[Array[Byte]] {
    type From = java.nio.ByteBuffer
    val clazz = classOf[java.nio.ByteBuffer]

    def f2t(f: From) = Try(com.datastax.driver.core.utils.Bytes.getArray(f).toIndexedSeq.toArray).toEither
    def extract(r: Row, name: String): ByteBuffer = r getBytes name
    override def decode(r: Row, name: String): Either[Throwable, Array[Byte]] =
      if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
      else {
        val cassName = r.getColumnDefinitions.getType(name).getName
        if (!(cassName == DataType.Name.BLOB))
          Left(new InvalidTypeException(s"Column $name is a $cassName, cannot be retrieved as a blob"))
        else f2t(extract(r, name))
      }
  }

  implicit def optionFormat[A](implicit underlying: CassFormatDecoder[A]): CassFormatDecoder[Option[A]] = new CassFormatDecoder[Option[A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From): Either[Throwable, Option[A]] = underlying.f2t(f).right.map(Option(_))
    def extract(r: Row, name: String) = underlying.extract(r, name)
    override def decode(r: Row, name: String): Either[Throwable, Option[A]] = Right(super.decode(r, name).right getOrElse None)
  }

  implicit def eitherFormat[A](implicit underlying: CassFormatDecoder[A]): CassFormatDecoder[Either[Throwable, A]] = new CassFormatDecoder[Either[Throwable, A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From) = Right(underlying.f2t(f))
    def extract(r: Row, name: String) = underlying.extract(r, name)
    override def decode(r: Row, name: String) = super.decode(r, name) match {
      case Left(l) => Right(Left(l))
      case same    => same
    }
  }
}