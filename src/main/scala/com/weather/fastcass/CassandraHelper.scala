package com.weather.fastcass

import com.datastax.driver.core.Row
import com.datastax.driver.core.exceptions.InvalidTypeException

import org.joda.time.DateTime
import scala.collection.JavaConverters._
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._
import scala.util.Try

object CassandraHelper {
  implicit class RichRow(r: Row) {
    def as[T: TypeTag : RowDecoder](name: String): T =
      if (r.isNull(name)) throw new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}""")
      else parseRow[T](name)
    def getAs[T: TypeTag : RowDecoder](name: String): Option[T] =
      if(!r.getColumnDefinitions.contains(name)) None
      else Try(parseRow[T](name)).toOption.flatMap(Option.apply)
    def getOrElse[T: TypeTag : RowDecoder](name: String, default: T): T = getAs[T](name).getOrElse(default)

    def parseRow[A](name: String)(implicit da: RowDecoder[A]) = da.decode(r, name)
  }

  private def getCassClass(t: Type): Class[_] = t match {
    case _ if t =:= typeOf[Long]        => classOf[java.lang.Long]
    case _ if t =:= typeOf[Int]         => classOf[java.lang.Integer]
    case _ if t =:= typeOf[Float]       => classOf[java.lang.Float]
    case _ if t =:= typeOf[Double]      => classOf[java.lang.Double]
    case _ if t =:= typeOf[Boolean]     => classOf[java.lang.Boolean]
    case _ if t =:= typeOf[DateTime]    => classOf[java.util.Date]
    case _ if t =:= typeOf[BigDecimal]  => classOf[java.math.BigDecimal]
    case _ if t =:= typeOf[Array[Byte]] => classOf[java.nio.ByteBuffer]
//          case _ if t =:= typeOf[String]      => classOf[String] // optional if runtimeClass is super slow
    case _                              => currentMirror.runtimeClass(t)
  }
  private def convertToScala[T](o: T): Any = o match {
    case o: java.nio.ByteBuffer  => com.datastax.driver.core.utils.Bytes.getArray(o).toIndexedSeq.toArray
    case o: java.math.BigDecimal => BigDecimal.javaBigDecimal2bigDecimal(o)
    case o: java.util.Date       => new DateTime(o)
    case _                       => o
  }

  trait RowDecoder[T] {
    def decode(r: Row, name: String): T
  }
  implicit val stringDecoder = new RowDecoder[String] {
    def decode(r: Row, name: String) = r.getString(name)
  }
  implicit val intDecoder = new RowDecoder[Int] {
    def decode(r: Row, name: String) = r.getInt(name)
  }
  implicit val LongDecoder = new RowDecoder[Long] {
    def decode(r: Row, name: String) = r.getLong(name)
  }
  implicit val booleanDecoder = new RowDecoder[Boolean] {
    def decode(r: Row, name: String) = r.getBool(name)
  }
  implicit val doubleDecoder = new RowDecoder[Double] {
    def decode(r: Row, name: String) = r.getDouble(name)
  }
  implicit val dateTimeDecoder = new RowDecoder[DateTime] {
    def decode(r: Row, name: String) = convertToScala(r.getDate(name)).asInstanceOf[DateTime]
  }
  implicit val uuidDecoder = new RowDecoder[java.util.UUID] {
    def decode(r: Row, name: String) = r.getUUID(name)
  }
  implicit val blobDecoder = new RowDecoder[Array[Byte]] {
    def decode(r: Row, name: String) = {
      val cassType = r.getColumnDefinitions.getType(name).asJavaClass
      if (cassType != classOf[java.nio.ByteBuffer])
        throw new InvalidTypeException(s"Column $name is a $cassType, cannot be retrieved as a Byte[Array]")
      else convertToScala(r.getBytes(name)).asInstanceOf[Array[Byte]]
    }
  }
  implicit val INetDecoder = new RowDecoder[java.net.InetAddress] {
    def decode(r: Row, name: String) = r.getInet(name)
  }
  implicit val bigDecimalDecoder = new RowDecoder[BigDecimal] {
    def decode(r: Row, name: String) = BigDecimal.javaBigDecimal2bigDecimal(r.getDecimal(name))
  }
  implicit val floatDecoder = new RowDecoder[Float] {
    def decode(r: Row, name: String) = r.getFloat(name)
  }
  implicit def listDecoder[A: TypeTag] = new RowDecoder[List[A]] {
    def decode(r: Row, name: String) = r.getList(name, getCassClass(typeOf[A])).asScala.map(convertToScala).toList.asInstanceOf[List[A]]
  }
  implicit def mapDecoder[A: TypeTag, B: TypeTag] = new RowDecoder[Map[A, B]] {
    def decode(r: Row, name: String) = r.getMap(name, getCassClass(typeOf[A]), getCassClass(typeOf[B])).asScala.map { case (k, v) =>
      convertToScala(k) -> convertToScala(v)
    }.toMap.asInstanceOf[Map[A, B]]
  }
  implicit def setDecoder[A: TypeTag] = new RowDecoder[Set[A]] {
    def decode(r: Row, name: String) = r.getSet(name, getCassClass(typeOf[A])).asScala.map(convertToScala).toSet.asInstanceOf[Set[A]]
  }
}
