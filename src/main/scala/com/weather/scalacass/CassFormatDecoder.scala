package com.weather.scalacass

import java.nio.ByteBuffer

import com.datastax.driver.core.{DataType, Row, TupleValue}
import com.datastax.driver.core.exceptions.{InvalidTypeException, QueryExecutionException}
import NotRecoverable.Try2Either

import scala.util.Try

trait CassFormatDecoder[T] { self =>
  import CassFormatDecoder.ValueNotDefinedException

  private[scalacass]type From <: AnyRef
  private[scalacass] def clazz: Class[From]
  private[scalacass] def f2t(f: From): Either[Throwable, T]
  private[scalacass] def extract(r: Row, name: String): From
  private[scalacass] def decode(r: Row, name: String): Either[Throwable, T] = Try[Either[Throwable, T]](
    if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
    else f2t(extract(r, name))
  ).unwrap[T]
  private[scalacass] def tupleExtract(tup: TupleValue, pos: Int): From
  private[scalacass] def tupleDecode(tup: TupleValue, pos: Int): Either[Throwable, T] = Try[Either[Throwable, T]](
    if (tup.isNull(pos)) Left(new ValueNotDefinedException(s"""position $pos was not defined in tuple $tup"""))
    else f2t(tupleExtract(tup, pos))
  ).unwrap[T]
  final def map[U](fn: T => U): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val clazz = self.clazz
    def f2t(f: From): Either[Throwable, U] = self.f2t(f).right.map(fn)
    def extract(r: Row, name: String): From = self.extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int): From = self.tupleExtract(tup, pos)
  }
  final def flatMap[U](fn: T => Either[Throwable, U]): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val clazz = self.clazz
    def f2t(f: From): Either[Throwable, U] = self.f2t(f).right.flatMap(fn)
    def extract(r: Row, name: String): From = self.extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int): From = self.tupleExtract(tup, pos)
  }

  final def as(r: Row)(name: String): T = decode(r, name) match {
    case Right(v)  => v
    case Left(exc) => throw exc
  }
  final def getAs(r: Row)(name: String): Option[T] = decode(r, name).right.toOption
  final def getOrElse(r: Row)(name: String, default: T): T = decode(r, name).right.getOrElse(default)
  final def attemptAs(r: Row)(name: String): Either[Throwable, T] = decode(r, name)
}

// extended by CassFormatDecoderVersionSpecific
trait LowPriorityCassFormatDecoder {
  implicit def tupleFormat[TUP <: Product](implicit underlying: TupleCassFormatDecoder[TUP]): CassFormatDecoder[TUP] = new CassFormatDecoder[TUP] {
    type From = TupleValue
    val clazz = classOf[TupleValue]
    def f2t(f: From) = underlying.decode(f, 0)
    def extract(r: Row, name: String) = r getTupleValue name
    def tupleExtract(tup: TupleValue, pos: Int) = tup getTupleValue pos
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf", "org.brianmckenna.wartremover.warts.IsInstanceOf"))
    override def decode(r: Row, name: String): Either[Throwable, TUP] = super.decode(r, name) match {
      case Left(f: java.lang.ArrayIndexOutOfBoundsException) => Left(new InvalidTypeException("tuple of wrong arity", f))
      case other => other
    }
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf", "org.brianmckenna.wartremover.warts.IsInstanceOf"))
    override def tupleDecode(tup: TupleValue, pos: Int): Either[Throwable, TUP] = super.tupleDecode(tup, pos) match {
      case Left(f: java.lang.ArrayIndexOutOfBoundsException) => Left(new InvalidTypeException("tuple of wrong arity", f))
      case other => other
    }
  }
}

object CassFormatDecoder extends CassFormatDecoderVersionSpecific {
  type Aux[T, From0] = CassFormatDecoder[T] { type From = From0 }
  def apply[T: CassFormatDecoder] = implicitly[CassFormatDecoder[T]]

  //  private[scalacass] implicit class TryEither[T](val t: Try[T]) extends AnyVal {
  //    def toEither: Either[Throwable, T] = t match {
  //      case TSuccess(s) => Right(s)
  //      case TFailure(f) => Left(f)
  //    }
  //  }

  class ValueNotDefinedException(m: String) extends QueryExecutionException(m)

