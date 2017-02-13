package com.weather.scalacass
package scsession

import com.datastax.driver.core._
import com.google.common.util.concurrent.{FutureCallback, Futures}
import com.weather.scalacass.scsession.QueryBuildingBlock._
import com.weather.scalacass.scsession.SCBatchStatement.Batchable

import scala.concurrent.{Future, Promise}

object SCStatement {
  private[scalacass] implicit def resultSetFutureToScalaFuture(f: ResultSetFuture): Future[ResultSet] = {
    val p = Promise[ResultSet]()
    Futures.addCallback(
      f,
      new FutureCallback[ResultSet] {
        def onSuccess(r: ResultSet): Unit = { p success r; (): Unit }
        def onFailure(t: Throwable): Unit = { p failure t; (): Unit }
      }
    )
    p.future
  }

  implicit class RightBiasedEither[+A, +B](val e: Either[A, B]) extends AnyVal {
    def map[C](fn: B => C): Either[A, C] = e.right.map(fn)
    def flatMap[AA >: A, C](fn: B => Either[AA, C]): Either[AA, C] = e.right.flatMap(fn)
    def foreach[U](fn: B => U): Unit = e match {
      case Right(b) =>
        fn(b); (): Unit
      case Left(_) =>
    }
    def getOrElse[BB >: B](or: => BB): BB = e.right.getOrElse(or)
    def valueOr[BB >: B](orFn: A => BB): BB = e.fold(orFn, identity)
  }
  type SCResultSetStatement = SCStatement[ResultSet]
  type SCIteratorStatement = SCStatement[Iterator[Row]]
  type SCOptionStatement = SCStatement[Option[Row]]
  type SCBatchableStatement = SCResultSetStatement with Batchable
}
trait SCStatement[Response] extends Product with Serializable {
  import SCStatement.{RightBiasedEither, resultSetFutureToScalaFuture}

  protected def sSession: ScalaSession

  protected def mkResponse(rs: ResultSet): Response
  def execute(): Result[Response] = prepare.map(p => mkResponse(sSession.session.execute(p)))
  def executeAsync(): Result[Future[Response]] = prepare.map(p => sSession.session.executeAsync(p).map(mkResponse)(scala.concurrent.ExecutionContext.global))

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock]
  private[scsession] def buildQuery: Result[(String, List[AnyRef])] = QueryBuildingBlock.build(queryBuildingBlocks)

  def getStringRepr: Result[String] = buildQuery.map(_._1)

  protected def prepare: Result[BoundStatement] = buildQuery.map {
    case (queryStr, anyrefArgs) =>
      val prepared = sSession.getFromCacheOrElse(queryStr, sSession.session.prepare(queryStr))
      prepared.bind(anyrefArgs: _*)
  }

  protected def replaceqWithValue(repr: String, values: List[AnyRef]): String = values.foldLeft(repr) { case (r, v) => r.replaceFirst("\\?", v.toString) }
  override def toString: String = buildQuery.fold("problem generating statement: " + _, query => s"${getClass.getSimpleName}(${(replaceqWithValue _).tupled(query)})")
}

