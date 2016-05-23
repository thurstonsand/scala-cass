package com.weather.scalacass

import org.joda.time.DateTime
import scala.collection.JavaConverters._

trait CassFormatEncoder[F] { self =>
  type To <: AnyRef
  val cassType: String
  def encode(f: F): To
  def map[G](fn: G => F) = new CassFormatEncoder[G] {
    type To = self.To
    val cassType = self.cassType
    def encode(f: G): To = self.encode(fn(f))
  }
}

trait LowPriorityCassFormatEncoder {
  def sameTypeCassFormatEncoder[F <: AnyRef](_cassType: String) = new CassFormatEncoder[F] {
    type To = F
    val cassType = _cassType
    def encode(f: F) = f
  }
  implicit val stringFormat = sameTypeCassFormatEncoder[String]("varchar")
  implicit val uuidFormat = sameTypeCassFormatEncoder[java.util.UUID]("uuid")
  implicit val iNetFormat = sameTypeCassFormatEncoder[java.net.InetAddress]("inet")

  def transCassFormatEncoder[F, T <: AnyRef](_cassType: String, _f2t: F => T) = new CassFormatEncoder[F] {
    type To = T
    val cassType = _cassType
    def encode(f: F) = _f2t(f)
  }
  implicit val intFormat = transCassFormatEncoder("int", Int.box)
  implicit val longFormat = transCassFormatEncoder("bigint", Long.box)
  implicit val booleanFormat = transCassFormatEncoder("boolean", Boolean.box)
  implicit val doubleFormat = transCassFormatEncoder("double", Double.box)
  implicit val bigIntegerFormat = transCassFormatEncoder("varint", (_: BigInt).underlying)
  implicit val bigDecimalFormat = transCassFormatEncoder("decimal", (_: BigDecimal).underlying)
  implicit val floatFormat = transCassFormatEncoder("float", Float.box)
  implicit val dateTimeFormat = transCassFormatEncoder("timestamp", (_: DateTime).toDate)
  implicit val blobFormat = transCassFormatEncoder("blob", java.nio.ByteBuffer.wrap)

  implicit def listFormat[A](implicit underlying: CassFormatEncoder[A]) = transCassFormatEncoder(s"list<${underlying.cassType}>", (_: List[A]).map(underlying.encode(_)).asJava)
  implicit def mapFormat[A, B](implicit underlyingA: CassFormatEncoder[A], underlyingB: CassFormatEncoder[B]) =
    transCassFormatEncoder(s"map<${underlyingA.cassType}, ${underlyingB.cassType}>", (_: Map[A, B]).map { case (k, v) => underlyingA.encode(k) -> underlyingB.encode(v) }.asJava)
  implicit def setFormat[A](implicit underlying: CassFormatEncoder[A]) = transCassFormatEncoder(s"set<${underlying.cassType}>", (_: Set[A]).map(underlying.encode(_)).asJava)
  implicit def optionFormat[A](implicit underlying: CassFormatEncoder[A]) = transCassFormatEncoder(underlying.cassType, (_: Option[A]).map(underlying.encode(_)))
}

object CassFormatEncoder extends LowPriorityCassFormatEncoder {
  type Aux[F, To0] = CassFormatEncoder[F] { type To = To0 }
}