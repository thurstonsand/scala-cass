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
        def onSuccess(r: ResultSet) = { p success r; (): Unit }
        def onFailure(t: Throwable) = { p failure t; (): Unit }
      }
    )
    p.future
  }

  implicit class RightBiasedEither[+A, +B](val e: Either[A, B]) extends AnyVal {
    def map[C](fn: B => C): Either[A, C] = e.right.map(fn)
    def flatMap[AA >: A, C](fn: B => Either[AA, C]) = e.right.flatMap(fn)
    def foreach[U](fn: B => U): Unit = e match {
      case Right(b) =>
        fn(b); (): Unit
      case Left(_) =>
    }
    def getOrElse[BB >: B](or: => BB): BB = e.right.getOrElse(or)
  }
  type SCResultSetStatement = SCStatement[ResultSet]
  type SCIteratorStatement = SCStatement[Iterator[Row]]
  type SCOptionStatement = SCStatement[Option[Row]]
}
trait SCStatement[Response] {
  import SCStatement.{RightBiasedEither, resultSetFutureToScalaFuture}

  protected def sSession: ScalaSession

  protected def mkResponse(rs: ResultSet): Response
  def execute: Result[Response] = prepare.map(p => mkResponse(sSession.session.execute(p)))
  def executeAsync: Result[Future[Response]] = prepare.map(p => sSession.session.executeAsync(p).map(mkResponse)(scala.concurrent.ExecutionContext.global))

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock]
  private[this] def buildQuery: Result[(String, List[AnyRef])] = QueryBuildingBlock.build(queryBuildingBlocks)

  def getStringRepr: Result[String] = buildQuery.map(_._1)

  protected def prepare: Result[BoundStatement] = buildQuery.map {
    case (queryStr, anyrefArgs) =>
      val prepared = sSession.getFromCacheOrElse(queryStr, sSession.session.prepare(queryStr))
      prepared.bind(anyrefArgs: _*)
  }
}

