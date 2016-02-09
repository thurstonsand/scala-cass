package com.weather.scalacass

import com.datastax.driver.core.Row

object ScalaCass {
  type CassFormat[T] = CassandraFormats.CassFormat[T]
  type CCCassFormat[T] = ShapelessCassandraFormats.CCCassFormat[T]

  private implicit class RichEither[+A <: Throwable, +B](val e: Either[A, B]) extends AnyVal {
    def getOrThrow = e match {
      case Right(v) => v
      case Left(exc) => throw exc
    }
    def getRightOpt = e.right.toOption
  }

  implicit class RichRow(val r: Row) extends AnyVal {
    def as[T: CassFormat](name: String): T = implicitly[CassFormat[T]].decode(r, name).getOrThrow
    def getAs[T: CassFormat](name: String): Option[T] = implicitly[CassFormat[T]].decode(r, name).getRightOpt
    def getOrElse[T: CassFormat](name: String, default: => T): T = getAs[T](name).getOrElse(default)

    def as[T](implicit f: CCCassFormat[T]): T = f.decode(r).getOrThrow
    def getAs[T](implicit f: CCCassFormat[T]): Option[T] = f.decode(r).getRightOpt
    def getOrElse[T](default: => T)(implicit f: CCCassFormat[T]): T = getAs[T].getOrElse(default)
  }

  // Native Cassandra Types
  trait LowPriorityRichRow {
    implicit val stringFormat     = CassandraFormats.stringFormat
    implicit val intFormat        = CassandraFormats.intFormat
    implicit val longFormat       = CassandraFormats.longFormat
    implicit val booleanFormat    = CassandraFormats.booleanFormat
    implicit val doubleFormat     = CassandraFormats.doubleFormat
    implicit val dateTimeFormat   = CassandraFormats.dateTimeFormat
    implicit val uuidFormat       = CassandraFormats.uuidFormat
    implicit val iNetFormat       = CassandraFormats.iNetFormat
    implicit val bigDecimalFormat = CassandraFormats.bigDecimalFormat
    implicit val floatFormat      = CassandraFormats.floatFormat
    implicit val blobFormat       = CassandraFormats.blobFormat

    implicit def listFormat[T: CassFormat]             = CassandraFormats.listFormat[T]
    implicit def mapFormat[A: CassFormat, B: CassFormat] = CassandraFormats.mapFormat[A, B]
    implicit def setFormat[A: CassFormat]              = CassandraFormats.setFormat[A]
    implicit def optionFormat[A: CassFormat]           = CassandraFormats.optionFormat[A]
  }
  object RichRow extends LowPriorityRichRow {
    // Case class derivation via Shapeless
    import shapeless.{Lazy, Witness, HList, LabelledGeneric}
    implicit val hNilFormat = ShapelessCassandraFormats.hNilDecoder
    implicit def hConsFormat[K <: Symbol, H, T <: HList](implicit
      w: Witness.Aux[K], tdH: Lazy[CassFormat[H]], tdT: Lazy[CCCassFormat[T]]
    ) = ShapelessCassandraFormats.hConsDecoder[K, H, T](w, tdH, tdT)
    implicit def hListConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], sg: Lazy[CCCassFormat[Repr]]) =
      ShapelessCassandraFormats.hListConverter[T, Repr]
  }

}


