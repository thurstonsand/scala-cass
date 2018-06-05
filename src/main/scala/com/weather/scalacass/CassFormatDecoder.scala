package com.weather.scalacass

import java.nio.ByteBuffer

import com.datastax.driver.core.{ DataType, Row, TupleValue }
import com.datastax.driver.core.exceptions.InvalidTypeException
import NotRecoverable.Try2Either
import com.google.common.reflect.{ TypeParameter, TypeToken }

import scala.util.Try

trait CassFormatDecoder[T] { self =>
  private[scalacass] type From <: AnyRef
  private[scalacass] def typeToken: TypeToken[From]
  private[scalacass] def f2t(f: From): Result[T]
  private[scalacass] def extract(r: Row, name: String): From
  private[scalacass] def decode(r: Row, name: String): Result[T] = Try[Result[T]](
    if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
    else f2t(extract(r, name))
  ).unwrap[T]
  private[scalacass] def tupleExtract(tup: TupleValue, pos: Int): From
  private[scalacass] def tupleDecode(tup: TupleValue, pos: Int): Result[T] = Try[Result[T]](
    if (tup.isNull(pos)) Left(new ValueNotDefinedException(s"""position $pos was not defined in tuple $tup"""))
    else f2t(tupleExtract(tup, pos))
  ).unwrap[T]
  final def map[U](fn: T => U): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val typeToken = self.typeToken
    def f2t(f: From): Result[U] = self.f2t(f).right.map(fn)
    def extract(r: Row, name: String): From = self.extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int): From = self.tupleExtract(tup, pos)
  }
  final def flatMap[U](fn: T => Result[U]): CassFormatDecoder[U] = new CassFormatDecoder[U] {
    type From = self.From
    val typeToken = self.typeToken
    def f2t(f: From): Result[U] = self.f2t(f).right.flatMap(fn)
    def extract(r: Row, name: String): From = self.extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int): From = self.tupleExtract(tup, pos)
  }

  final def as(r: Row)(name: String): T = decode(r, name) match {
    case Right(v)  => v
    case Left(exc) => throw exc
  }
  final def attemptAs(r: Row)(name: String): Result[T] = decode(r, name)
}

// extended by CassFormatDecoderVersionSpecific
trait LowPriorityCassFormatDecoder {
  implicit def tupleFormat[TUP <: Product](implicit underlying: TupleCassFormatDecoder[TUP]): CassFormatDecoder[TUP] = new CassFormatDecoder[TUP] {
    type From = TupleValue
    val typeToken = TypeToken.of(classOf[TupleValue])
    def f2t(f: From) = underlying.decode(f, 0)
    def extract(r: Row, name: String) = r getTupleValue name
    def tupleExtract(tup: TupleValue, pos: Int) = tup getTupleValue pos
    override def decode(r: Row, name: String): Result[TUP] = super.decode(r, name) match {
      case Left(f: java.lang.ArrayIndexOutOfBoundsException) => Left(new InvalidTypeException("tuple of wrong arity", f))
      case other => other
    }
    override def tupleDecode(tup: TupleValue, pos: Int): Result[TUP] = super.tupleDecode(tup, pos) match {
      case Left(f: java.lang.ArrayIndexOutOfBoundsException) => Left(new InvalidTypeException("tuple of wrong arity", f))
      case other => other
    }
  }
}

object CassFormatDecoder extends CassFormatDecoderVersionSpecific {
  type Aux[T, From0] = CassFormatDecoder[T] { type From = From0 }
  def apply[T](implicit decoder: CassFormatDecoder[T]): CassFormatDecoder[T] = decoder

  // in cassandra, empty collection == NULL, so null check is not helpful
  trait CollectionCassFormatDecoder[T] extends CassFormatDecoder[T] {
    override private[scalacass] def decode(r: Row, name: String): Either[Throwable, T] =
      Try[Either[Throwable, T]](f2t(extract(r, name))).unwrap[T]
    override private[scalacass] def tupleDecode(tup: TupleValue, pos: Int): Either[Throwable, T] =
      Try[Either[Throwable, T]](f2t(tupleExtract(tup, pos))).unwrap[T]
  }

