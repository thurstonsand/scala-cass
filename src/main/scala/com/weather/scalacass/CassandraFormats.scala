package com.weather.scalacass

import com.datastax.driver.core.Row
import com.datastax.driver.core.exceptions.InvalidTypeException
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.util.{Try, Success => TSuccess, Failure => TFailure}

trait CassFormat[T] {
  type From <: AnyRef
  val clazz: Class[From]
  val cassType: String
  def f2t(f: From): T
  def t2f(t: T): From
  def decode(r: Row, name: String): Either[Throwable, T]
}

trait LowPriorityCassFormat {
  private def tryDecode[T](r: Row, name: String, decode: (Row, String) => T) = Try[Either[Throwable, T]](
    if (r.isNull(name)) Left(new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}"""))
    else Right(decode(r, name))
  ) match {
      case TSuccess(v) => v
      case TFailure(e) => Left(e)
    }

  def sameTypeCassFormat[T <: AnyRef](_cassType: String, _clazz: Class[T], _decode: (Row, String) => T) = new CassFormat[T] {
    type From = T
    val cassType = _cassType
    val clazz = _clazz
    def f2t(f: T) = f
    def t2f(t: T) = t
    def decode(r: Row, name: String) = tryDecode(r, name, _decode)
  }
  implicit val stringFormat = sameTypeCassFormat[String](
    "varchar",
    classOf[String],
    _ getString _
  )
  implicit val uuidFormat = sameTypeCassFormat[java.util.UUID](
    "uuid",
    classOf[java.util.UUID],
    _ getUUID _
  )
  implicit val iNetFormat = sameTypeCassFormat[java.net.InetAddress](
    "inet",
    classOf[java.net.InetAddress],
    _ getInet _
  )

  def noConvertCassFormat[T, F <: AnyRef](_cassType: String, _clazz: Class[F], _t2f: T => F, _f2t: F => T, _decode: (Row, String) => T) = new CassFormat[T] {
    type From = F
    val cassType = _cassType
    val clazz = _clazz
    def t2f(t: T) = _t2f(t)
    def f2t(f: From) = _f2t(f)
    def decode(r: Row, name: String) = tryDecode(r, name, _decode)
  }
  implicit val intFormat = noConvertCassFormat[Int, java.lang.Integer](
    "int",
    classOf[java.lang.Integer],
    Int.box,
    Int.unbox,
    _ getInt _
  )

  implicit val longFormat = noConvertCassFormat[Long, java.lang.Long](
    "bigint",
    classOf[java.lang.Long],
    Long.box,
    Long.unbox,
    _ getLong _
  )
  implicit val booleanFormat = noConvertCassFormat[Boolean, java.lang.Boolean](
    "boolean",
    classOf[java.lang.Boolean],
    Boolean.box,
    Boolean.unbox,
    _ getBool _
  )
  implicit val doubleFormat = noConvertCassFormat[Double, java.lang.Double](
    "double",
    classOf[java.lang.Double],
    Double.box,
    Double.unbox,
    _ getDouble _
  )
  implicit val bigIntegerFormat = noConvertCassFormat[BigInt, java.math.BigInteger](
    "varint",
    classOf[java.math.BigInteger],
    _.underlying,
    BigInt.javaBigInteger2bigInt,
    _ getVarint _
  )
  implicit val bigDecimalFormat = noConvertCassFormat[BigDecimal, java.math.BigDecimal](
    "decimal",
    classOf[java.math.BigDecimal],
    _.underlying,
    BigDecimal.javaBigDecimal2bigDecimal,
    _ getDecimal _
  )
  implicit val floatFormat = noConvertCassFormat[Float, java.lang.Float](
    "float",
    classOf[java.lang.Float],
    Float.box,
    Float.unbox,
    _ getFloat _
  )

  def collectionCassFormat[T, F <: AnyRef](_cassType: String, _clazz: Class[F], _t2f: T => F, _f2t: F => T, _decode: (Row, String) => F) = new CassFormat[T] {
    type From = F
    val cassType = _cassType
    val clazz = _clazz
    def f2t(f: F) = _f2t(f)
    def t2f(t: T) = _t2f(t)
    def decode(r: Row, name: String) = tryDecode(r, name, (rr, nn) => _f2t(_decode(rr, nn)))
  }
  implicit def listFormat[T](implicit underlying: CassFormat[T]) = collectionCassFormat[List[T], java.util.List[underlying.From]](
    s"list<${underlying.cassType}>",
    classOf[java.util.List[underlying.From]],
    _.map(underlying.t2f(_)).asJava,
    _.asScala.map(underlying.f2t).toList,
    (r, name) => r.getList(name, underlying.clazz)
  )
  implicit def mapFormat[A, B](implicit underlyingA: CassFormat[A], underlyingB: CassFormat[B]) = collectionCassFormat[Map[A, B], java.util.Map[underlyingA.From, underlyingB.From]](
    s"map<${underlyingA.cassType}, ${underlyingB.cassType}>",
    classOf[java.util.Map[underlyingA.From, underlyingB.From]],
    _.map { case (k, v) => underlyingA.t2f(k) -> underlyingB.t2f(v) }.asJava,
    _.asScala.map { case (k, v) => underlyingA.f2t(k) -> underlyingB.f2t(v) }.toMap,
    (r, name) => r.getMap(name, underlyingA.clazz, underlyingB.clazz)
  )
  implicit def setFormat[A](implicit underlying: CassFormat[A]) = collectionCassFormat[Set[A], java.util.Set[underlying.From]](
    s"set<${underlying.cassType}>",
    classOf[java.util.Set[underlying.From]],
    _.map(underlying.t2f(_)).asJava,
    _.asScala.map(underlying.f2t).toSet,
    (r, name) => r.getSet(name, underlying.clazz)
  )

  implicit val dateTimeFormat = new CassFormat[DateTime] {
    type From = java.util.Date
    val cassType = "timestamp"
    val clazz = classOf[java.util.Date]
    def t2f(t: DateTime) = t.toDate
    def f2t(f: From) = new DateTime(f)
    def decode(r: Row, name: String) = tryDecode(r, name, (rr, nn) => f2t(r.getDate(name)))
  }

  implicit val blobFormat = new CassFormat[Array[Byte]] {
    type From = java.nio.ByteBuffer
    val clazz = classOf[From]
    val cassType = "blob"
    def f2t(f: From) = com.datastax.driver.core.utils.Bytes.getArray(f).toIndexedSeq.toArray
    def t2f(t: Array[Byte]) = java.nio.ByteBuffer.wrap(t)
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

  implicit def optionFormat[A](implicit underlying: CassFormat[A]) = new CassFormat[Option[A]] {
    type From = Option[underlying.From]
    val clazz = classOf[From]
    val cassType = underlying.cassType
    def f2t(f: From) = f.map(underlying.f2t)
    def t2f(f: Option[A]) = f.map(underlying.t2f(_))
    def decode(r: Row, name: String) = Try(
      if (r.isNull(name)) None
      else underlying.decode(r, name).right.toOption
    ) match {
        case TSuccess(v) => Right(v)
        case TFailure(_) => Right(None)
      }
  }
}

object CassFormat extends LowPriorityCassFormat
