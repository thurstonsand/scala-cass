package com.weather.scalacass

import java.util.concurrent.Callable

import com.datastax.driver.core._
import com.google.common.cache.{ Cache, CacheBuilder }
import com.weather.scalacass.scsession._
import com.typesafe.scalalogging.StrictLogging

object ScalaSession {
  private implicit def Fn02Callable[V](f: => V): Callable[V] = new Callable[V] {
    override def call(): V = f
  }

  final case class Star(`*`: Nothing)
  object Star {
    implicit val ccCassEncoder: CCCassFormatEncoder[Star] = CCCassFormatEncoder.derive
  }
  final case class NoQuery()
  object NoQuery {
    implicit val ccCassEncoder: CCCassFormatEncoder[NoQuery] = CCCassFormatEncoder.derive
  }
  final case class NoUpdate()
  object NoUpdate {
    implicit val ccCassEncoder: CCCassFormatEncoder[NoUpdate] = CCCassFormatEncoder.derive
  }

  sealed trait UpdateBehavior[F[_], A] {
    def coll: F[A]
    def withQuery(name: String): String
  }
  object UpdateBehavior {
    final case class Add[F[_], A] private (coll: F[A]) extends UpdateBehavior[F, A] {
      def withQuery(name: String): String = s"$name=$name+?"
    }
    object Add {
      def apply[A](coll: List[A]): Add[List, A] = new Add(coll)
      def apply[A](coll: Set[A]): Add[Set, A] = new Add(coll)
      implicit def liftList[A](l: List[A]): Add[List, A] = apply(l)
      implicit def listSet[A](s: Set[A]): Add[Set, A] = apply(s)
    }
    final case class Subtract[F[_], A] private (coll: F[A]) extends UpdateBehavior[F, A] {
      def withQuery(name: String): String = s"$name=$name-?"
    }
    object Subtract {
      def apply[A](coll: List[A]): Subtract[List, A] = new Subtract(coll)
      def apply[A](coll: Set[A]): Subtract[Set, A] = new Subtract(coll)
      implicit def liftList[A](l: List[A]): Subtract[List, A] = apply(l)
      implicit def listSet[A](s: Set[A]): Subtract[Set, A] = apply(s)
    }
    final case class Replace[F[_], A] private (coll: F[A]) extends UpdateBehavior[F, A] {
      def withQuery(name: String): String = s"$name=?"
    }
    object Replace {
      def apply[A](coll: List[A]) = new Replace(coll)
      def apply[A](coll: Set[A]) = new Replace(coll)
      implicit def liftList[A](l: List[A]): Replace[List, A] = apply(l)
      implicit def listSet[A](s: Set[A]): Replace[Set, A] = apply(s)
    }
  }
}

final case class ScalaSession(keyspace: String)(implicit val session: Session) extends StrictLogging {
  import ScalaSession.{ Fn02Callable, Star, NoQuery }

  private[this] val queryCache: Cache[String, Either[Throwable, PreparedStatement]] =
    CacheBuilder.newBuilder().maximumSize(1000).build[String, Either[Throwable, PreparedStatement]]()

  private[scalacass] def getFromCacheOrElse(key: String, statement: => PreparedStatement) = {
    def onCacheMiss: Either[Throwable, PreparedStatement] = {
      logger.debug(s"cache miss for key $key")
      try Right(statement) catch { case ex: Throwable => Left(ex) }
    }
    queryCache.get(key, onCacheMiss)
  }
  def invalidateCache(): Unit = queryCache.invalidateAll()

  def close(): Unit = session.close()

  def createKeyspace(properties: String): SCCreateKeyspaceStatement = SCCreateKeyspaceStatement(keyspace, properties, this)

  def dropKeyspace(): SCDropKeyspaceStatement = SCDropKeyspaceStatement(keyspace, this)

  def createTable[T : CCCassFormatEncoder](name: String, numPartitionKeys: Int, numClusteringKeys: Int): SCCreateTableStatement =
    SCCreateTableStatement[T](keyspace, name, numPartitionKeys, numClusteringKeys, this)

  def truncateTable(table: String): SCTruncateTableStatement = SCTruncateTableStatement(keyspace, table, this)
  def dropTable(table: String): SCDropTableStatement = SCDropTableStatement(keyspace, table, this)

  def insert[I : CCCassFormatEncoder](table: String, insertable: I): SCInsertStatement = SCInsertStatement(keyspace, table, insertable, this)

  def update[U : CCCassFormatEncoder, Q : CCCassFormatEncoder](table: String, updateable: U, query: Q): SCUpdateStatement =
    SCUpdateStatement(keyspace, table, updateable, query, this)

  object delete {
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def apply[D] = partiallyApplied.asInstanceOf[PartiallyApplied[D]]
    final class PartiallyApplied[D] {
      def apply[Q : CCCassFormatEncoder](table: String, where: Q)(implicit dEncoder: CCCassFormatEncoder[D]): SCDeleteStatement =
        SCDeleteStatement[D, Q](keyspace, table, where, ScalaSession.this)
    }
    private val partiallyApplied = new PartiallyApplied[Nothing]
  }
  def deleteRow = delete[NoQuery]

  def batch(batches: List[SCStatement.SCBatchableStatement]): SCBatchStatement = SCBatchStatement(batches, this)
  def batchOf(batch: SCStatement.SCBatchableStatement, batches: SCStatement.SCBatchableStatement*): SCBatchStatement =
    SCBatchStatement((batch +: batches).toList, this)

  object select {
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def apply[S] = partiallyApplied.asInstanceOf[PartiallyApplied[S]]
    final class PartiallyApplied[S] {
      def apply[Q : CCCassFormatEncoder](table: String, where: Q)(implicit sEncoder: CCCassFormatEncoder[S]): SCSelectItStatement =
        SCSelectStatement.apply[S, Q](keyspace, table, where, ScalaSession.this)
    }
    private val partiallyApplied = new PartiallyApplied[Nothing]
  }
  def selectStar = select[Star]

  object selectOne {
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def apply[S] = partiallyApplied.asInstanceOf[PartiallyApplied[S]]
    final class PartiallyApplied[S] {
      def apply[Q : CCCassFormatEncoder](table: String, where: Q)(implicit sEncoder: CCCassFormatEncoder[S]): SCSelectOneStatement =
        SCSelectStatement.applyOne[S, Q](keyspace, table, where, ScalaSession.this)
    }
    private val partiallyApplied = new PartiallyApplied[Nothing]
  }
  def selectOneStar = selectOne[Star]

  def rawStatement(query: String, anyrefArgs: AnyRef*): SCRawStatement =
    SCRaw.apply(query, anyrefArgs.toList, this)
  def rawSelect(query: String, anyrefArgs: AnyRef*): SCRawSelectStatement[Iterator] =
    SCRaw.applyIterator(query, anyrefArgs.toList, this)
  def rawSelectOne(query: String, anyrefArgs: AnyRef*): SCRawSelectStatement[Option] =
    SCRaw.applyOne(query, anyrefArgs.toList, this)
}
