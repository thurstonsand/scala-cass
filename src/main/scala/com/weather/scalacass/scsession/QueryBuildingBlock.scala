package com.weather.scalacass
package scsession

trait QueryBuildingBlock {
  import SCStatement.RightBiasedEither

  def strRepr: Result[String]
  def valueRepr: Result[List[AnyRef]]
  def allRepr: Result[(String, List[AnyRef])] = for {
    nr <- strRepr
    vr <- valueRepr
  } yield (nr, vr)
}

object QueryBuildingBlock {
  import SCStatement.RightBiasedEither

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf", "org.brianmckenna.wartremover.warts.IsInstanceOf", "org.brianmckenna.wartremover.warts.Any"))
  private[this] def encode(encoded: Result[List[(String, AnyRef)]]): Result[(List[String], List[AnyRef])] = {
    encoded.right.map(_.flatMap {
      case (str, Some(anyref: AnyRef)) => List((str, anyref))
      case (_, None)                   => Nil
      case other                       => List(other)
    }.unzip)
  }
  def namedEncode[A](toEncode: A)(implicit encoder: CCCassFormatEncoder[A]): Result[(List[String], List[AnyRef])] =
    encode(encoder.encodeWithName(toEncode))

  def queryEncode[A](toEncode: A)(implicit encoder: CCCassFormatEncoder[A]): Result[(List[String], List[AnyRef])] =
    encode(encoder.encodeWithQuery(toEncode))

  trait NoQuery { this: QueryBuildingBlock =>
    val strRepr: Result[String] = Right("")
    val valueRepr: Result[List[AnyRef]] = Right(Nil)
  }

  final case class Preamble(verb: String, keyspace: String, table: String) extends QueryBuildingBlock {
    def strRepr = Right(s"$verb $keyspace.$table")
    def valueRepr = Right(Nil)
  }

  sealed trait TTLTimestamp extends QueryBuildingBlock {
    def updateWith(ttl: Int): TTLTimestamp = TTLTimestamp(this, ttl)
    def updateWith(ts: Option[Long]): TTLTimestamp = TTLTimestamp(this, ts)
    def removeTTL: TTLTimestamp = TTLTimestamp.removeTTL(this)
    def removeTimestamp: TTLTimestamp = TTLTimestamp.removeTimestamp(this)
  }

  object TTLTimestamp {
    final case object Neither extends TTLTimestamp with NoQuery

    final case class TTL(ttl: Int) extends TTLTimestamp {
      def strRepr: Result[String] = Right(" USING TTL ?")
      def valueRepr: Result[List[AnyRef]] = CassFormatEncoder[Int].encode(ttl).map(_ :: Nil)
    }

    final case class Timestamp(ts: Option[Long]) extends TTLTimestamp {
      def strRepr: Result[String] = Right(" USING TIMESTAMP" + (if (ts.isEmpty) "" else " ?"))
      def valueRepr: Result[List[AnyRef]] = ts match {
        case Some(_ts) => CassFormatEncoder[Long].encode(_ts).map(_ :: Nil)
        case None      => Right(Nil)
      }
    }
    final case class TTLAndTimestamp(ttl: Int, ts: Option[Long]) extends TTLTimestamp {
      def strRepr = Right(" USING TTL ? AND TIMESTAMP" + (if (ts.isEmpty) "" else " ?"))
      def valueRepr = (ts match {
        case Some(_ts) => CassFormatEncoder[Long].encode(_ts).right.map(_ :: Nil)
        case None      => Right(Nil)
      }).flatMap(ls => CassFormatEncoder[Int].encode(ttl).map(_ :: ls))
    }

    def apply(prev: TTLTimestamp, ts: Option[Long]): TTLTimestamp = prev match {
      case Neither             => Timestamp(ts)
      case TTL(ttl)            => TTLAndTimestamp(ttl, ts)
      case _: Timestamp        => Timestamp(ts)
      case tt: TTLAndTimestamp => tt.copy(ts = ts)
    }

    def apply(prev: TTLTimestamp, ttl: Int): TTLTimestamp = prev match {
      case Neither             => TTL(ttl)
      case _: TTL              => TTL(ttl)
      case Timestamp(ts)       => TTLAndTimestamp(ttl, ts)
      case tt: TTLAndTimestamp => tt.copy(ttl = ttl)
    }

    def removeTimestamp(prev: TTLTimestamp): TTLTimestamp = prev match {
      case TTLAndTimestamp(ttl, _) => TTL(ttl)
      case _: Timestamp            => Neither
      case other                   => other
    }

    def removeTTL(prev: TTLTimestamp): TTLTimestamp = prev match {
      case TTLAndTimestamp(_, ts) => Timestamp(ts)
      case _: TTL                 => Neither
      case other                  => other
    }
  }

