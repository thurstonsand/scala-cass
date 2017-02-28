package com.weather.scalacass

import com.datastax.driver.core.DataType
import ScalaSession.UpdateBehavior

trait CassFormatEncoder[F] { self =>
  type From <: AnyRef
  def cassDataType: DataType
  def encode(f: F): Result[From]
  def withQuery(instance: F, name: String) = s"$name=?"
  def cassType: String = cassDataType.toString

  final def map[G](fn: G => F): CassFormatEncoder[G] = new CassFormatEncoder[G] {
    type From = self.From
    val cassDataType = self.cassDataType
    def encode(f: G): Result[From] = self.encode(fn(f))
  }

  final def flatMap[G](fn: G => Result[F]): CassFormatEncoder[G] = new CassFormatEncoder[G] {
    type From = self.From
    val cassDataType = self.cassDataType
    def encode(f: G): Result[From] = fn(f).right.flatMap(self.encode)
  }
}

object CassFormatEncoder extends CassFormatEncoderVersionSpecific {
  type Aux[F, From0] = CassFormatEncoder[F] { type From = From0 }
  def apply[T](implicit encoder: CassFormatEncoder[T]) = encoder

  private[scalacass] def sameTypeCassFormatEncoder[F <: AnyRef](_cassDataType: DataType): CassFormatEncoder[F] = new CassFormatEncoder[F] {
    type From = F
    val cassDataType = _cassDataType
    def encode(f: F) = Right(f)
  }
  private[scalacass] def transCassFormatEncoder[F, T <: AnyRef](_cassDataType: DataType, _encode: F => T): CassFormatEncoder[F] = new CassFormatEncoder[F] {
    type From = T
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

  def updateBehaviorListEncoder[A, UB <: UpdateBehavior[List, A]](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[UB] {
    type From = java.util.List[underlying.From]
    val cassDataType = DataType.list(underlying.cassDataType)
    def encode(f: UB): Result[java.util.List[underlying.From]] = {
      val acc = new java.util.ArrayList[underlying.From]()
      @scala.annotation.tailrec
      def process(l: List[A]): Result[java.util.List[underlying.From]] = l.headOption.map(underlying.encode(_)) match {
        case Some(Left(ff)) => Left(ff)
        case Some(Right(n)) =>
          acc.add(n)
          process(l.tail)
        case None => Right(acc)
      }
      process(f.coll)
    }
    override def withQuery(instance: UB, name: String): String = instance withQuery name
  }

  def updateBehaviorSetEncoder[A, UB <: UpdateBehavior[Set, A]](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[UB] {
    type From = java.util.Set[underlying.From]
    val cassDataType = DataType.set(underlying.cassDataType)
    def encode(f: UB): Result[java.util.Set[underlying.From]] = {
      val acc = new java.util.HashSet[underlying.From]()
      @scala.annotation.tailrec
      def process(s: Set[A]): Result[java.util.Set[underlying.From]] = s.headOption.map(underlying.encode(_)) match {
        case Some(Left(ff)) => Left(ff)
        case Some(Right(n)) =>
          acc.add(n)
          process(s.tail)
        case None => Right(acc)
      }
      process(f.coll)
    }
    override def withQuery(instance: UB, name: String): String = instance withQuery name
  }

  implicit def listFormatAdd[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior.Add[List, A]] =
    updateBehaviorListEncoder[A, UpdateBehavior.Add[List, A]]
  implicit def listFormatSubtract[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior.Subtract[List, A]] =
    updateBehaviorListEncoder[A, UpdateBehavior.Subtract[List, A]]
  implicit def listFormatReplace[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior.Replace[List, A]] =
    updateBehaviorListEncoder[A, UpdateBehavior.Replace[List, A]]
  implicit def listFormatUpdateBehavior[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior[List, A]] =
    updateBehaviorListEncoder[A, UpdateBehavior[List, A]]

  implicit def listFormat[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[List[A]] =
    updateBehaviorListEncoder[A, UpdateBehavior.Replace[List, A]](underlying).map[List[A]](UpdateBehavior.Replace(_))

  implicit def setFormatAdd[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior.Add[Set, A]] =
    updateBehaviorSetEncoder[A, UpdateBehavior.Add[Set, A]]
  implicit def setFormatSubtract[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior.Subtract[Set, A]] =
    updateBehaviorSetEncoder[A, UpdateBehavior.Subtract[Set, A]]
  implicit def setFormatReplace[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior.Replace[Set, A]] =
    updateBehaviorSetEncoder[A, UpdateBehavior.Replace[Set, A]]
  implicit def setFormatUpdateBehavior[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[UpdateBehavior[Set, A]] =
    updateBehaviorSetEncoder[A, UpdateBehavior[Set, A]]

  implicit def setFormat[A](implicit underlying: CassFormatEncoder[A]): CassFormatEncoder[Set[A]] =
    updateBehaviorSetEncoder[A, UpdateBehavior.Replace[Set, A]](underlying).map[Set[A]](UpdateBehavior.Replace(_))

  implicit def mapFormat[A, B](implicit underlyingA: CassFormatEncoder[A], underlyingB: CassFormatEncoder[B]) =
    new CassFormatEncoder[Map[A, B]] {
      type From = java.util.Map[underlyingA.From, underlyingB.From]
      val cassDataType = DataType.map(underlyingA.cassDataType, underlyingB.cassDataType)
      def encode(f: Map[A, B]): Result[java.util.Map[underlyingA.From, underlyingB.From]] = {
        val acc = new java.util.HashMap[underlyingA.From, underlyingB.From]()
        @scala.annotation.tailrec
        def process(l: Iterable[(A, B)]): Result[java.util.Map[underlyingA.From, underlyingB.From]] = l.headOption.map {
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
    type From = Option[underlying.From]
    val cassDataType = underlying.cassDataType
    def encode(f: Option[A]): Result[Option[underlying.From]] = f.map(underlying.encode(_)) match {
      case None           => Right(None)
      case Some(Left(_))  => Right(None)
      case Some(Right(n)) => Right(Some(n))
    }
  }
  implicit def eitherFormat[A](implicit underlying: CassFormatEncoder[A]) = new CassFormatEncoder[Result[A]] {
    type From = Result[underlying.From]
    val cassDataType = underlying.cassDataType
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Product", "org.brianmckenna.wartremover.warts.Serializable"))
    def encode(f: Result[A]): Result[Result[underlying.From]] = f.right.map(underlying.encode(_)) match {
      case Left(ff) => Right(Left(ff))
      case other    => other
    }
  }

  implicit val nothingFormat = new CassFormatEncoder[Nothing] {
    type From = Nothing
    def cassDataType = throw new IllegalArgumentException("Nothing isn't a real type!")
    def encode(f: Nothing): Result[From] = throw new IllegalArgumentException("Nothing isn't a real type!")
  }
}