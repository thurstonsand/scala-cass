package com.weather.scalacass

import com.datastax.driver.core.Row
import com.datastax.driver.core.exceptions.InvalidTypeException
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._
import scala.util.{Try, Success => TSuccess, Failure => TFailure}

object TypeClasses {
  trait RowDecoder[T] {
    def decode(r: Row, name: String): Either[Throwable, T]
  }

  def rowDecoder[T](_decode: (Row, String) => T) = new RowDecoder[T] {
    def decode(r: Row, name: String) = Try(_decode(r, name)) match {
      case TSuccess(v) => Right(v)
      case TFailure(e) => Left(e)
    }
  }

  def getCassClass(t: Type): Class[_] = t match {
    case _ if t =:= typeOf[Long]        => classOf[java.lang.Long]
    case _ if t =:= typeOf[Int]         => classOf[java.lang.Integer]
    case _ if t =:= typeOf[Float]       => classOf[java.lang.Float]
    case _ if t =:= typeOf[Double]      => classOf[java.lang.Double]
    case _ if t =:= typeOf[Boolean]     => classOf[java.lang.Boolean]
    case _ if t =:= typeOf[DateTime]    => classOf[java.util.Date]
    case _ if t =:= typeOf[BigDecimal]  => classOf[java.math.BigDecimal]
    case _ if t =:= typeOf[Array[Byte]] => classOf[java.nio.ByteBuffer]
    case _ if t =:= typeOf[String]      => classOf[String]
    case _                              => currentMirror.runtimeClass(t)
  }
  def convertToScala(o: Any): Any = o match {
    case o: java.nio.ByteBuffer  => com.datastax.driver.core.utils.Bytes.getArray(o).toIndexedSeq.toArray
    case o: java.math.BigDecimal => BigDecimal.javaBigDecimal2bigDecimal(o)
    case o: java.util.Date       => new DateTime(o)
    case _                       => o
  }

  implicit val stringDecoder = rowDecoder[String]((r, name) => r.getString(name))
  implicit val intDecoder = rowDecoder[Int]((r, name) => r.getInt(name))
  implicit val longDecoder = rowDecoder[Long]((r, name) => r.getLong(name))
  implicit val booleanDecoder = rowDecoder[Boolean]((r, name) => r.getBool(name))
  implicit val doubleDecoder = rowDecoder[Double]((r, name) => r.getDouble(name))
  implicit val dateTimeDecoder = rowDecoder[DateTime]((r, name) => convertToScala(r.getDate(name)).asInstanceOf[DateTime])
  implicit val uuidDecoder = rowDecoder[java.util.UUID]((r, name) => r.getUUID(name))
  implicit val iNetDecoder = rowDecoder[java.net.InetAddress]((r, name) => r.getInet(name))
  implicit val bigDecimalDecoder = rowDecoder[BigDecimal]((r, name) => convertToScala(r.getDecimal(name)).asInstanceOf[BigDecimal])
  implicit val floatDecoder = rowDecoder[Float]((r, name) => r.getFloat(name))
  implicit val blobDecoder = rowDecoder[Array[Byte]]((r, name) => {
    val cassType = r.getColumnDefinitions.getType(name).asJavaClass
    if (cassType != classOf[java.nio.ByteBuffer])
      throw new InvalidTypeException(s"Column $name is a $cassType, cannot be retrieved as an Array[Byte]")
    else convertToScala(r.getBytes(name)).asInstanceOf[Array[Byte]]
  })

  implicit def listDecoder[A: TypeTag] = rowDecoder[List[A]]((r, name) =>
    r.getList(name, getCassClass(typeOf[A])).asScala.map(convertToScala).toList.asInstanceOf[List[A]])
  implicit def mapDecoder[A: TypeTag, B: TypeTag] = rowDecoder[Map[A, B]]((r, name) => {
    r.getMap(name, getCassClass(typeOf[A]), getCassClass(typeOf[B])).asScala.map { case (k, v) => convertToScala(k) -> convertToScala(v) }.toMap.asInstanceOf[Map[A, B]]
  })
  implicit def setDecoder[A: TypeTag] = rowDecoder[Set[A]]((r, name) => r.getSet(name, getCassClass(typeOf[A])).asScala.map(convertToScala).toSet.asInstanceOf[Set[A]])
  implicit def optionDecoder[A: TypeTag: RowDecoder] = rowDecoder[Option[A]]((r, name) =>
    if (!r.getColumnDefinitions.contains(name)) None
    else if (r.isNull(name)) None
    else implicitly[RowDecoder[A]].decode(r, name).right.toOption
  )
}