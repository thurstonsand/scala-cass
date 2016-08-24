package com.weather.scalacass

import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.language.higherKinds

trait CassFormatEncoder[F] { self =>
  type To <: AnyRef
  val cassType: String
  def encode(f: F): Either[Throwable, To]
  def map[G](fn: G => F): CassFormatEncoder[G] = new CassFormatEncoder[G] {
    type To = self.To
    val cassType = self.cassType
    def encode(f: G): Either[Throwable, To] = self.encode(fn(f))
  }

  def flatMap[G](fn: G => Either[Throwable, F]): CassFormatEncoder[G] = new CassFormatEncoder[G] {
    type To = self.To
    val cassType = self.cassType
    def encode(f: G): Either[Throwable, To] = fn(f).right.flatMap(self.encode)
  }
}

object CassFormatEncoder extends CassFormatEncoderVersionSpecific {
  type Aux[F, To0] = CassFormatEncoder[F] { type To = To0 }
  def apply[T: CassFormatEncoder] = implicitly[CassFormatEncoder[T]]

  private[scalacass] def sameTypeCassFormatEncoder[F <: AnyRef](_cassType: String): CassFormatEncoder[F] = new CassFormatEncoder[F] {
    type To = F
    val cassType = _cassType
    def encode(f: F) = Right(f)
  }
  private[scalacass] def transCassFormatEncoder[F, T <: AnyRef](_cassType: String, _encode: F => T): CassFormatEncoder[F] = new CassFormatEncoder[F] {
    type To = T
    val cassType = _cassType
    def encode(f: F) = Right(_encode(f))
  }

  // encoders

  implicit val stringFormat = sameTypeCassFormatEncoder[String]("varchar")
  implicit val uuidFormat = sameTypeCassFormatEncoder[java.util.UUID]("uuid")
  implicit val iNetFormat = sameTypeCassFormatEncoder[java.net.InetAddress]("inet")

  implicit val intFormat = transCassFormatEncoder("int", Int.box)
  implicit val longFormat = transCassFormatEncoder("bigint", Long.box)
  implicit val booleanFormat = transCassFormatEncoder("boolean", Boolean.box)
  implicit val doubleFormat = transCassFormatEncoder("double", Double.box)
  implicit val bigIntegerFormat = transCassFormatEncoder("varint", (_: BigInt).underlying)
  implicit val bigDecimalFormat = transCassFormatEncoder("decimal", (_: BigDecimal).underlying)
  implicit val floatFormat = transCassFormatEncoder("float", Float.box)
  implicit val dateTimeFormat = transCassFormatEncoder("timestamp", (_: DateTime).toDate)
  implicit val blobFormat = transCassFormatEncoder("blob", java.nio.ByteBuffer.wrap)

  def containerCassFormatEncoder[Coll[_], F, JColl[_] <: java.util.Collection[_], T <: AnyRef](_cassType: String, _encode: F => Either[Throwable, T], lb2JColl: ListBuffer[T] => JColl[T])(implicit ev: Coll[F] <:< Iterable[F]) =
    new CassFormatEncoder[Coll[F]] {
      type To = JColl[T]
      val cassType = _cassType
      def encode(f: Coll[F]) = {
        val acc = ListBuffer.empty[T]
        @scala.annotation.tailrec
        def process(l: Iterable[F]): Either[Throwable, JColl[T]] = l.headOption.map(_encode) match {
          case Some(Left(ff)) => Left(ff)
          case Some(Right(n)) =>
            acc += n
            process(l.tail)
          case None => Right(lb2JColl(acc))
        }
        process(f)
      }
    }

  implicit def listFormat[A](implicit underlying: CassFormatEncoder[A]) = containerCassFormatEncoder[List, A, java.util.List, underlying.To](s"list<${underlying.cassType}>", underlying.encode(_), _.asJava)
  implicit def setFormat[A](implicit underlying: CassFormatEncoder[A]) = containerCassFormatEncoder[Set, A, java.util.Set, underlying.To](s"set<${underlying.cassType}>", underlying.encode(_), _.toSet.asJava)
  implicit def mapFormat[A, B](implicit underlyingA: CassFormatEncoder[A], underlyingB: CassFormatEncoder[B]) =
    new CassFormatEncoder[Map[A, B]] {
      type To = java.util.Map[underlyingA.To, underlyingB.To]
      val cassType = s"map<${underlyingA.cassType}, ${underlyingB.cassType}>"
      def encode(f: Map[A, B]): Either[Throwable, java.util.Map[underlyingA.To, underlyingB.To]] = {
        val acc = ListBuffer.empty[(underlyingA.To, underlyingB.To)]
        @scala.annotation.tailrec
        def process(l: Iterable[(A, B)]): Either[Throwable, java.util.Map[underlyingA.To, underlyingB.To]] = l.headOption.map {
          case (k, v) => for {
            kk <- underlyingA.encode(k).right
            vv <- underlyingB.encode(v).right
          } yield (kk, vv)
        } match {
          case Some(Left(ff)) => Left(ff)
          case Some(Right(n)) =>
            acc += n
            process(l.tail)
          case None => Right(acc.toMap.asJava)
        }
        process(f)
      }
    }

  implicit def optionFormat[A](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[Option[A]] {
    type To = Option[underlying.To]
    val cassType = underlying.cassType
    def encode(f: Option[A]): Either[Throwable, Option[underlying.To]] = f.map(underlying.encode(_)) match {
      case None           => Right(None)
      case Some(Left(_))  => Right(None)
      case Some(Right(n)) => Right(Some(n))
    }
  }
  implicit def eitherFormat[A](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[Either[Throwable, A]] {
    type To = Either[Throwable, underlying.To]
    val cassType = underlying.cassType
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Product", "org.brianmckenna.wartremover.warts.Serializable"))
    def encode(f: Either[Throwable, A]): Either[Throwable, Either[Throwable, underlying.To]] = f.right.map(underlying.encode(_)) match {
      case Left(ff) => Right(Left(ff))
      case other    => other
    }
  }

  implicit val nothingFormat = new CassFormatEncoder[Nothing] {
    type To = Nothing
    val cassType = ""
    def encode(f: Nothing): Either[Throwable, To] = throw new IllegalArgumentException("Nothing isn't a real type!")
  }
}