final case class SCInsertStatement private (
    private val preableBlock: Preamble,
    private val insertBlock: QueryBuildingBlock,
    private val ifBlock: If = If.NoConditional,
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable {
  //  def withKeyspace(keyspace: String): SCInsertStatement[T] = copy(preableBlock = preableBlock.copy(keyspace = keyspace))
  //  def withTable(table: String): SCInsertStatement[T] = copy(preableBlock = preableBlock.copy(table = table))

  def ifNotExists: SCInsertStatement = copy(ifBlock = If.IfNotExists)
  def noConditional: SCInsertStatement = copy(ifBlock = If.NoConditional)

  def usingTTL(ttl: Int): SCInsertStatement = copy(usingBlock = usingBlock.updateWith(ttl))
  def usingTimestamp(ts: Long): SCInsertStatement = copy(usingBlock = usingBlock.updateWith(Some(ts)))
  def usingTimestampNow: SCInsertStatement = copy(usingBlock = usingBlock.updateWith(Option.empty[Long]))
  def noTTL: SCInsertStatement = copy(usingBlock = usingBlock.removeTTL)
  def noTimestamp: SCInsertStatement = copy(usingBlock = usingBlock.removeTimestamp)

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(preableBlock, insertBlock, ifBlock, usingBlock)
  protected def mkResponse(rs: ResultSet) = rs
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
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable {
  def ifExists: SCUpdateStatement = copy(ifBlock = If.IfExists)
  def `if`[A: CCCassFormatEncoder](statement: A) = copy(ifBlock = If.IfStatement(statement))
  def noConditional: SCUpdateStatement = copy(ifBlock = If.NoConditional)

  def usingTTL(ttl: Int): SCUpdateStatement = copy(usingBlock = usingBlock.updateWith(ttl))
  def usingTimestamp(ts: Long): SCUpdateStatement = copy(usingBlock = usingBlock.updateWith(Some(ts)))
  def usingTimestampNow: SCUpdateStatement = copy(usingBlock = usingBlock.updateWith(Option.empty[Long]))
  def noTTL: SCUpdateStatement = copy(usingBlock = usingBlock.removeTTL)
  def noTimestamp: SCUpdateStatement = copy(usingBlock = usingBlock.removeTimestamp)

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(preamble, usingBlock, updateBlock, whereBlock, ifBlock)
  protected def mkResponse(rs: ResultSet) = rs
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
  def noTimestamp: SCDeleteStatement = copy(usingBlock = TTLTimestamp.Neither)

  def ifExists: SCDeleteStatement = copy(ifBlock = If.IfExists)
  def `if`[A: CCCassFormatEncoder](statement: A) = copy(ifBlock = If.IfStatement(statement))
  def noConditional: SCDeleteStatement = copy(ifBlock = If.NoConditional)
}
object SCDeleteStatement {
  def apply[D: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    new SCDeleteStatement(CCBlockDelete[D](Preamble("DELETE", keyspace, table)), CCBlockWhere(where))(sSession)
}

final case class SCSelectStatement[F[_]](
    private val _mkResponse: ResultSet => F[Row],
    private val selectBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val limitBlock: Limit = Limit.NoLimit,
    private val filteringBlock: Filtering = Filtering.NoFiltering
)(implicit protected val sSession: ScalaSession) extends SCStatement[F[Row]] {

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(selectBlock, whereBlock, limitBlock, filteringBlock)

  protected def mkResponse(rs: ResultSet): F[Row] = _mkResponse(rs)
  def limit(n: Int): SCSelectStatement[F] = copy(limitBlock = Limit.LimitN(n))
  def noLimit: SCSelectStatement[F] = copy(limitBlock = Limit.NoLimit)

  def allowFiltering: SCSelectStatement[F] = copy(filteringBlock = Filtering.AllowFiltering)
  def noAllowFiltering: SCSelectStatement[F] = copy(filteringBlock = Filtering.NoFiltering)
}
object SCSelectStatement {
  def mkIteratorResponse(rs: ResultSet): Iterator[Row] = {
    import scala.collection.JavaConverters._
    rs.iterator.asScala
  }
  def mkOptionResponse(rs: ResultSet): Option[Row] = Option(rs.one())

  def apply[S: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    new SCSelectStatement[Iterator](mkIteratorResponse, CCBlockSelect[S](Preamble("SELECT", keyspace, table)), CCBlockWhere(where))(sSession)

  def applyOne[S: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    new SCSelectStatement[Option](mkOptionResponse, CCBlockSelect[S](Preamble("SELECT", keyspace, table)), CCBlockWhere(where))(sSession)
}

trait SCRaw[Response] extends SCStatement[Response] {
  protected def rawBlock: Raw
  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(rawBlock)
}
final case class SCRawStatement private (
    protected val rawBlock: Raw
)(implicit protected val sSession: ScalaSession) extends SCRaw[ResultSet] with SCBatchStatement.Batchable {
  protected def mkResponse(rs: ResultSet): ResultSet = rs
}
final case class SCRawSelectStatement[F[_]](private val _mkResponse: ResultSet => F[Row], protected val rawBlock: Raw)(implicit protected val sSession: ScalaSession) extends SCRaw[F[Row]] {
  protected def mkResponse(rs: ResultSet): F[Row] = _mkResponse(rs)
}

object SCRaw {
  def apply(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawStatement = new SCRawStatement(Raw(strRepr, anyrefArgs))(sSession)
  def applyIterator(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawSelectStatement[Iterator] = new SCRawSelectStatement[Iterator](SCSelectStatement.mkIteratorResponse, Raw(strRepr, anyrefArgs))(sSession)
  def applyOne(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawSelectStatement[Option] = new SCRawSelectStatement[Option](SCSelectStatement.mkOptionResponse, Raw(strRepr, anyrefArgs))(sSession)
}

final case class SCCreateTableStatement private (
    private val createTable: QueryBuildingBlock,
    private val tableProperties: TableProperties = TableProperties.NoProperties
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(createTable, tableProperties)

  def `with`(properties: String) = copy(tableProperties = TableProperties.With(properties))
}
object SCCreateTableStatement {
  def apply[T: CCCassFormatEncoder](keyspace: String, name: String, numPartitionKeys: Int, numClusteringKeys: Int, sSession: ScalaSession): SCCreateTableStatement =
    new SCCreateTableStatement(CreateTable(keyspace, name, numPartitionKeys, numClusteringKeys))(sSession)
}

final case class SCBatchStatement private (
    private val statements: Seq[SCBatchStatement.Batchable],
    private val batchType: BatchStatement.Type = BatchStatement.Type.LOGGED
)(
    implicit
    protected val sSession: ScalaSession
) extends SCStatement[ResultSet] {
  import SCStatement.{RightBiasedEither, resultSetFutureToScalaFuture}

  protected def mkResponse(rs: ResultSet): ResultSet = throw new NotImplementedError("implementing execute/executeAsync directly -- not needed")

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = throw new NotImplementedError("batch uses the cassandra session directly -- don't use me!")

  private def mkBatch: Result[BatchStatement] = statements.foldLeft[Result[BatchStatement]](Right(new BatchStatement(batchType))) {
    case (bs, s) =>
      for {
        _bs <- bs
        b <- s.asBatch
      } yield { _bs.add(b); _bs }
  }
  override def execute: Result[ResultSet] = mkBatch.map(sSession.session.execute)

  override def executeAsync: Result[Future[ResultSet]] = mkBatch.map(sSession.session.executeAsync(_))

  def +(batchable: Batchable): SCBatchStatement = copy(statements = batchable +: statements)
  def ++(batchables: Seq[Batchable]): SCBatchStatement = copy(statements = batchables ++ statements)
  def and(batchables: Batchable*): SCBatchStatement = copy(statements = batchables ++ statements)

  def withBatchType(batchType: BatchStatement.Type) = copy(batchType = batchType)
}
object SCBatchStatement {
  trait Batchable { this: SCStatement.SCResultSetStatement =>
    def asBatch: Result[BoundStatement] = prepare
  }

  def apply(statements: Seq[Batchable], sSession: ScalaSession): SCBatchStatement = new SCBatchStatement(statements)(sSession)
}