final case class SCInsertStatement private (
    private val preableBlock: Preamble,
    private val insertBlock: QueryBuildingBlock,
    private val ifBlock: If = If.NoConditional,
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable {
  def ifNotExists: SCInsertStatement = copy(ifBlock = If.IfNotExists)
  def noConditional: SCInsertStatement = copy(ifBlock = If.NoConditional)

  def usingTTL(ttl: Int): SCInsertStatement = copy(usingBlock = usingBlock.updateWith(ttl))
  def usingTimestamp(ts: Long): SCInsertStatement = copy(usingBlock = usingBlock.updateWith(Some(ts)))
  def usingTimestampNow: SCInsertStatement = copy(usingBlock = usingBlock.updateWith(Option.empty[Long]))
  def noTTL: SCInsertStatement = copy(usingBlock = usingBlock.removeTTL)
  def noTimestamp: SCInsertStatement = copy(usingBlock = usingBlock.removeTimestamp)

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(preableBlock, insertBlock, ifBlock, usingBlock)
  protected def mkResponse(rs: ResultSet): ResultSet = rs
}
object SCInsertStatement {
  def apply[I: CCCassFormatEncoder](keyspace: String, table: String, insertable: I, sSession: ScalaSession) =
    new SCInsertStatement(Preamble("INSERT INTO", keyspace, table), CCBlockInsert(insertable))(sSession)
}

final case class SCUpdateStatement private (
    private val preamble: Preamble,
    private val updateBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither,
    private val ifBlock: If = If.NoConditional
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable with SCUpdateStatementVersionSpecific {
  def ifExists: SCUpdateStatement = copy(ifBlock = If.IfExists)
  def `if`[A: CCCassFormatEncoder](statement: A): SCUpdateStatement = copy(ifBlock = If.IfStatement(statement))
  def noConditional: SCUpdateStatement = copy(ifBlock = If.NoConditional)

  def usingTTL(ttl: Int): SCUpdateStatement = copy(usingBlock = usingBlock.updateWith(ttl))
  def usingTimestamp(ts: Long): SCUpdateStatement = copy(usingBlock = usingBlock.updateWith(Some(ts)))
  def usingTimestampNow: SCUpdateStatement = copy(usingBlock = usingBlock.updateWith(Option.empty[Long]))
  def noTTL: SCUpdateStatement = copy(usingBlock = usingBlock.removeTTL)
  def noTimestamp: SCUpdateStatement = copy(usingBlock = usingBlock.removeTimestamp)

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(preamble, usingBlock, updateBlock, whereBlock, ifBlock)
  protected def mkResponse(rs: ResultSet): ResultSet = rs
}
object SCUpdateStatement {
  def apply[U: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, updateable: U, where: Q, sSession: ScalaSession) =
    new SCUpdateStatement(Preamble("UPDATE", keyspace, table), CCBlockUpdate(updateable), CCBlockWhere(where))(sSession)
}

final case class SCDeleteStatement private (
    private val deleteBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither,
    private val ifBlock: If = If.NoConditional
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(deleteBlock, usingBlock, whereBlock, ifBlock)

  def usingTimestamp(ts: Long): SCDeleteStatement = copy(usingBlock = TTLTimestamp.Timestamp(Some(ts)))
  def usingTimestampNow: SCDeleteStatement = copy(usingBlock = TTLTimestamp.Timestamp(Option.empty[Long]))
  def noTimestamp: SCDeleteStatement = copy(usingBlock = TTLTimestamp.Neither)

  def ifExists: SCDeleteStatement = copy(ifBlock = If.IfExists)
  def `if`[A: CCCassFormatEncoder](statement: A): SCDeleteStatement = copy(ifBlock = If.IfStatement(statement))
  def noConditional: SCDeleteStatement = copy(ifBlock = If.NoConditional)
}
object SCDeleteStatement {
  def apply[D: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    new SCDeleteStatement(CCBlockDelete[D](Preamble("DELETE", keyspace, table)), CCBlockWhere(where))(sSession)
}

abstract class SCSelectStatement[F[_]](
    private val _mkResponse: ResultSet => F[Row],
    private val limitBlock: Limit = Limit.NoLimit
) extends SCStatement[F[Row]] {
  implicit protected def sSession: ScalaSession
  protected def selectBlock: QueryBuildingBlock
  protected def whereBlock: QueryBuildingBlock
  protected def filteringBlock: Filtering

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(selectBlock, whereBlock, limitBlock, filteringBlock)
  protected def mkResponse(rs: ResultSet): F[Row] = _mkResponse(rs)
}

final case class SCSelectOneStatement(
    protected val selectBlock: QueryBuildingBlock,
    protected val whereBlock: QueryBuildingBlock,
    protected val filteringBlock: Filtering = Filtering.NoFiltering
)(implicit protected val sSession: ScalaSession) extends SCSelectStatement[Option](SCSelectStatement.mkOptionResponse, Limit.LimitN(1)) {
  def allowFiltering: SCSelectOneStatement = copy(filteringBlock = Filtering.AllowFiltering)
  def noAllowFiltering: SCSelectOneStatement = copy(filteringBlock = Filtering.NoFiltering)
}

final case class SCSelectItStatement(
    protected val selectBlock: QueryBuildingBlock,
    protected val whereBlock: QueryBuildingBlock,
    protected val filteringBlock: Filtering = Filtering.NoFiltering,
    private val limitBlock: Limit = Limit.NoLimit
)(implicit protected val sSession: ScalaSession) extends SCSelectStatement[Iterator](SCSelectStatement.mkIteratorResponse, limitBlock) {
  def limit(n: Int): SCSelectItStatement = copy(limitBlock = Limit.LimitN(n))
  def noLimit: SCSelectItStatement = copy(limitBlock = Limit.NoLimit)

  def allowFiltering: SCSelectItStatement = copy(filteringBlock = Filtering.AllowFiltering)
  def noAllowFiltering: SCSelectItStatement = copy(filteringBlock = Filtering.NoFiltering)
}

object SCSelectStatement {
  def mkIteratorResponse(rs: ResultSet): Iterator[Row] = {
    import scala.collection.JavaConverters._
    rs.iterator.asScala
  }
  def mkOptionResponse(rs: ResultSet): Option[Row] = Option(rs.one())

  def apply[S: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    SCSelectItStatement(CCBlockSelect[S](Preamble("SELECT", keyspace, table)), CCBlockWhere(where))(sSession)

  def applyOne[S: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    SCSelectOneStatement(CCBlockSelect[S](Preamble("SELECT", keyspace, table)), CCBlockWhere(where))(sSession)
}

trait SCRaw[Response] extends SCStatement[Response] {
  protected def rawBlock: Raw
  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(rawBlock)
}
final case class SCRawStatement private[scsession] (
    protected val rawBlock: Raw
)(implicit protected val sSession: ScalaSession) extends SCRaw[ResultSet] with SCBatchStatement.Batchable {
  protected def mkResponse(rs: ResultSet): ResultSet = rs
}
final case class SCRawSelectStatement[F[_]] private[scsession] (
    private val _mkResponse: ResultSet => F[Row],
    protected val rawBlock: Raw
)(implicit protected val sSession: ScalaSession) extends SCRaw[F[Row]] {
  protected def mkResponse(rs: ResultSet): F[Row] = _mkResponse(rs)
}

object SCRaw {
  def apply(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawStatement = SCRawStatement(Raw(strRepr, anyrefArgs))(sSession)
  def applyIterator(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawSelectStatement[Iterator] = new SCRawSelectStatement[Iterator](SCSelectStatement.mkIteratorResponse, Raw(strRepr, anyrefArgs))(sSession)
  def applyOne(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawSelectStatement[Option] = new SCRawSelectStatement[Option](SCSelectStatement.mkOptionResponse, Raw(strRepr, anyrefArgs))(sSession)
}

final case class SCCreateKeyspaceStatement private (
    private val createKeyspaceBlock: CreateKeyspace
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(createKeyspaceBlock)

  def ifNotExists: SCCreateKeyspaceStatement =
    copy(createKeyspaceBlock = CreateKeyspace(createKeyspaceBlock.keyspace, If.IfNotExists, createKeyspaceBlock.properties))
  def noConditional: SCCreateKeyspaceStatement =
    copy(createKeyspaceBlock = CreateKeyspace(createKeyspaceBlock.keyspace, If.NoConditional, createKeyspaceBlock.properties))
}
object SCCreateKeyspaceStatement {
  def apply(keyspace: String, properties: String, sSession: ScalaSession): SCCreateKeyspaceStatement = new SCCreateKeyspaceStatement(CreateKeyspace(keyspace, If.NoConditional, properties))(sSession)
}
final case class SCDropKeyspaceStatement private (
    private val dropKeyspaceBlock: DropKeyspace
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(dropKeyspaceBlock)
}
object SCDropKeyspaceStatement {
  def apply(keyspace: String, sSession: ScalaSession): SCDropKeyspaceStatement = new SCDropKeyspaceStatement(DropKeyspace(keyspace))(sSession)
}

final case class SCCreateTableStatement private (
    private val createTable: QueryBuildingBlock,
    private val tableProperties: TableProperties = TableProperties.NoProperties
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(createTable, tableProperties)

  def `with`(properties: String): SCCreateTableStatement = copy(tableProperties = TableProperties.With(properties))
}
object SCCreateTableStatement {
  def apply[T: CCCassFormatEncoder](keyspace: String, name: String, numPartitionKeys: Int, numClusteringKeys: Int, sSession: ScalaSession): SCCreateTableStatement =
    new SCCreateTableStatement(CreateTable(keyspace, name, numPartitionKeys, numClusteringKeys))(sSession)
}
final case class SCTruncateTableStatement private (
    private val truncateTableBlock: TruncateTable
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(truncateTableBlock)
}
object SCTruncateTableStatement {
  def apply(keyspace: String, table: String, sSession: ScalaSession): SCTruncateTableStatement = new SCTruncateTableStatement(TruncateTable(keyspace, table))(sSession)
}
final case class SCDropTableStatement private (
    private val dropTableBlock: DropTable
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(dropTableBlock)
}
object SCDropTableStatement {
  def apply(keyspace: String, table: String, sSession: ScalaSession): SCDropTableStatement = new SCDropTableStatement(DropTable(keyspace, table))(sSession)
}

final case class SCBatchStatement private (
    private val statements: List[SCStatement.SCBatchableStatement],
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither,
    private val batchType: BatchStatement.Type = BatchStatement.Type.LOGGED
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  import SCStatement.{RightBiasedEither, resultSetFutureToScalaFuture, SCBatchableStatement}
  import SCBatchStatement.ListEitherTraverse

  protected def mkResponse(rs: ResultSet): ResultSet = throw new NotImplementedError("implementing execute/executeAsync directly -- not needed")

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = throw new NotImplementedError("batch uses the cassandra session directly -- don't use me!")

  override def toString: String = (for {
    tup <- statements.traverseU(_.buildQuery)
    queryStatements = tup.map(t => s"    ${t._1};").mkString("\n")
    fullQuery = s"""
     |  BEGIN $batchType BATCH
     |$queryStatements
     |  APPLY BATCH;
     |""".stripMargin
    values = tup.flatMap(_._2)
  } yield s"${getClass.getSimpleName}(${replaceqWithValue(fullQuery, values)})")
    .valueOr("problem generating statement: " + _)

  private def mkBatch: Result[BatchStatement] = statements.traverseU(_.asBatch).map { ss =>
    import scala.collection.JavaConverters._

    val bs = new BatchStatement(batchType)
    bs.addAll(ss.asJava)
    bs
  }
  override def execute(): Result[ResultSet] = mkBatch.map(sSession.session.execute)

  override def executeAsync(): Result[Future[ResultSet]] = mkBatch.map(sSession.session.executeAsync(_))

  def +(batchable: SCBatchableStatement): SCBatchStatement = copy(statements = statements :+ batchable)
  def ++(batchables: List[SCBatchableStatement]): SCBatchStatement = copy(statements = statements ++ batchables)
  def ++(otherStatement: SCBatchStatement): SCBatchStatement = copy(statements = statements ++ otherStatement.statements)
  def and(batchables: SCBatchableStatement*): SCBatchStatement = copy(statements = statements ++ batchables)

  def withBatchType(batchType: BatchStatement.Type): SCBatchStatement = copy(batchType = batchType)
}
object SCBatchStatement {
  trait Batchable { this: SCStatement.SCResultSetStatement =>
    def asBatch: Result[BoundStatement] = prepare
  }

  def apply(statements: List[SCStatement.SCBatchableStatement], sSession: ScalaSession): SCBatchStatement = new SCBatchStatement(statements)(sSession)

  // should be `private`, but the compiler thinks this is not being used (which it is), so setting to `protected` to work around bug
  protected implicit class ListEitherTraverse[LV](val list: List[LV]) extends AnyVal {
    def traverseU[L, R](f: LV => Either[L, R]): Either[L, List[R]] = {
      val builder = List.newBuilder[R]

      @scala.annotation.tailrec
      def trav(l: List[LV]): Either[L, List[R]] = l.headOption.map(f) match {
        case Some(Left(f)) => Left(f)
        case Some(Right(r)) =>
          builder += r; trav(l.tail)
        case None => Right(builder.result)
      }
      trav(list)
    }
  }

}