  trait CCBlock { this: QueryBuildingBlock =>
    protected def prefix: String
    protected def infix: String
    protected def suffix: String
    protected def strList: Result[List[String]]

    def strRepr: Result[String] = strList.map(_.mkString(prefix, infix, suffix))
  }

  abstract class CCBlockWithNamedValue[T: CCCassFormatEncoder](protected val prefix: String, protected val infix: String, protected val suffix: String) extends CCBlock { this: QueryBuildingBlock =>
    protected def cc: T
    protected lazy val namedEncoded = namedEncode(cc)
    protected lazy val strList = namedEncoded.map(_._1)

    def valueRepr = namedEncoded.map(_._2)
  }

  abstract class CCBlockWithQueryValue[T: CCCassFormatEncoder](protected val prefix: String, protected val infix: String, protected val suffix: String) extends CCBlock { this: QueryBuildingBlock =>
    protected def cc: T
    protected lazy val queryEncoded = queryEncode(cc)
    protected lazy val strList = queryEncoded.map(_._1)

    def valueRepr = queryEncoded.map(_._2)
  }

  abstract class CCBlockWithNoValue[T](protected val preambleInfix: String)(implicit encoder: CCCassFormatEncoder[T]) extends CCBlock { this: QueryBuildingBlock =>
    protected def preamble: Preamble
    protected lazy val strList = Right(encoder.names)

    protected val prefix = s"${preamble.verb} "
    protected val infix = s", "
    protected val suffix = s" $preambleInfix ${preamble.keyspace}.${preamble.table}"

    def valueRepr = Right(Nil)
  }

  case class CCBlockInsert[T: CCCassFormatEncoder](protected val cc: T) extends CCBlockWithNamedValue(" (", ", ", ")") with QueryBuildingBlock {
    override def strRepr: Result[String] = strList.map { ns =>
      if (ns.isEmpty) ""
      else s"${ns.mkString(prefix, infix, suffix)} VALUES ${List.fill(ns.length)("?").mkString(prefix, infix, suffix)}"
    }
  }
  case class CCBlockDelete[T](protected val preamble: Preamble)(implicit encoder: CCCassFormatEncoder[T]) extends CCBlockWithNoValue[T]("FROM") with QueryBuildingBlock
  case class CCBlockSelect[T](preamble: Preamble)(implicit encoder: CCCassFormatEncoder[T]) extends CCBlockWithNoValue[T]("FROM") with QueryBuildingBlock
  case class CCBlockUpdate[T: CCCassFormatEncoder](protected val cc: T) extends CCBlockWithQueryValue(" SET ", ", ", "") with QueryBuildingBlock
  case class CCBlockWhere[T: CCCassFormatEncoder](protected val cc: T) extends CCBlockWithQueryValue(" WHERE ", " AND ", "") with QueryBuildingBlock

  sealed trait If extends QueryBuildingBlock

  object If {
    case object NoConditional extends If with NoQuery

    case object IfNotExists extends If {
      val strRepr = Right(" IF NOT EXISTS")
      val valueRepr = Right(Nil)
    }

    case object IfExists extends If {
      val strRepr = Right(" IF EXISTS")
      val valueRepr = Right(Nil)
    }

    case class IfStatement[A: CCCassFormatEncoder](cc: A) extends CCBlockWithQueryValue(" IF ", " AND ", "") with If
  }

  sealed trait Limit extends QueryBuildingBlock

  object Limit {
    case object NoLimit extends NoQuery with Limit
    case class LimitN(limit: Int) extends Limit {
      def strRepr: Result[String] = Right(" LIMIT ?")
      def valueRepr: Result[List[AnyRef]] = CassFormatEncoder[Int].encode(limit).map(_ :: Nil)
    }
  }

  sealed trait Filtering extends QueryBuildingBlock

  object Filtering {
    case object NoFiltering extends NoQuery with Filtering
    case object AllowFiltering extends Filtering {
      val strRepr: Result[String] = Right(" ALLOW FILTERING")
      val valueRepr: Result[List[AnyRef]] = Right(Nil)
    }
  }

  def build(qbbs: Seq[QueryBuildingBlock]): Result[(String, List[AnyRef])] = {
    qbbs.foldLeft(Right(("", List.empty[AnyRef])): Result[(String, List[AnyRef])]) {
      case (acc, n) =>
        for {
          _acc <- acc
          tup <- n.allRepr
        } yield (_acc._1 + tup._1, _acc._2 ::: tup._2)
    }
  }
  def of(qbbs: QueryBuildingBlock*) = build(qbbs)
}