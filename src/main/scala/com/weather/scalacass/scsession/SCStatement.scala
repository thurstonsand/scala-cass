package com.weather.scalacass
package scsession

import java.util.concurrent.Executor

import com.datastax.driver.core._
import com.google.common.util.concurrent.{ FutureCallback, Futures }
import com.weather.scalacass.scsession.QueryBuildingBlock._
import com.weather.scalacass.scsession.SCBatchStatement.Batchable

import scala.concurrent.{ ExecutionContext, Future, Promise }

object SCStatement {
  private[scalacass] implicit def resultSetFutureToScalaFuture(f: ResultSetFuture)(implicit ec: ExecutionContext): Future[ResultSet] = {
    val p = Promise[ResultSet]()
    Futures.addCallback(
      f,
      new FutureCallback[ResultSet] {
        def onSuccess(r: ResultSet): Unit = { p success r; (): Unit }
        def onFailure(t: Throwable): Unit = { p failure t; (): Unit }
      },
      new Executor {
        override def execute(command: Runnable): Unit = ec.execute(command)
      }
    )
    p.future
  }

  private[scalacass] implicit class BiMappableFuture[T](val f: Future[T]) {
    def bimap[LL, RR](lfn: Throwable => LL, rfn: T => RR)(implicit ec: ExecutionContext): Future[Either[LL, RR]] =
      f.map(res => Right(rfn(res))).recover { case t => Left(lfn(t)) }
    def attempt(implicit ec: ExecutionContext): Future[Result[T]] = bimap(identity, identity)
  }

  private[scalacass] implicit class RightBiasedEither[+A, +B](val e: Either[A, B]) extends AnyVal {
    def map[C](fn: B => C): Either[A, C] = e.right.map(fn)
    def flatMap[AA >: A, C](fn: B => Either[AA, C]): Either[AA, C] = e.right.flatMap(fn)
    def foreach[U](fn: B => U): Unit = e match {
      case Right(b) =>
        fn(b); (): Unit
      case Left(_) =>
    }
    def getOrElse[BB >: B](or: => BB): BB = e.right.getOrElse(or)
    def valueOr[BB >: B](orFn: A => BB): BB = e.fold(orFn, identity)
    def toOption: Option[B] = e.right.toOption
  }
  type SCBatchableStatement = SCStatement[ResultSet] with Batchable
}
trait SCStatement[Response] extends Product with Serializable {
  import SCStatement.{ RightBiasedEither, BiMappableFuture, resultSetFutureToScalaFuture }

  protected def sSession: ScalaSession

  protected def mkResponse(rs: ResultSet): Response

  protected def cassConsistency: CassConsistency

  def execute(): Result[Response] =
    prepareAndBind().map(b => mkResponse(sSession.session.execute(b)))

  def executeAsync()(implicit ec: ExecutionContext): Future[Result[Response]] =
    prepareAndBind().fold(Future.failed, b => (sSession.session.executeAsync(b): Future[ResultSet]).bimap(identity, mkResponse))

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock]

  private[scsession] def buildQuery: Result[(String, List[AnyRef])] = QueryBuildingBlock.build(queryBuildingBlocks)

  def getStringRepr: Result[String] = buildQuery.map(_._1)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  protected[scalacass] def prepareAndBind(): Result[BoundStatement] =
    buildQuery.flatMap { case (queryStr, anyrefArgs) =>
      sSession.getFromCacheOrElse(queryStr, sSession.session.prepare(queryStr))
        .map { p =>
          p.setConsistencyLevel(cassConsistency.level.orNull)
          p.bind(anyrefArgs: _*)
        }
    }

  protected def replaceqWithValue(repr: String, values: List[AnyRef]): String = values.foldLeft(repr) { case (r, v) => r.replaceFirst("\\?", v.toString) }

  protected[scalacass] def stringifyQuery: Result[String] = buildQuery.map { case (statement, anyrefArgs) => replaceqWithValue(statement, anyrefArgs) + cassConsistency.level.fold("")(cl => s" <CONSISTENCY $cl>") }
  override def toString: String = stringifyQuery.fold(ex => s"problem generating statement: $ex", q => s"${getClass.getSimpleName}($q)")
}

