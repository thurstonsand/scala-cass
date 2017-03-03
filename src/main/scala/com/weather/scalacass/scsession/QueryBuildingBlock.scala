package com.weather.scalacass
package scsession

private[scalacass] sealed trait QueryBuildingBlock {
  import SCStatement.RightBiasedEither

  def strRepr: Result[String]
  def valueRepr: Result[List[AnyRef]]
  def allRepr: Result[(String, List[AnyRef])] = for {
    nr <- strRepr
    vr <- valueRepr
  } yield (nr, vr)
}

private[scalacass] object QueryBuildingBlock {
  import SCStatement.RightBiasedEither

  def namedEncode[A](toEncode: A)(implicit encoder: CCCassFormatEncoder[A]): Result[(List[String], List[AnyRef])] =
    encoder.encodeWithName(toEncode).right.map { e =>
      val strList = List.newBuilder[String]
      val anyrefList = List.newBuilder[AnyRef]

      e.foreach {
        case (str, Some(anyref: AnyRef)) =>
          strList += str; anyrefList += anyref
        case (_, None) =>
        case (str, anyref) =>
          strList += str; anyrefList += anyref
      }
      (strList.result, anyrefList.result)
    }

  def queryEncode[A](toEncode: A)(implicit encoder: CCCassFormatEncoder[A]): Result[(List[String], List[AnyRef])] =
    encoder.encodeWithQuery(toEncode).right.map { e =>
      val strList = List.newBuilder[String]
      val anyrefList = List.newBuilder[AnyRef]

      e.foreach {
        case (str, Some(anyref: AnyRef)) =>
          strList += str; anyrefList += anyref
        case (_, None) =>
        case (str, Is(anyref: AnyRef)) =>
          strList += str; anyrefList += anyref
        case (str, _: Nullable[_]) => strList += str
        case (str, anyref) =>
          strList += str; anyrefList += anyref
      }
      (strList.result, anyrefList.result)
    }

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
    protected def skipIfEmpty: Boolean

    def strRepr: Result[String] = strList.map(strs => if (skipIfEmpty && strs.isEmpty) "" else strs.mkString(prefix, infix, suffix))
  }

  abstract class CCBlockWithNamedValue[T: CCCassFormatEncoder](protected val skipIfEmpty: Boolean, protected val prefix: String, protected val infix: String, protected val suffix: String) extends CCBlock { this: QueryBuildingBlock =>
    protected def cc: T
    protected lazy val namedEncoded: Result[(List[String], List[AnyRef])] = namedEncode(cc)
    protected lazy val strList: Result[List[String]] = namedEncoded.map(_._1)

    def valueRepr: Result[List[AnyRef]] = namedEncoded.map(_._2)
  }

  abstract class CCBlockWithQueryValue[T: CCCassFormatEncoder](protected val skipIfEmpty: Boolean, protected val prefix: String, protected val infix: String, protected val suffix: String) extends CCBlock { this: QueryBuildingBlock =>
    protected def cc: T
    protected lazy val queryEncoded: Result[(List[String], List[AnyRef])] = queryEncode(cc)
    protected lazy val strList: Result[List[String]] = queryEncoded.map(_._1)

    def valueRepr: Result[List[AnyRef]] = queryEncoded.map(_._2)
  }

  abstract class CCBlockWithNoValue[T](protected val skipIfEmpty: Boolean, protected val preambleInfix: String)(implicit encoder: CCCassFormatEncoder[T]) extends CCBlock { this: QueryBuildingBlock =>
    protected def preamble: Preamble
    protected lazy val strList: Result[List[String]] = Right(encoder.names)

    protected val prefix = s"${preamble.verb} "
    protected val infix = s", "
    protected val suffix = s" $preambleInfix ${preamble.keyspace}.${preamble.table}"

    def valueRepr: Result[List[AnyRef]] = Right(Nil)
  }

  final case class CCBlockInsert[T: CCCassFormatEncoder](protected val cc: T) extends CCBlockWithNamedValue(true, "(", ", ", ")") with QueryBuildingBlock {
    override def strRepr: Result[String] = strList.map { ns =>
      if (ns.isEmpty) ""
      else s" ${ns.mkString(prefix, infix, suffix)} VALUES ${List.fill(ns.length)("?").mkString(prefix, infix, suffix)}"
    }
  }
  final case class CCBlockDelete[T](protected val preamble: Preamble)(implicit encoder: CCCassFormatEncoder[T]) extends CCBlockWithNoValue[T](false, "FROM") with QueryBuildingBlock
  final case class CCBlockSelect[T](preamble: Preamble)(implicit encoder: CCCassFormatEncoder[T]) extends CCBlockWithNoValue[T](false, "FROM") with QueryBuildingBlock
  final case class CCBlockUpdate[T: CCCassFormatEncoder](protected val cc: T) extends CCBlockWithQueryValue(true, " SET ", ", ", "") with QueryBuildingBlock
  final case class CCBlockWhere[T: CCCassFormatEncoder](protected val cc: T) extends CCBlockWithQueryValue(true, " WHERE ", " AND ", "") with QueryBuildingBlock

  sealed trait If extends QueryBuildingBlock

  object If {
    final case object NoConditional extends If with NoQuery

    final case object IfNotExists extends If {
      val strRepr = Right(" IF NOT EXISTS")
      val valueRepr = Right(Nil)
    }

    final case object IfExists extends If {
      val strRepr = Right(" IF EXISTS")
      val valueRepr = Right(Nil)
    }

    final case class IfStatement[A: CCCassFormatEncoder](cc: A) extends CCBlockWithQueryValue(false, " IF ", " AND ", "") with If
  }

  sealed trait Limit extends QueryBuildingBlock

  object Limit {
    final case object NoLimit extends NoQuery with Limit
    final case class LimitN(limit: Int) extends Limit {
      def strRepr: Result[String] = Right(" LIMIT ?")
      def valueRepr: Result[List[AnyRef]] = CassFormatEncoder[Int].encode(limit).map(_ :: Nil)
    }
  }

  sealed trait Filtering extends QueryBuildingBlock

  object Filtering {
    final case object NoFiltering extends NoQuery with Filtering
    final case object AllowFiltering extends Filtering {
      val strRepr: Result[String] = Right(" ALLOW FILTERING")
      val valueRepr: Result[List[AnyRef]] = Right(Nil)
    }
  }

  final case class Raw(private val _strRepr: String, private val anyrefArgs: List[AnyRef]) extends QueryBuildingBlock {
    def strRepr = Right(_strRepr)
    def valueRepr = Right(anyrefArgs)
  }

  final case class CreateTable[T](keyspace: String, name: String, numPartitionKeys: Int, numClusteringKeys: Int)(implicit encoder: CCCassFormatEncoder[T]) extends QueryBuildingBlock {
    private[this] def wrongPKSize(m: String): (Result[String], Result[List[AnyRef]]) = {
      val failed = Left(new WrongPrimaryKeySizeException(m))
      (failed, failed)
    }

    lazy val allColumns: List[(String, String)] = encoder.namesAndTypes

    lazy val (strRepr: Result[String], valueRepr: Result[List[AnyRef]]) =
      if (numPartitionKeys <= 0) wrongPKSize("must include at least one partition key")
      else if (numPartitionKeys + numClusteringKeys > allColumns.length) wrongPKSize(s"too many partition+clustering keys for table $name")
      else {
        val (partitionKeys, rest) = allColumns.splitAt(numPartitionKeys)
        val clusteringKeys = rest.take(numClusteringKeys)
        val pk = s"${partitionKeys.map(_._1).mkString("(", ", ", ")")}"
        val fullKey = if (numClusteringKeys > 0) s"($pk, ${clusteringKeys.map(_._1).mkString(", ")})" else s"($pk)"
        (Right(s"CREATE TABLE $keyspace.$name (${allColumns.map(nt => s"${nt._1} ${nt._2}").mkString(", ")}, PRIMARY KEY $fullKey)"), Right(Nil))
      }
  }
  final case class TruncateTable(keyspace: String, table: String) extends QueryBuildingBlock {
    def strRepr: Result[String] = Right(s"TRUNCATE TABLE $keyspace.$table")
    def valueRepr: Result[List[AnyRef]] = Right(Nil)
  }
  final case class DropTable(keyspace: String, table: String) extends QueryBuildingBlock {
    def strRepr: Result[String] = Right(s"DROP TABLE $keyspace.$table")
    def valueRepr: Result[List[AnyRef]] = Right(Nil)
  }

  sealed trait TableProperties extends QueryBuildingBlock
  object TableProperties {
    final case object NoProperties extends NoQuery with TableProperties
    final case class With(properties: String) extends TableProperties {
      def strRepr = Right(s" WITH $properties")
      def valueRepr = Right(Nil)
    }
  }

  final case class CreateKeyspace(keyspace: String, ifBlock: If, properties: String) extends QueryBuildingBlock {
    def strRepr: Result[String] = ifBlock.strRepr.map("CREATE KEYSPACE" + _ + s" $keyspace WITH $properties")
    def valueRepr: Result[List[AnyRef]] = Right(Nil)
  }
  final case class DropKeyspace(keyspace: String) extends QueryBuildingBlock {
    def strRepr: Result[String] = Right(s"DROP KEYSPACE $keyspace")
    def valueRepr: Result[List[AnyRef]] = Right(Nil)
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
  def of(qbbs: QueryBuildingBlock*): Result[(String, List[AnyRef])] = build(qbbs)
}