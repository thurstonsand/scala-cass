package com.weather.scalacass
package scsession

import com.datastax.driver.core.{BoundStatement, ResultSet, Row}
import com.weather.scalacass.scsession.QueryBuildingBlock._

import scala.concurrent.Future

object SCStatement {
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
}
trait SCStatement[Response] {
  import SCStatement.RightBiasedEither
  import ScalaSession.resultSetFutureToScalaFuture

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

case class SCInsertStatement(
    private val preableBlock: Preamble,
    private val insertBlock: QueryBuildingBlock,
    private val ifBlock: If = If.NoConditional,
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
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

case class SCUpdateStatement(
    private val preamble: Preamble,
    private val updateBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither,
    private val ifBlock: If = If.NoConditional
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
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

case class SCDeleteStatement(
    private val deleteBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val usingBlock: TTLTimestamp = TTLTimestamp.Neither,
    private val ifBlock: If = If.NoConditional
)(implicit protected val sSession: ScalaSession) extends SCStatement[ResultSet] {
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

case class SCSelectStatement(
    private val selectBlock: QueryBuildingBlock,
    private val whereBlock: QueryBuildingBlock,
    private val limitBlock: Limit = Limit.NoLimit,
    private val filteringBlock: Filtering = Filtering.NoFiltering
)(implicit val sSession: ScalaSession) extends SCStatement[Iterator[Row]] {
  protected def mkResponse(rs: ResultSet): Iterator[Row] = {
    import scala.collection.JavaConverters._
    rs.iterator.asScala
  }

  protected def queryBuildingBlocks: Seq[QueryBuildingBlock] = Seq(selectBlock, whereBlock, limitBlock, filteringBlock)

  def limit(n: Int): SCSelectStatement = copy(limitBlock = Limit.LimitN(n))
  def noLimit: SCSelectStatement = copy(limitBlock = Limit.NoLimit)

  def allowFiltering: SCSelectStatement = copy(filteringBlock = Filtering.AllowFiltering)
  def noAllowFiltering: SCSelectStatement = copy(filteringBlock = Filtering.NoFiltering)
}
object SCSelectStatement {
  def apply[S: CCCassFormatEncoder, Q: CCCassFormatEncoder](keyspace: String, table: String, where: Q, sSession: ScalaSession) =
    new SCSelectStatement(CCBlockSelect[S](Preamble("SELECT", keyspace, table)), CCBlockWhere(where))(sSession)
}