final case class SCInsertStatement private (
    private val preambleBlock: Preamble,
    private val insertBlock: QueryBuildingBlock,
    private val ifBlock: If,
    private val usingBlock: TTLTimestamp,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable {
  def ifNotExists: SCInsertStatement = copy(ifBlock = If.IfNotExists)
  def noConditional: SCInsertStatement = copy(ifBlock = If.NoConditional)

  def usingTTL(ttl: Int): SCInsertStatement = copy(usingBlock = usingBlock.updateTTLWith(ttl))
  def usingTimestamp(ts: Long): SCInsertStatement = copy(usingBlock = usingBlock.updateTimestampWith(ts))
  def usingTimestampNow: SCInsertStatement = copy(usingBlock = usingBlock.updateTimestampWith(System.currentTimeMillis()))
  def noTTL: SCInsertStatement = copy(usingBlock = usingBlock.removeTTL)
  def noTimestamp: SCInsertStatement = copy(usingBlock = usingBlock.removeTimestamp)

  def consistency(cl: ConsistencyLevel): SCInsertStatement = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCInsertStatement = copy(cassConsistency = CassConsistency.removeConsistency)

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(preambleBlock, insertBlock, ifBlock, usingBlock)
  protected def mkResponse(rs: ResultSet): ResultSet = rs
}
object SCInsertStatement {
  def apply[I : CCCassFormatEncoder](keyspace: String, table: String, insertable: I, sSession: ScalaSession) =
    new SCInsertStatement(Preamble("INSERT INTO", keyspace, table), CCBlockInsert(insertable), If.NoConditional, TTLTimestamp.Neither, CassConsistency.Default)(sSession)
}

final case class SCUpdateStatement private (
    private val preamble: Preamble,
    private val updateBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val usingBlock: TTLTimestamp,
    private val ifBlock: If,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable with SCUpdateStatementVersionSpecific {
  def ifExists: SCUpdateStatement = copy(ifBlock = If.IfExists)
  def `if`[A : CCCassFormatEncoder](statement: A): SCUpdateStatement = copy(ifBlock = If.IfStatement(statement))
  def noConditional: SCUpdateStatement = copy(ifBlock = If.NoConditional)

  def usingTTL(ttl: Int): SCUpdateStatement = copy(usingBlock = usingBlock.updateTTLWith(ttl))
  def usingTimestamp(ts: Long): SCUpdateStatement = copy(usingBlock = usingBlock.updateTimestampWith(ts))
  def usingTimestampNow: SCUpdateStatement = copy(usingBlock = usingBlock.updateTimestampWith(System.currentTimeMillis()))
  def noTTL: SCUpdateStatement = copy(usingBlock = usingBlock.removeTTL)
  def noTimestamp: SCUpdateStatement = copy(usingBlock = usingBlock.removeTimestamp)

  def consistency(cl: ConsistencyLevel): SCUpdateStatement = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCUpdateStatement = copy(cassConsistency = CassConsistency.removeConsistency)

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(preamble, usingBlock, updateBlock, whereBlock, ifBlock)
  protected def mkResponse(rs: ResultSet): ResultSet = rs
}
object SCUpdateStatement {
  def apply[U : CCCassFormatEncoder, Q : CCCassFormatEncoder](keyspace: String, table: String, updateable: U, where: Q, sSession: ScalaSession) =
    new SCUpdateStatement(Preamble("UPDATE", keyspace, table), CCBlockUpdate(updateable), CCBlockWhere(where), TTLTimestamp.Neither, If.NoConditional, CassConsistency.Default)(sSession)
}

final case class SCDeleteStatement private (
    private val deleteBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val usingBlock: TTLTimestamp,
    private val ifBlock: If,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] with SCBatchStatement.Batchable {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(deleteBlock, usingBlock, whereBlock, ifBlock)

  def usingTimestamp(ts: Long): SCDeleteStatement = copy(usingBlock = TTLTimestamp.Timestamp(ts))
  def usingTimestampNow: SCDeleteStatement = copy(usingBlock = TTLTimestamp.Timestamp(System.currentTimeMillis()))
  def noTimestamp: SCDeleteStatement = copy(usingBlock = TTLTimestamp.Neither)

  def ifExists: SCDeleteStatement = copy(ifBlock = If.IfExists)
  def `if`[A : CCCassFormatEncoder](statement: A): SCDeleteStatement = copy(ifBlock = If.IfStatement(statement))
  def noConditional: SCDeleteStatement = copy(ifBlock = If.NoConditional)

  def consistency(cl: ConsistencyLevel): SCDeleteStatement = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCDeleteStatement = copy(cassConsistency = CassConsistency.removeConsistency)
}
object SCDeleteStatement {
  def apply[D : CCCassFormatEncoder, Q : CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    new SCDeleteStatement(CCBlockDelete[D](Preamble("DELETE", keyspace, table)), CCBlockWhere(where), TTLTimestamp.Neither, If.NoConditional, CassConsistency.Default)(sSession)
}

abstract class SCSelectStatement[F[_]](
    private val _mkResponse: ResultSet => F[Row],
    private val limitBlock: Limit
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
    protected val filteringBlock: Filtering,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCSelectStatement[Option](SCSelectStatement.mkOptionResponse, Limit.LimitN(1)) {
  def allowFiltering: SCSelectOneStatement = copy(filteringBlock = Filtering.AllowFiltering)
  def noAllowFiltering: SCSelectOneStatement = copy(filteringBlock = Filtering.NoFiltering)

  def consistency(cl: ConsistencyLevel): SCSelectOneStatement = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCSelectOneStatement = copy(cassConsistency = CassConsistency.removeConsistency)
}

final case class SCSelectItStatement(
    protected val selectBlock: QueryBuildingBlock,
    protected val whereBlock: QueryBuildingBlock,
    protected val filteringBlock: Filtering,
    private val limitBlock: Limit,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCSelectStatement[Iterator](SCSelectStatement.mkIteratorResponse, limitBlock) {
  def limit(n: Int): SCSelectItStatement = copy(limitBlock = Limit.LimitN(n))
  def noLimit: SCSelectItStatement = copy(limitBlock = Limit.NoLimit)

  def allowFiltering: SCSelectItStatement = copy(filteringBlock = Filtering.AllowFiltering)
  def noAllowFiltering: SCSelectItStatement = copy(filteringBlock = Filtering.NoFiltering)

  def consistency(cl: ConsistencyLevel): SCSelectItStatement = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCSelectItStatement = copy(cassConsistency = CassConsistency.removeConsistency)
}

object SCSelectStatement {
  def mkIteratorResponse(rs: ResultSet): Iterator[Row] = {
    import scala.collection.JavaConverters._
    rs.iterator.asScala
  }
  def mkOptionResponse(rs: ResultSet): Option[Row] = Option(rs.one())

  def apply[S : CCCassFormatEncoder, Q : CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    SCSelectItStatement(CCBlockSelect[S](Preamble("SELECT", keyspace, table)), CCBlockWhere(where), Filtering.NoFiltering, Limit.NoLimit, CassConsistency.Default)(sSession)

  def applyOne[S : CCCassFormatEncoder, Q : CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    SCSelectOneStatement(CCBlockSelect[S](Preamble("SELECT", keyspace, table)), CCBlockWhere(where), Filtering.NoFiltering, CassConsistency.Default)(sSession)
}

trait SCRaw {
  protected def rawBlock: Raw
  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(rawBlock)
}
final case class SCRawStatement private[scsession] (
    protected val rawBlock: Raw,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCRaw with SCStatement[ResultSet] with SCBatchStatement.Batchable {
  def consistency(cl: ConsistencyLevel): SCRawStatement = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCRawStatement = copy(cassConsistency = CassConsistency.removeConsistency)

  protected def mkResponse(rs: ResultSet): ResultSet = rs
}
final case class SCRawSelectStatement[F[_]] private[scsession] (
    private val _mkResponse: ResultSet => F[Row],
    protected val rawBlock: Raw,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCRaw with SCStatement[F[Row]] {
  protected def mkResponse(rs: ResultSet): F[Row] = _mkResponse(rs)

  def consistency(cl: ConsistencyLevel): SCRawSelectStatement[F] = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCRawSelectStatement[F] = copy(cassConsistency = CassConsistency.removeConsistency)
}

object SCRaw {
  def apply(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawStatement = SCRawStatement(Raw(strRepr, anyrefArgs), CassConsistency.Default)(sSession)
  def applyIterator(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawSelectStatement[Iterator] = new SCRawSelectStatement[Iterator](SCSelectStatement.mkIteratorResponse, Raw(strRepr, anyrefArgs), CassConsistency.Default)(sSession)
  def applyOne(strRepr: String, anyrefArgs: List[AnyRef], sSession: ScalaSession): SCRawSelectStatement[Option] = new SCRawSelectStatement[Option](SCSelectStatement.mkOptionResponse, Raw(strRepr, anyrefArgs), CassConsistency.Default)(sSession)
}

final case class SCBatchStatement private (
    private val statements: List[SCStatement.SCBatchableStatement],
    private val usingBlock: TTLTimestamp,
    private val batchType: BatchStatement.Type,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  import SCStatement.{ RightBiasedEither, BiMappableFuture, resultSetFutureToScalaFuture, SCBatchableStatement }
  import SCBatchStatement.ListEitherTraverse

  protected def mkResponse(rs: ResultSet): ResultSet = throw new NotImplementedError("implementing execute/executeAsync directly -- not needed")

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = throw new NotImplementedError("batch uses the cassandra session directly -- don't use me!")

  override def toString: String = (for {
    tup <- statements.traverseU(_.stringifyQuery)
    queryStatements = tup.map(t => s"    $t;").mkString("\n")
    stringified = s"""
                     |  BEGIN $batchType BATCH
                     |$queryStatements
                     |  APPLY BATCH;
                     |${cassConsistency.level.fold("")(cl => s"  <CONSISTENCY $cl>\n")}""".stripMargin
  } yield s"${getClass.getSimpleName}($stringified)")
    .valueOr(ex => s"problem generating statement: $ex")

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private[scalacass] def mkBatch: Result[BatchStatement] = statements.traverseU(_.asBatch).map { ss =>
    import scala.collection.JavaConverters._

    val bs = new BatchStatement(batchType)
    bs.addAll(ss.asJava)
    val scl = cassConsistency.level.getOrElse(sSession.session.getCluster.getConfiguration.getQueryOptions.getSerialConsistencyLevel)
    bs.setSerialConsistencyLevel(scl)
    bs
  }
  override def execute(): Result[ResultSet] = mkBatch.map(sSession.session.execute)

  override def executeAsync()(implicit ec: ExecutionContext): Future[Result[ResultSet]] =
    mkBatch.fold(Future.failed, bs => (sSession.session.executeAsync(bs): Future[ResultSet]).attempt)

  def +(batchable: SCBatchableStatement): SCBatchStatement = copy(statements = statements :+ batchable)
  def ++(batchables: List[SCBatchableStatement]): SCBatchStatement = copy(statements = statements ++ batchables)
  def ++(otherStatement: SCBatchStatement): SCBatchStatement = copy(statements = statements ++ otherStatement.statements)
  def and(batchables: SCBatchableStatement*): SCBatchStatement = copy(statements = statements ++ batchables)

  def withBatchType(batchType: BatchStatement.Type): SCBatchStatement = copy(batchType = batchType)

  def consistency(cl: ConsistencyLevel): SCBatchStatement = copy(cassConsistency = CassConsistency.addConsistency(cl))
  def defaultConsistency: SCBatchStatement = copy(cassConsistency = CassConsistency.removeConsistency)
}
object SCBatchStatement {
  trait Batchable { this: SCStatement[ResultSet] =>
    import SCStatement.RightBiasedEither

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def asBatch: Result[BoundStatement] = buildQuery.flatMap { case (queryStr, anyrefArgs) =>
      sSession.getFromCacheOrElse(queryStr, sSession.session.prepare(queryStr))
        .map { p =>
          p.setConsistencyLevel(null)
          p.bind(anyrefArgs: _*)
        }
    }

  }

  def apply(statements: List[SCStatement.SCBatchableStatement], sSession: ScalaSession): SCBatchStatement =
    new SCBatchStatement(statements, TTLTimestamp.Neither, BatchStatement.Type.LOGGED, CassConsistency.Default)(sSession)

  // should be `private`, but the compiler thinks this is not being used (which it is), so setting to `protected` to work around bug
  protected implicit class ListEitherTraverse[LV](val list: List[LV]) extends AnyVal {
    def traverseU[L, R](f: LV => Either[L, R]): Either[L, List[R]] = {
      val builder = List.newBuilder[R]

      @scala.annotation.tailrec
      def trav(li: List[LV]): Either[L, List[R]] = li.headOption.map(f) match {
        case Some(Left(l)) => Left(l)
        case Some(Right(r)) =>
          builder += r; trav(li.tail)
        case None => Right(builder.result)
      }
      trav(list)
    }
  }

}

final case class SCCreateKeyspaceStatement private (
    private val createKeyspaceBlock: CreateKeyspace,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(createKeyspaceBlock)

  def ifNotExists: SCCreateKeyspaceStatement =
    copy(createKeyspaceBlock = CreateKeyspace(createKeyspaceBlock.keyspace, If.IfNotExists, createKeyspaceBlock.properties))
  def noConditional: SCCreateKeyspaceStatement =
    copy(createKeyspaceBlock = CreateKeyspace(createKeyspaceBlock.keyspace, If.NoConditional, createKeyspaceBlock.properties))
}
object SCCreateKeyspaceStatement {
  def apply(keyspace: String, properties: String, sSession: ScalaSession): SCCreateKeyspaceStatement = new SCCreateKeyspaceStatement(CreateKeyspace(keyspace, If.NoConditional, properties), CassConsistency.Default)(sSession)
}
final case class SCDropKeyspaceStatement private (
    private val dropKeyspaceBlock: DropKeyspace,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(dropKeyspaceBlock)
}
object SCDropKeyspaceStatement {
  def apply(keyspace: String, sSession: ScalaSession): SCDropKeyspaceStatement = new SCDropKeyspaceStatement(DropKeyspace(keyspace), CassConsistency.Default)(sSession)
}

final case class SCCreateTableStatement private (
    private val createTable: QueryBuildingBlock,
    private val tableProperties: TableProperties,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(createTable, tableProperties)

  def `with`(properties: String): SCCreateTableStatement = copy(tableProperties = TableProperties.With(properties))
}
object SCCreateTableStatement {
  def apply[T : CCCassFormatEncoder](keyspace: String, name: String, numPartitionKeys: Int, numClusteringKeys: Int, sSession: ScalaSession): SCCreateTableStatement =
    new SCCreateTableStatement(CreateTable(keyspace, name, numPartitionKeys, numClusteringKeys), TableProperties.NoProperties, CassConsistency.Default)(sSession)
}
final case class SCTruncateTableStatement private (
    private val truncateTableBlock: TruncateTable,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(truncateTableBlock)
}
object SCTruncateTableStatement {
  def apply(keyspace: String, table: String, sSession: ScalaSession): SCTruncateTableStatement = new SCTruncateTableStatement(TruncateTable(keyspace, table), CassConsistency.Default)(sSession)
}
final case class SCDropTableStatement private (
    private val dropTableBlock: DropTable,
    protected val cassConsistency: CassConsistency
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
  protected def mkResponse(rs: ResultSet): ResultSet = rs

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(dropTableBlock)
}
object SCDropTableStatement {
  def apply(keyspace: String, table: String, sSession: ScalaSession): SCDropTableStatement = new SCDropTableStatement(DropTable(keyspace, table), CassConsistency.Default)(sSession)
}
