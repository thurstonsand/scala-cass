package com.weather.scalacass

import com.datastax.driver.core.DataType

trait CassFormatEncoder[F] { self =>
  type To <: AnyRef
  def cassDataType: DataType
  def cassType: String = cassDataType.toString
  def encode(f: F): Either[Throwable, To]
  def map[G](fn: G => F): CassFormatEncoder[G] = new CassFormatEncoder[G] {
    type To = self.To
    val cassDataType = self.cassDataType
    def encode(f: G): Either[Throwable, To] = self.encode(fn(f))
  }

  def flatMap[G](fn: G => Either[Throwable, F]): CassFormatEncoder[G] = new CassFormatEncoder[G] {
    type To = self.To
    val cassDataType = self.cassDataType
    def encode(f: G): Either[Throwable, To] = fn(f).right.flatMap(self.encode)
  }
}

object CassFormatEncoder extends CassFormatEncoderVersionSpecific {
  type Aux[F, To0] = CassFormatEncoder[F] { type To = To0 }
  def apply[T: CassFormatEncoder] = implicitly[CassFormatEncoder[T]]

  private[scalacass] def sameTypeCassFormatEncoder[F <: AnyRef](_cassDataType: DataType): CassFormatEncoder[F] = new CassFormatEncoder[F] {
    type To = F
    val cassDataType = _cassDataType
    def encode(f: F) = Right(f)
  }
  private[scalacass] def transCassFormatEncoder[F, T <: AnyRef](_cassDataType: DataType, _encode: F => T): CassFormatEncoder[F] = new CassFormatEncoder[F] {
    type To = T
    val cassDataType = _cassDataType
    def encode(f: F) = Right(_encode(f))
  }

  // encoders

  implicit val stringFormat = sameTypeCassFormatEncoder[String](DataType.varchar)
  implicit val uuidFormat = sameTypeCassFormatEncoder[java.util.UUID](DataType.uuid)
  implicit val iNetFormat = sameTypeCassFormatEncoder[java.net.InetAddress](DataType.inet)

  implicit val intFormat = transCassFormatEncoder(DataType.cint, Int.box)
  implicit val longFormat = transCassFormatEncoder(DataType.bigint, Long.box)
  implicit val booleanFormat = transCassFormatEncoder(DataType.cboolean, Boolean.box)
  implicit val doubleFormat = transCassFormatEncoder(DataType.cdouble, Double.box)
  implicit val bigIntegerFormat = transCassFormatEncoder(DataType.varint, (_: BigInt).underlying)
  implicit val bigDecimalFormat = transCassFormatEncoder(DataType.decimal, (_: BigDecimal).underlying)
  implicit val floatFormat = transCassFormatEncoder(DataType.cfloat, Float.box)
  implicit val blobFormat = transCassFormatEncoder(DataType.blob, java.nio.ByteBuffer.wrap)

  implicit def listFormat[A](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[List[A]] {
    type To = java.util.List[underlying.To]
    val cassDataType = DataType.list(underlying.cassDataType)
    def encode(f: List[A]): Either[Throwable, java.util.List[underlying.To]] = {
      val acc = new java.util.ArrayList[underlying.To]()
      @scala.annotation.tailrec
      def process(l: List[A]): Either[Throwable, java.util.List[underlying.To]] = l.headOption.map(underlying.encode(_)) match {
        case Some(Left(ff)) => Left(ff)
        case Some(Right(n)) =>
          acc.add(n)
          process(l.tail)
        case None => Right(acc)
      }
      process(f)
    }
  }

  //  val a = tupleFormat[(String, Int)](implicitly[TupleCassFormatEncoder[(String, Int)]])

  implicit def setFormat[A](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[Set[A]] {
    type To = java.util.Set[underlying.To]
    val cassDataType = DataType.set(underlying.cassDataType)
    def encode(f: Set[A]): Either[Throwable, java.util.Set[underlying.To]] = {
      val acc = new java.util.HashSet[underlying.To]()
      @scala.annotation.tailrec
      def process(s: Set[A]): Either[Throwable, java.util.Set[underlying.To]] = s.headOption.map(underlying.encode(_)) match {
        case Some(Left(ff)) => Left(ff)
        case Some(Right(n)) =>
          acc.add(n)
          process(s.tail)
        case None => Right(acc)
      }
      process(f)
    }
  }
  implicit def mapFormat[A, B](implicit underlyingA: CassFormatEncoder[A], underlyingB: CassFormatEncoder[B]) =
    new CassFormatEncoder[Map[A, B]] {
      type To = java.util.Map[underlyingA.To, underlyingB.To]
      val cassDataType = DataType.map(underlyingA.cassDataType, underlyingB.cassDataType)
      def encode(f: Map[A, B]): Either[Throwable, java.util.Map[underlyingA.To, underlyingB.To]] = {
        val acc = new java.util.HashMap[underlyingA.To, underlyingB.To]()
        @scala.annotation.tailrec
        def process(l: Iterable[(A, B)]): Either[Throwable, java.util.Map[underlyingA.To, underlyingB.To]] = l.headOption.map {
          case (k, v) => for {
            kk <- underlyingA.encode(k).right
            vv <- underlyingB.encode(v).right
          } yield (kk, vv)
        } match {
          case Some(Left(ff)) => Left(ff)
          case Some(Right(n)) =>
            acc.put(n._1, n._2)
            process(l.tail)
          case None => Right(acc)
        }
        process(f)
      }
    }

  implicit def optionFormat[A](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[Option[A]] {
    type To = Option[underlying.To]
    val cassDataType = underlying.cassDataType
    def encode(f: Option[A]): Either[Throwable, Option[underlying.To]] = f.map(underlying.encode(_)) match {
      case None           => Right(None)
      case Some(Left(_))  => Right(None)
      case Some(Right(n)) => Right(Some(n))
    }
  }
  implicit def eitherFormat[A](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[Either[Throwable, A]] {
    type To = Either[Throwable, underlying.To]
    val cassDataType = underlying.cassDataType
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Product", "org.brianmckenna.wartremover.warts.Serializable"))
    def encode(f: Either[Throwable, A]): Either[Throwable, Either[Throwable, underlying.To]] = f.right.map(underlying.encode(_)) match {
      case Left(ff) => Right(Left(ff))
      case other    => other
    }
  }

  implicit val nothingFormat = new CassFormatEncoder[Nothing] {
    type To = Nothing
    def cassDataType = throw new IllegalArgumentException("Nothing isn't a real type!")
    override val cassType = ""
    def encode(f: Nothing): Either[Throwable, To] = throw new IllegalArgumentException("Nothing isn't a real type!")
  }
}