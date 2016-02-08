package com.weather.scalacass

import com.datastax.driver.core.Row
import scala.reflect.runtime.universe.TypeTag
import scala.util.Try
//import TypeClasses.RowDecoder
//import ShapelessTypeClasses.CCRowDecoder

object ScalaCass {
  type RowDecoder[T] = TypeClasses.RowDecoder[T]
  type CCRowDecoder[T] = ShapelessTypeClasses.CCRowDecoder[T]
  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T: TypeTag : RowDecoder](name: String): T =
      if (r.isNull(name)) throw new IllegalArgumentException(s"""Cassandra: "$name" was not defined in ${r.getColumnDefinitions.getTable(name)}""")
      else parseRow[T](name)
    def getAs[T: TypeTag](name: String)(implicit od: RowDecoder[Option[T]]): Option[T] = parseRow[Option[T]](name)
    def getOrElse[T: TypeTag](name: String, default: => T)(implicit od: RowDecoder[Option[T]]): T = getAs[T](name).getOrElse(default)

    private def parseRow[A: RowDecoder](name: String) = implicitly[RowDecoder[A]].decode(r, name)

    def as[T](implicit f: CCRowDecoder[T]): T = f.decode(r)
    def getAs[T](implicit f: CCRowDecoder[T]): Option[T] = Try(f.decode(r)).toOption
    def getOrElse[T](default: => T)(implicit f: CCRowDecoder[T]): T = getAs[T].getOrElse(default)
  }

  // Native Cassandra Types
  implicit val stringDecoder     = TypeClasses.stringDecoder
  implicit val intDecoder        = TypeClasses.intDecoder
  implicit val longDecoder       = TypeClasses.longDecoder
  implicit val booleanDecoder    = TypeClasses.booleanDecoder
  implicit val doubleDecoder     = TypeClasses.doubleDecoder
  implicit val dateTimeDecoder   = TypeClasses.dateTimeDecoder
  implicit val uuidDecoder       = TypeClasses.uuidDecoder
  implicit val iNetDecoder       = TypeClasses.iNetDecoder
  implicit val bigDecimalDecoder = TypeClasses.bigDecimalDecoder
  implicit val floatDecoder      = TypeClasses.floatDecoder
  implicit val blobDecoder       = TypeClasses.blobDecoder

  implicit def listDecoder[A: TypeTag]               = TypeClasses.listDecoder[A]
  implicit def mapDecoder[A: TypeTag, B: TypeTag]    = TypeClasses.mapDecoder[A, B]
  implicit def setDecoder[A: TypeTag]                = TypeClasses.setDecoder[A]
  implicit def optionDecoder[A: TypeTag: RowDecoder] = TypeClasses.optionDecoder[A]

  // Case class derivation via Shapeless
  import shapeless.{Lazy, Witness, HList, LabelledGeneric}
  implicit val hNilDecoder = ShapelessTypeClasses.HNilDecoder
  implicit def HConsDecoder[K <: Symbol, H, T <: HList](implicit
    w: Witness.Aux[K], tdH: Lazy[RowDecoder[H]], tdT: Lazy[CCRowDecoder[T]]) =
    ShapelessTypeClasses.HConsDecoder[K, H, T](w, tdH, tdT)
  implicit def HListConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], sg: Lazy[CCRowDecoder[Repr]]) =
    ShapelessTypeClasses.HListConverter[T, Repr]
}


