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
    private def getForTypeOpt[T: TypeTag](name: String): Option[T] = Try(getForType[T](name)).toOption.flatMap(Option(_))
    private def getForType[T: TypeTag](name: String): T = (typeOf[T] match {
      case t if t <:< typeOf[Map[_, _]] =>
        val List(arg1, arg2) = t.typeArgs
        r.getMap(name, getRTClass(arg1), getRTClass(arg2)).asScala.map { case (k, v) =>
          convertToScala(k) -> convertToScala(v)
        }.toMap
      case t if t <:< typeOf[List[_]] =>
        val List(arg) = t.typeArgs
        r.getList(name, getRTClass(arg)).asScala.map(convertToScala).toList
      case t if t <:< typeOf[Set[_]] =>
        val List(arg) = t.typeArgs
        r.getSet(name, getRTClass(arg)).asScala.map(convertToScala).toSet
      case t =>
        val funcArg = getRTClass(t)
        val cassArg = r.getColumnDefinitions.getType(name).asJavaClass()
        if (funcArg != cassArg)
          throw new InvalidTypeException(s"Column $name is a $cassArg, cannot be retrieved as a $funcArg")
        else convertToScala(r.getObject(name))
    }).asInstanceOf[T]
    private def getRTClass(t: Type): Class[_] = t match {
      case _ if t =:= typeOf[Long]        => classOf[java.lang.Long]
      case _ if t =:= typeOf[Int]         => classOf[java.lang.Integer]
      case _ if t =:= typeOf[Float]       => classOf[java.lang.Float]
      case _ if t =:= typeOf[Double]      => classOf[java.lang.Double]
      case _ if t =:= typeOf[Boolean]     => classOf[java.lang.Boolean]
      case _ if t =:= typeOf[DateTime]    => classOf[java.util.Date]
      case _ if t =:= typeOf[BigDecimal]  => classOf[java.math.BigDecimal]
      case _ if t =:= typeOf[Array[Byte]] => classOf[java.nio.ByteBuffer]
//      case _ if t =:= typeOf[String]      => classOf[String] // optional if runtimeClass is super slow
      case _                              => currentMirror.runtimeClass(t)
    }
    private def convertToScala[T: TypeTag](o: T): Any = o match {
      case o: java.nio.ByteBuffer  => com.datastax.driver.core.utils.Bytes.getArray(o).toIndexedSeq.toArray
      case o: java.math.BigDecimal => BigDecimal.javaBigDecimal2bigDecimal(o)
      case o: java.util.Date       => new DateTime(o)
      case _                       => o
    }

    def as[T: TypeTag](name: String): T =
      if (r.isNull(name)) throw new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}""")
      else getForType[T](name)
    def getAs[T: TypeTag](name: String): Option[T] =
      if(!r.getColumnDefinitions.contains(name)) None
      else getForTypeOpt[T](name)
  }
}