  private[scalacass] def sameTypeCassFormatDecoder[T <: AnyRef](_clazz: Class[T], _extract: (Row, String) => T, _tupExtract: (TupleValue, Int) => T) = new CassFormatDecoder[T] {
    type From = T
    val clazz = _clazz
    def f2t(f: From) = Right(f)
    def extract(r: Row, name: String) = _extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int): T = _tupExtract(tup, pos)
  }
  def safeConvertCassFormatDecoder[T, F <: AnyRef](_clazz: Class[F], _f2t: F => T, _extract: (Row, String) => F, _tupExtract: (TupleValue, Int) => F) = new CassFormatDecoder[T] {
    type From = F
    val clazz = _clazz
    def f2t(f: From) = Right(_f2t(f))
    def extract(r: Row, name: String) = _extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int) = _tupExtract(tup, pos)
  }

  // decoders

  implicit val stringFormat = sameTypeCassFormatDecoder(classOf[String], _ getString _, _ getString _)
  implicit val uuidFormat = sameTypeCassFormatDecoder(classOf[java.util.UUID], _ getUUID _, _ getUUID _)
  implicit val iNetFormat = sameTypeCassFormatDecoder[java.net.InetAddress](classOf[java.net.InetAddress], _ getInet _, _ getInet _)

  implicit val intFormat = safeConvertCassFormatDecoder[Int, java.lang.Integer](classOf[java.lang.Integer], Int.unbox, _ getInt _, _ getInt _)

  implicit val longFormat = safeConvertCassFormatDecoder[Long, java.lang.Long](classOf[java.lang.Long], Long.unbox, _ getLong _, _ getLong _)
  implicit val booleanFormat = safeConvertCassFormatDecoder[Boolean, java.lang.Boolean](classOf[java.lang.Boolean], Boolean.unbox, _ getBool _, _ getBool _)
  implicit val doubleFormat = safeConvertCassFormatDecoder[Double, java.lang.Double](classOf[java.lang.Double], Double.unbox, _ getDouble _, _ getDouble _)
  implicit val floatFormat = safeConvertCassFormatDecoder[Float, java.lang.Float](classOf[java.lang.Float], Float.unbox, _ getFloat _, _ getFloat _)
  implicit val bigIntegerFormat = safeConvertCassFormatDecoder[BigInt, java.math.BigInteger](classOf[java.math.BigInteger], BigInt.javaBigInteger2bigInt, _ getVarint _, _ getVarint _)
  implicit val bigDecimalFormat = safeConvertCassFormatDecoder[BigDecimal, java.math.BigDecimal](classOf[java.math.BigDecimal], BigDecimal.javaBigDecimal2bigDecimal, _ getDecimal _, _ getDecimal _)

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
    def tupleExtract(tup: TupleValue, pos: Int) = tup getList (pos, underlying.clazz)
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
    def tupleExtract(tup: TupleValue, pos: Int) = tup getSet (pos, underlying.clazz)
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
      def tupleExtract(tup: TupleValue, pos: Int) = tup getMap (pos, underlyingK.clazz, underlyingV.clazz)
    }

  implicit val blobFormat: CassFormatDecoder[Array[Byte]] = new CassFormatDecoder[Array[Byte]] {
    type From = java.nio.ByteBuffer
    val clazz = classOf[java.nio.ByteBuffer]

    def f2t(f: From) = Try(com.datastax.driver.core.utils.Bytes.getArray(f).toIndexedSeq.toArray).toEither
    def extract(r: Row, name: String): ByteBuffer = r getBytes name
    override def decode(r: Row, name: String): Either[Throwable, Array[Byte]] = Try[Either[Throwable, Array[Byte]]](
      if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
      else {
        val cassName = r.getColumnDefinitions.getType(name).getName
        if (cassName != DataType.Name.BLOB)
          Left(new InvalidTypeException(s"Column $name is a $cassName, is not a blob"))
        else f2t(extract(r, name))
      }
    ).unwrap[Array[Byte]]
    def tupleExtract(tup: TupleValue, pos: Int): ByteBuffer = tup getBytes pos
    override def tupleDecode(tup: TupleValue, pos: Int): Either[Throwable, Array[Byte]] = Try[Either[Throwable, Array[Byte]]](
      if (tup.isNull(pos)) Left(new ValueNotDefinedException(s"""position $pos was not defined in tuple $tup"""))
      else {
        val cassName = tup.getType.getComponentTypes.get(pos).getName
        if (cassName != DataType.Name.BLOB)
          Left(new InvalidTypeException(s"position $pos in tuple $tup is not a blob"))
        else f2t(tupleExtract(tup, pos))
      }
    ).unwrap[Array[Byte]]
  }

  implicit def optionFormat[A](implicit underlying: CassFormatDecoder[A]): CassFormatDecoder[Option[A]] = new CassFormatDecoder[Option[A]] {
    type From = underlying.From
    val clazz = underlying.clazz
    def f2t(f: From): Either[Throwable, Option[A]] = underlying.f2t(f).right.map(Option(_))
    def extract(r: Row, name: String) = underlying.extract(r, name)
    override def decode(r: Row, name: String): Either[Throwable, Option[A]] = Right(super.decode(r, name).right getOrElse None)
    def tupleExtract(tup: TupleValue, pos: Int) = underlying.tupleExtract(tup, pos)
    override def tupleDecode(tup: TupleValue, pos: Int): Either[Throwable, Option[A]] =
      Right(super.tupleDecode(tup, pos).right getOrElse None)
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
    def tupleExtract(tup: TupleValue, pos: Int) = underlying.tupleExtract(tup, pos)
    override def tupleDecode(tup: TupleValue, pos: Int) = super.tupleDecode(tup, pos) match {
      case Left(l) => Right(Left(l))
      case same    => same
    }
  }
}