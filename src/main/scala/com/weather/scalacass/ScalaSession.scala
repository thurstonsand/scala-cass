package com.weather.scalacass

import java.util.concurrent.Callable

import com.datastax.driver.core._
import com.google.common.cache.CacheBuilder
import com.weather.scalacass.scsession._
import com.google.common.util.concurrent.{FutureCallback, Futures}
import com.weather.scalacass.scsession.SCBatchStatement.Batchable

import scala.concurrent.{Future, Promise}

sealed trait Batch extends Product with Serializable
final case class UpdateBatch[T, S](table: String, updateable: T, query: S, ttl: Option[Int] = None)(implicit val tEncoder: CCCassFormatEncoder[T], val sEncoder: CCCassFormatEncoder[S]) extends Batch
final case class DeleteBatch[T](table: String, item: T)(implicit val tEncoder: CCCassFormatEncoder[T]) extends Batch
final case class InsertBatch[T](table: String, item: T, ttl: Option[Int] = None)(implicit val tEncoder: CCCassFormatEncoder[T]) extends Batch
final case class RawBatch(query: String, anyrefArgs: AnyRef*) extends Batch

object ScalaSession {
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

  private implicit def Fn02Callable[V](f: => V): Callable[V] = new Callable[V] {
    override def call(): V = f
  }

  final case class Star(`*`: Nothing)
  object Star {
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
    implicit val ccCassEncoder: CCCassFormatEncoder[Star] = CCCassFormatEncoder.derive[Star]
  }
  final case class NoQuery()
  object NoQuery {
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
    implicit val ccCassEncoder: CCCassFormatEncoder[NoQuery] = CCCassFormatEncoder.derive[NoQuery]
  }
  final case class NoUpdate()
  object NoUpdate {
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
    implicit val ccCassEncoder: CCCassFormatEncoder[NoUpdate] = CCCassFormatEncoder.derive[NoUpdate]
  }

  sealed trait UpdateBehavior[F[_], A] {
    def coll: F[A]
    def withQuery(name: String): String
  }
  object UpdateBehavior {
    final case class Add[F[_], A] private (coll: F[A]) extends UpdateBehavior[F, A] {
      def withQuery(name: String) = s"$name=$name+?"
    }
    object Add {
      def apply[A](coll: List[A]) = new Add(coll)
      def apply[A](coll: Set[A]) = new Add(coll)
      implicit def liftList[A](l: List[A]): Add[List, A] = apply(l)
      implicit def listSet[A](s: Set[A]): Add[Set, A] = apply(s)
    }
    final case class Subtract[F[_], A] private (coll: F[A]) extends UpdateBehavior[F, A] {
      def withQuery(name: String) = s"$name=$name-?"
    }
    object Subtract {
      def apply[A](coll: List[A]) = new Subtract(coll)
      def apply[A](coll: Set[A]) = new Subtract(coll)
      implicit def liftList[A](l: List[A]): Subtract[List, A] = apply(l)
      implicit def listSet[A](s: Set[A]): Subtract[Set, A] = apply(s)
    }
    final case class Replace[F[_], A] private (coll: F[A]) extends UpdateBehavior[F, A] {
      def withQuery(name: String) = s"$name=?"
    }
    object Replace {
      def apply[A](coll: List[A]) = new Replace(coll)
      def apply[A](coll: Set[A]) = new Replace(coll)
      implicit def liftList[A](l: List[A]): Replace[List, A] = apply(l)
      implicit def listSet[A](s: Set[A]): Replace[Set, A] = apply(s)
    }
  }
}

case class ScalaSession(keyspace: String)(implicit val session: Session) {
  import ScalaSession.{Fn02Callable, Star}

  //  private[this] val queryCache = new LRUCache[Set[String], PreparedStatement](100)
  private[this] val queryCache = CacheBuilder.newBuilder().maximumSize(1000).build[Set[String], PreparedStatement]()

  def close(): Unit = session.close()

  def createKeyspace(properties: String): ResultSet = session.execute(s"CREATE KEYSPACE $keyspace WITH $properties")

  def dropKeyspace(): ResultSet = session.execute(s"DROP KEYSPACE $keyspace")

  def createTable[T: CCCassFormatEncoder](name: String, numPartitionKeys: Int, numClusteringKeys: Int): SCCreateTableStatement =
    SCCreateTableStatement[T](keyspace, name, numPartitionKeys, numClusteringKeys, this)

  def truncateTable(table: String): ResultSet = session.execute(s"TRUNCATE TABLE $keyspace.$table")
  def dropTable(table: String): ResultSet = session.execute(s"DROP TABLE $keyspace.$table")

  private[scalacass] def getFromCacheOrElse(key: String, statement: => PreparedStatement) = queryCache.get(Set(key), statement)
  def invalidateCache(): Unit = queryCache.invalidateAll()

  def insert[I: CCCassFormatEncoder](table: String, insertable: I): SCInsertStatement = SCInsertStatement(keyspace, table, insertable, this)

  def update[U: CCCassFormatEncoder, Q: CCCassFormatEncoder](table: String, updateable: U, query: Q): SCUpdateStatement =
    SCUpdateStatement(keyspace, table, updateable, query, this)

  def delete[D] = dh.asInstanceOf[DeleteHelper[D]]
  def deleteStar = dh.asInstanceOf[DeleteHelper[Star]]
  private[this] val dh = new DeleteHelper[Nothing]

  final class DeleteHelper[D] {
    def apply[Q: CCCassFormatEncoder](table: String, where: Q)(implicit dEncoder: CCCassFormatEncoder[D]): SCDeleteStatement =
      SCDeleteStatement[D, Q](keyspace, table, where, ScalaSession.this)
  }

  def batch(batches: Seq[Batchable]): SCBatchStatement = SCBatchStatement(batches, this)

  def select[S] = sh.asInstanceOf[SelectHelper[S]]
  def selectStar = sh.asInstanceOf[SelectHelper[Star]]
  private[this] val sh = new SelectHelper[Nothing]

  final class SelectHelper[S] {
    def apply[Q: CCCassFormatEncoder](table: String, where: Q)(implicit sEncoder: CCCassFormatEncoder[S]): SCSelectStatement[Iterator] =
      SCSelectStatement.apply[S, Q](keyspace, table, where, ScalaSession.this)
  }

  def selectOne[S] = soh.asInstanceOf[SelectOneHelper[S]]
  def selectOneStar = soh.asInstanceOf[SelectOneHelper[Star]]
  private[this] val soh = new SelectOneHelper[Nothing]

  final class SelectOneHelper[S] {
    def apply[Q: CCCassFormatEncoder](table: String, where: Q)(implicit sEncoder: CCCassFormatEncoder[S]): SCSelectStatement[Option] =
      SCSelectStatement.applyOne[S, Q](keyspace, table, where, ScalaSession.this)
  }

  def rawStatement(query: String, anyrefArgs: AnyRef*): SCRawStatement[ResultSet] =
    SCRawStatement.apply(query, anyrefArgs.toList, this)
  def rawSelect(query: String, anyrefArgs: AnyRef*): SCRawStatement[Iterator[Row]] =
    SCRawStatement.applyIterator(query, anyrefArgs.toList, this)
  def rawSelectOne(query: String, anyrefArgs: AnyRef*): SCRawStatement[Option[Row]] =
    SCRawStatement.applyOne(query, anyrefArgs.toList, this)
}