  private[scalacass] def sameTypeCassFormatDecoder[T <: AnyRef](_typeToken: TypeToken[T], _extract: (Row, String) => T, _tupExtract: (TupleValue, Int) => T) = new CassFormatDecoder[T] {
    type From = T
    val typeToken = _typeToken
    def f2t(f: From) = Right(f)
    def extract(r: Row, name: String) = _extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int): T = _tupExtract(tup, pos)
  }
  def safeConvertCassFormatDecoder[T, F <: AnyRef](_typeToken: TypeToken[F], _f2t: F => T, _extract: (Row, String) => F, _tupExtract: (TupleValue, Int) => F) = new CassFormatDecoder[T] {
    type From = F
    val typeToken = _typeToken
    def f2t(f: From) = Right(_f2t(f))
    def extract(r: Row, name: String) = _extract(r, name)
    def tupleExtract(tup: TupleValue, pos: Int) = _tupExtract(tup, pos)
  }

  // decoders

  implicit val stringFormat: CassFormatDecoder[String] = sameTypeCassFormatDecoder(TypeToken.of(classOf[String]), _ getString _, _ getString _)
  implicit val uuidFormat: CassFormatDecoder[java.util.UUID] = sameTypeCassFormatDecoder(TypeToken.of(classOf[java.util.UUID]), _ getUUID _, _ getUUID _)
  implicit val iNetFormat: CassFormatDecoder[java.net.InetAddress] = sameTypeCassFormatDecoder[java.net.InetAddress](TypeToken.of(classOf[java.net.InetAddress]), _ getInet _, _ getInet _)

  implicit val intFormat: CassFormatDecoder[Int] = safeConvertCassFormatDecoder[Int, java.lang.Integer](TypeToken.of(classOf[java.lang.Integer]), Int.unbox, _ getInt _, _ getInt _)

  implicit val longFormat: CassFormatDecoder[Long] = safeConvertCassFormatDecoder[Long, java.lang.Long](TypeToken.of(classOf[java.lang.Long]), Long.unbox, _ getLong _, _ getLong _)
  implicit val booleanFormat: CassFormatDecoder[Boolean] = safeConvertCassFormatDecoder[Boolean, java.lang.Boolean](TypeToken.of(classOf[java.lang.Boolean]), Boolean.unbox, _ getBool _, _ getBool _)
  implicit val doubleFormat: CassFormatDecoder[Double] = safeConvertCassFormatDecoder[Double, java.lang.Double](TypeToken.of(classOf[java.lang.Double]), Double.unbox, _ getDouble _, _ getDouble _)
  implicit val floatFormat: CassFormatDecoder[Float] = safeConvertCassFormatDecoder[Float, java.lang.Float](TypeToken.of(classOf[java.lang.Float]), Float.unbox, _ getFloat _, _ getFloat _)
  implicit val bigIntegerFormat: CassFormatDecoder[BigInt] = safeConvertCassFormatDecoder[BigInt, java.math.BigInteger](TypeToken.of(classOf[java.math.BigInteger]), BigInt.javaBigInteger2bigInt, _ getVarint _, _ getVarint _)
  implicit val bigDecimalFormat: CassFormatDecoder[BigDecimal] = safeConvertCassFormatDecoder[BigDecimal, java.math.BigDecimal](TypeToken.of(classOf[java.math.BigDecimal]), BigDecimal.javaBigDecimal2bigDecimal, _ getDecimal _, _ getDecimal _)

  def listOf[T](eltType: TypeToken[T]): TypeToken[java.util.List[T]] =
    new TypeToken[java.util.List[T]]() {}.where(new TypeParameter[T]() {}, eltType)

  implicit def listFormat[T](implicit underlying: CassFormatDecoder[T]): CassFormatDecoder[List[T]] = new CollectionCassFormatDecoder[List[T]] {
    type From = java.util.List[underlying.From]
    val typeToken = listOf(underlying.typeToken)
    def f2t(f: From): Result[List[T]] = {
      val acc = List.newBuilder[T]
      @scala.annotation.tailrec
      def process(it: java.util.ListIterator[underlying.From]): Result[List[T]] = if (!it.hasNext) Right(acc.result)
      else {
        underlying.f2t(it.next()) match {
          case Right(h) =>
            acc += h; process(it)
          case Left(exc) => Left(exc)
        }
      }
      process(f.listIterator)
    }
    def extract(r: Row, name: String) = r getList (name, underlying.typeToken)
    def tupleExtract(tup: TupleValue, pos: Int) = tup getList (pos, underlying.typeToken)
  }

  def setOf[T](eltType: TypeToken[T]): TypeToken[java.util.Set[T]] =
    new TypeToken[java.util.Set[T]]() {}.where(new TypeParameter[T]() {}, eltType)

  implicit def setFormat[T](implicit underlying: CassFormatDecoder[T]): CassFormatDecoder[Set[T]] = new CollectionCassFormatDecoder[Set[T]] {
    type From = java.util.Set[underlying.From]
    val typeToken: TypeToken[java.util.Set[underlying.From]] = setOf(underlying.typeToken)
    def f2t(f: From): Result[Set[T]] = {
      val acc = Set.newBuilder[T]
      @scala.annotation.tailrec
      def process(it: java.util.Iterator[underlying.From]): Result[Set[T]] = if (!it.hasNext) Right(acc.result)
      else {
        underlying.f2t(it.next()) match {
          case Right(h) =>
            acc += h; process(it)
          case Left(exc) => Left(exc)
        }
      }
      process(f.iterator)
    }
    def extract(r: Row, name: String) = r getSet (name, underlying.typeToken)
    def tupleExtract(tup: TupleValue, pos: Int) = tup getSet (pos, underlying.typeToken)
  }

  def mapOf[K, V](keyType: TypeToken[K], valueType: TypeToken[V]): TypeToken[java.util.Map[K, V]] =
    new TypeToken[java.util.Map[K, V]]() {}
      .where(new TypeParameter[K]() {}, keyType)
      .where(new TypeParameter[V]() {}, valueType)

  implicit def mapFormat[K, V](implicit underlyingK: CassFormatDecoder[K], underlyingV: CassFormatDecoder[V]): CassFormatDecoder[Map[K, V]] =
    new CollectionCassFormatDecoder[Map[K, V]] {
      type From = java.util.Map[underlyingK.From, underlyingV.From]
      val typeToken = mapOf(underlyingK.typeToken, underlyingV.typeToken)
      def f2t(f: From): Result[Map[K, V]] = {
        val acc = Map.newBuilder[K, V]
        @scala.annotation.tailrec
        def process(it: java.util.Iterator[java.util.Map.Entry[underlyingK.From, underlyingV.From]]): Result[Map[K, V]] =
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
      def extract(r: Row, name: String) = r getMap (name, underlyingK.typeToken, underlyingV.typeToken)
      def tupleExtract(tup: TupleValue, pos: Int) = tup getMap (pos, underlyingK.typeToken, underlyingV.typeToken)
    }

  implicit val blobFormat: CassFormatDecoder[Array[Byte]] = new CassFormatDecoder[Array[Byte]] {
    type From = java.nio.ByteBuffer
    val typeToken = TypeToken.of(classOf[java.nio.ByteBuffer])

    def f2t(f: From) = Try(com.datastax.driver.core.utils.Bytes.getArray(f).toIndexedSeq.toArray).toEither
    def extract(r: Row, name: String): ByteBuffer = r getBytes name
    override def decode(r: Row, name: String): Result[Array[Byte]] = Try[Result[Array[Byte]]](
      if (r.isNull(name)) Left(new ValueNotDefinedException(s""""$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
      else {
        val cassName = r.getColumnDefinitions.getType(name).getName
        if (cassName != DataType.Name.BLOB)
          Left(new InvalidTypeException(s"Column $name is a $cassName, is not a blob"))
        else f2t(extract(r, name))
      }
    ).unwrap[Array[Byte]]
    def tupleExtract(tup: TupleValue, pos: Int): ByteBuffer = tup getBytes pos
    override def tupleDecode(tup: TupleValue, pos: Int): Result[Array[Byte]] = Try[Result[Array[Byte]]](
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
    val typeToken = underlying.typeToken
    def f2t(f: From): Result[Option[A]] = underlying.f2t(f).right.map(Option(_))
    def extract(r: Row, name: String) = underlying.extract(r, name)
    override def decode(r: Row, name: String): Result[Option[A]] = super.decode(r, name) match {
      case Left(Recoverable(_)) => Right(None)
      case other                => other
    }
    def tupleExtract(tup: TupleValue, pos: Int) = underlying.tupleExtract(tup, pos)
    override def tupleDecode(tup: TupleValue, pos: Int): Result[Option[A]] = super.tupleDecode(tup, pos) match {
      case Left(Recoverable(_)) => Right(None)
      case other                => other
    }
  }

  implicit def eitherFormat[A](implicit underlying: CassFormatDecoder[A]): CassFormatDecoder[Result[A]] = new CassFormatDecoder[Result[A]] {
    type From = underlying.From
    val typeToken = underlying.typeToken
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
