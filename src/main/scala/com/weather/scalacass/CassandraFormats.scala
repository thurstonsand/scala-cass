package com.weather.scalacass

import com.datastax.driver.core.Row
import com.datastax.driver.core.exceptions.InvalidTypeException
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.util.{Try, Success => TSuccess, Failure => TFailure}

object CassandraFormats {
  def try2Either[T](fn: => T) = Try(fn) match {
    case TSuccess(v) => Right(v)
    case TFailure(e) => Left(e)
  }
  trait CassFormat[T] {
    type From
    val clazz: Class[From]
    def convert(f: From): T
    def decodeNoNullCheck(r: Row, name: String): T
    def decode(r: Row, name: String): Either[Throwable, T] = try2Either(
      if (r.isNull(name)) throw new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}""")
      else decodeNoNullCheck(r, name))
  }
  trait BasicCassFormat[T] extends CassFormat[T] {
    protected def decodeFrom(r: Row, name: String): From
    override def decodeNoNullCheck(r: Row, name: String): T = convert(decodeFrom(r, name))
  }
  trait NoConvertCassFormat[T] extends BasicCassFormat[T] {
    type From = T
    def convert(f: T) = f
  }

  val stringFormat = new NoConvertCassFormat[String] {
    val clazz = classOf[From]
    def decodeFrom(r: Row, name: String) = r.getString(name)
  }
  val intFormat = new CassFormat[Int] {
    type From = java.lang.Integer
    val clazz = classOf[From]
    def convert(f: From) = Int.unbox(f)
    def decodeNoNullCheck(r: Row, name: String) = r.getInt(name)
  }
  val longFormat = new CassFormat[Long] {
    type From = java.lang.Long
    val clazz = classOf[From]
    def convert(f: From) = Long.unbox(f)
    def decodeNoNullCheck(r: Row, name: String) = r.getLong(name)
  }
  val booleanFormat = new CassFormat[Boolean] {
    type From = java.lang.Boolean
    val clazz = classOf[From]
    def convert(f: From) = Boolean.unbox(f)
    def decodeNoNullCheck(r: Row, name: String) = r.getBool(name)
  }
  val doubleFormat = new CassFormat[Double] {
    type From = java.lang.Double
    val clazz = classOf[From]
    def convert(f: From) = Double.unbox(f)
    def decodeNoNullCheck(r: Row, name: String) = r.getDouble(name)
  }
  val dateTimeFormat = new CassFormat[DateTime] {
    type From = java.util.Date
    val clazz = classOf[From]
    def convert(f: From) = new DateTime(f)
    def decodeNoNullCheck(r: Row, name: String) = convert(r.getDate(name))
  }
  val uuidFormat = new NoConvertCassFormat[java.util.UUID] {
    val clazz = classOf[From]
    def decodeFrom(r: Row, name: String) = r.getUUID(name)
  }
  val iNetFormat = new NoConvertCassFormat[java.net.InetAddress] {
    val clazz = classOf[From]
    def decodeFrom(r: Row, name: String) = r.getInet(name)
  }
  val bigDecimalFormat = new BasicCassFormat[BigDecimal] {
    type From = java.math.BigDecimal
    val clazz = classOf[From]
    def convert(f: From) = BigDecimal.javaBigDecimal2bigDecimal(f)
    def decodeFrom(r: Row, name: String) = r.getDecimal(name)
  }
  val floatFormat = new CassFormat[Float] {
    type From = java.lang.Float
    val clazz = classOf[From]
    def convert(f: From) = Float.unbox(f)
    def decodeNoNullCheck(r: Row, name: String) = r.getFloat(name)
  }
  val blobFormat = new BasicCassFormat[Array[Byte]] {
    type From = java.nio.ByteBuffer
    val clazz = classOf[From]
    def convert(f: From) = com.datastax.driver.core.utils.Bytes.getArray(f).toIndexedSeq.toArray
    def decodeFrom(r: Row, name: String) = {
      val cassClass = r.getColumnDefinitions.getType(name).asJavaClass
      if (cassClass != clazz)
        throw new InvalidTypeException(s"Column $name is a $cassClass, cannot be retrieved as an Array[Byte]")
      else r.getBytes(name)
    }
  }

  def listFormat[T](implicit underlying: CassFormat[T]) = new BasicCassFormat[List[T]] {
    type From = java.util.List[underlying.From]
    val clazz = classOf[From]
    def convert(f: From): List[T] = f.asScala.map(underlying.convert).toList
    def decodeFrom(r: Row, name: String) = r.getList(name, underlying.clazz)
  }

  def mapFormat[A, B](implicit underlyingA: CassFormat[A], underlyingB: CassFormat[B]) = new BasicCassFormat[Map[A, B]] {
    type From = java.util.Map[underlyingA.From, underlyingB.From]
    val clazz = classOf[From]
    def convert(f: From) = f.asScala.map{ case (k ,v) => underlyingA.convert(k) -> underlyingB.convert(v)}.toMap
    def decodeFrom(r: Row, name: String) = r.getMap(name, underlyingA.clazz, underlyingB.clazz)
  }
  def setFormat[A](implicit underlying: CassFormat[A]) = new BasicCassFormat[Set[A]] {
    type From = java.util.Set[underlying.From]
    val clazz = classOf[From]
    def convert(f: From) = f.asScala.map(underlying.convert).toSet
    def decodeFrom(r: Row, name: String) = r.getSet(name, underlying.clazz)
  }
  def optionFormat[A](implicit underlying: CassFormat[A]) = new CassFormat[Option[A]] {
    type From = Option[underlying.From]
    val clazz = classOf[From]
    def convert(f: From) = f.map(underlying.convert)
    def decodeNoNullCheck(r: Row, name: String) = throw new NotImplementedError("already handling null")
    override def decode(r: Row, name: String) = Right(
      if (!r.getColumnDefinitions.contains(name)) None
      else if (r.isNull(name)) None
      else underlying.decode(r, name).right.toOption
    )
  }
}