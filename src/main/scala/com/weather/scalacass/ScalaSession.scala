package com.weather.scalacass

import java.util.concurrent.Callable

import com.datastax.driver.core._
import com.google.common.cache.CacheBuilder
import exceptions.QueryExecutionException
import com.google.common.util.concurrent.{FutureCallback, Futures}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

sealed trait Batch extends Product with Serializable
final case class UpdateBatch[T, S](table: String, updateable: T, query: S)(implicit val tEncoder: CCCassFormatEncoder[T], val sEncoder: CCCassFormatEncoder[S]) extends Batch
final case class DeleteBatch[T](table: String, item: T)(implicit val tEncoder: CCCassFormatEncoder[T]) extends Batch
final case class InsertBatch[T](table: String, item: T)(implicit val tEncoder: CCCassFormatEncoder[T]) extends Batch
final case class RawBatch(query: String, anyrefArgs: AnyRef*) extends Batch

object ScalaSession {
  def apply(keyspace: String, keyspaceProperties: String = "")(implicit session: Session): ScalaSession = {
    if (keyspaceProperties.nonEmpty)
      session.execute(s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH $keyspaceProperties")
    new ScalaSession(keyspace)
  }

  private implicit def resultSetFutureToScalaFuture(f: ResultSetFuture): Future[ResultSet] = {
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

  //  class AllowFilteringNotEnabledException(m: String) extends QueryExecutionException(m)
  class WrongPrimaryKeySizeException(m: String) extends QueryExecutionException(m)

  final case class Star(`*`: Nothing)
  object Star {
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
    implicit val ccCassEncoder: CCCassFormatEncoder[Star] = shapeless.cachedImplicit
  }
  final case class NoQuery()
  object NoQuery {
    @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
    implicit val ccCassEncoder: CCCassFormatEncoder[NoQuery] = shapeless.cachedImplicit
  }
}

class ScalaSession(val keyspace: String)(implicit val session: Session) {
  import ScalaSession.{resultSetFutureToScalaFuture, WrongPrimaryKeySizeException, Fn02Callable, Star}

  //  private[this] def keyspaceMeta = session.getCluster.getMetadata.getKeyspace(keyspace)

  //  private[this] val queryCache = new LRUCache[Set[String], PreparedStatement](100)
  private[this] val queryCache = CacheBuilder.newBuilder().maximumSize(1000).build[Set[String], PreparedStatement]()

  private[this] def clean[T: CCCassFormatEncoder](toClean: T): (List[String], List[AnyRef]) =
    clean(implicitly[CCCassFormatEncoder[T]].encode(toClean) match {
      case Right(res) => res
      case Left(exc)  => throw exc
    })
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any", "org.brianmckenna.wartremover.warts.AsInstanceOf", "org.brianmckenna.wartremover.warts.IsInstanceOf"))
  private[this] def clean[T](toClean: List[(String, AnyRef)]): (List[String], List[AnyRef]) = toClean.filter(_._2 match {
    case None => false
    case _    => true
  }).map {
    case (str, Some(anyref: AnyRef)) => (str, anyref)
    case other                       => other
  }.unzip

  def close(): Unit = session.close()

  def dropKeyspace(): ResultSet = session.execute(s"DROP KEYSPACE $keyspace")

  def createTable[T: CCCassFormatEncoder](name: String, numPartitionKeys: Int, numClusteringKeys: Int, tableProperties: String = ""): ResultSet = {
    if (numPartitionKeys <= 0) throw new WrongPrimaryKeySizeException("must include at least one partition key")
    val allColumns = implicitly[CCCassFormatEncoder[T]].namesAndTypes
    if (numPartitionKeys + numClusteringKeys > allColumns.length) throw new WrongPrimaryKeySizeException(s"too many partition+clustering keys for table $name")
    val (partitionKeys, rest) = allColumns.splitAt(numPartitionKeys)
    val clusteringKeys = rest.take(numClusteringKeys)
    val pk = s"${partitionKeys.map(_._1).mkString("(", ", ", ")")}"
    val fullKey = if (numClusteringKeys > 0) s"($pk, ${clusteringKeys.map(_._1).mkString(", ")})" else s"($pk)"
    val withClause = if (tableProperties.nonEmpty) s" WITH $tableProperties" else ""
    session.execute(s"CREATE TABLE $keyspace.$name (${allColumns.map(nt => s"${nt._1} ${nt._2}").mkString(", ")}, PRIMARY KEY $fullKey)" + withClause)
  }

  def truncateTable(table: String): ResultSet = session.execute(s"TRUNCATE TABLE $keyspace.$table")
  def dropTable(table: String): ResultSet = session.execute(s"DROP TABLE $keyspace.$table")

  private[this] def prepareInsert[T: CCCassFormatEncoder](table: String, insertable: T): BoundStatement = {
    val (strArgs, anyrefArgs) = clean(insertable)
    val prepared = queryCache.get(
      strArgs.toSet + table + "INSERT",
      session.prepare(s"INSERT INTO $keyspace.$table ${strArgs.mkString("(", ",", ")")} VALUES ${List.fill(anyrefArgs.length)("?").mkString("(", ",", ")")}")
    )
    prepared.bind(anyrefArgs: _*)
  }

  // includeColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
  def insert[T: CCCassFormatEncoder](table: String, insertable: T): ResultSet = session.execute(prepareInsert(table, insertable))
  def insertAsync[T: CCCassFormatEncoder](table: String, insertable: T): Future[ResultSet] = session.executeAsync(prepareInsert(table, insertable))

  private[this] def prepareUpdate[T: CCCassFormatEncoder, S: CCCassFormatEncoder](table: String, updateable: T, query: S): BoundStatement = {
    val (updateStrArgs, updateAnyrefArgs) = clean(updateable)
    val (queryStrArgs, queryAnyrefArgs) = clean(query)
    val prepared = queryCache.get(
      updateStrArgs.toSet ++ queryStrArgs.toSet + table + "UPDATE",
      session.prepare(s"UPDATE $keyspace.$table SET ${updateStrArgs.map(_ + "=?").mkString(",")} WHERE ${queryStrArgs.map(_ + "=?").mkString(" AND ")}")
    )
    prepared.bind(updateAnyrefArgs ++ queryAnyrefArgs: _*)
  }

  def update[T: CCCassFormatEncoder, S: CCCassFormatEncoder](table: String, updateable: T, query: S): ResultSet =
    session.execute(prepareUpdate(table, updateable, query))
  def updateAsync[T: CCCassFormatEncoder, S: CCCassFormatEncoder](table: String, updateable: T, query: S): Future[ResultSet] =
    session.executeAsync(prepareUpdate(table, updateable, query))

  private[this] def prepareDelete[T: CCCassFormatEncoder](table: String, deletable: T): BoundStatement = {
    val (strArgs, anyrefArgs) = clean(deletable)
    val prepared = queryCache.get(
      strArgs.toSet + table + "DELETE",
      session.prepare(s"DELETE FROM $keyspace.$table WHERE ${strArgs.map(_ + "=?").mkString(" AND ")}")
    )
    prepared.bind(anyrefArgs: _*)
  }
  // includeColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
  def delete[T: CCCassFormatEncoder](table: String, deletable: T): ResultSet =
    session.execute(prepareDelete(table, deletable))
  def deleteAsync[T: CCCassFormatEncoder](table: String, deletable: T): Future[ResultSet] =
    session.executeAsync(prepareDelete(table, deletable))

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any"))
  def prepareBatch(batches: Seq[Batch], batchType: BatchStatement.Type): BatchStatement = {
    val batch = new BatchStatement(batchType)
    batches.foreach {
      case d @ DeleteBatch(table, item)         => batch.add(prepareDelete(table, item)(d.tEncoder))
      case u @ UpdateBatch(table, item, query)  => batch.add(prepareUpdate(table, item, query)(u.tEncoder, u.sEncoder))
      case i @ InsertBatch(table, item)         => batch.add(prepareInsert(table, item)(i.tEncoder))
      case r @ RawBatch(query, anyrefArgs @ _*) => batch.add(prepareRawStatement(query, anyrefArgs))
    }
    batch
  }
  def batch(batches: Seq[Batch], batchType: BatchStatement.Type = BatchStatement.Type.LOGGED): ResultSet = session.execute(prepareBatch(batches, batchType))
  def batchAsync(batches: Seq[Batch], batchType: BatchStatement.Type = BatchStatement.Type.LOGGED): Future[ResultSet] = session.executeAsync(prepareBatch(batches, batchType))

  private[this] def prepareSelect[Sub: CCCassFormatEncoder, Query: CCCassFormatEncoder](table: String, selectable: Query, allowFiltering: Boolean, limit: Long) = {
    val sStrArgs = CCCassFormatEncoder[Sub].namesAndTypes.map(_._1)
    val (qStrArgs, qAnyRefArgs) = clean(selectable)
    val prepared = queryCache.get(sStrArgs.toSet ++ qStrArgs.toSet + table + "SELECT", {
      val whereClauseStr = if (qStrArgs.nonEmpty) s" WHERE ${qStrArgs.map(_ + "=?").mkString(" AND ")}" else ""
      val limitStr = if (limit > 0) s" LIMIT $limit" else ""
      val filteringStr = if (allowFiltering) s" ALLOW FILTERING" else ""
      session.prepare(s"SELECT ${sStrArgs.mkString(", ")} FROM $keyspace.$table" + whereClauseStr + limitStr + filteringStr)
    })
    prepared.bind(qAnyRefArgs: _*)
  }

  def select[T: CCCassFormatEncoder](table: String, selectable: T, allowFiltering: Boolean = false, limit: Long = 0): Iterator[Row] =
    session.execute(prepareSelect[Star, T](table, selectable, allowFiltering, limit)).iterator.asScala
  def selectAsync[T: CCCassFormatEncoder](table: String, selectable: T, allowFiltering: Boolean = false, limit: Long = 0): Future[Iterator[Row]] =
    session.executeAsync(prepareSelect[Star, T](table, selectable, allowFiltering, limit)).map(_.iterator.asScala)
  def selectOne[T: CCCassFormatEncoder](table: String, selectable: T, allowFiltering: Boolean = false): Option[Row] =
    Option(session.execute(prepareSelect[Star, T](table, selectable, allowFiltering, 0)).one())
  def selectOneAsync[T: CCCassFormatEncoder](table: String, selectable: T, allowFiltering: Boolean = false): Future[Option[Row]] =
    session.executeAsync(prepareSelect[Star, T](table, selectable, allowFiltering, 0)).map(rs => Option(rs.one()))

  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
  def selectColumns[Sub] = sch.asInstanceOf[SelectColumnsHelper[Sub]]
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
  def selectColumnsAsync[Sub] = scah.asInstanceOf[SelectColumnsAsyncHelper[Sub]]
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
  def selectColumnsOne[Sub] = scoh.asInstanceOf[SelectColumnsOneHelper[Sub]]
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.AsInstanceOf"))
  def selectColumnsOneAsync[Sub] = scoah.asInstanceOf[SelectColumnsOneAsyncHelper[Sub]]

  final class SelectColumnsHelper[Sub] {
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, allowFiltering: Boolean, limit: Long)(implicit se: CCCassFormatEncoder[Sub]): Iterator[Row] =
      session.execute(prepareSelect[Sub, Query](table, selectable, allowFiltering, limit)).iterator.asScala
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, limit: Long)(implicit se: CCCassFormatEncoder[Sub]): Iterator[Row] =
      apply(table, selectable, false, limit)
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, allowFiltering: Boolean)(implicit se: CCCassFormatEncoder[Sub]): Iterator[Row] =
      apply(table, selectable, allowFiltering, 0)
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query)(implicit se: CCCassFormatEncoder[Sub]): Iterator[Row] =
      apply(table, selectable, false, 0)
  }

  final class SelectColumnsAsyncHelper[Sub] {
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, allowFiltering: Boolean, limit: Long)(implicit se: CCCassFormatEncoder[Sub]): Future[Iterator[Row]] =
      session.executeAsync(prepareSelect[Sub, Query](table, selectable, allowFiltering, limit)).map(_.iterator.asScala)
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, limit: Long = 0)(implicit se: CCCassFormatEncoder[Sub]): Future[Iterator[Row]] =
      apply(table, selectable, false, limit)
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, allowFiltering: Boolean)(implicit se: CCCassFormatEncoder[Sub]): Future[Iterator[Row]] =
      apply(table, selectable, allowFiltering, 0)
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query)(implicit se: CCCassFormatEncoder[Sub]): Future[Iterator[Row]] =
      apply(table, selectable, false, 0)
  }

  final class SelectColumnsOneHelper[Sub] {
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, allowFiltering: Boolean)(implicit se: CCCassFormatEncoder[Sub]): Option[Row] =
      Option(session.execute(prepareSelect[Sub, Query](table, selectable, allowFiltering, 0)).one())
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query)(implicit se: CCCassFormatEncoder[Sub]): Option[Row] =
      apply(table, selectable, false)
  }

  final class SelectColumnsOneAsyncHelper[Sub] {
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query, allowFiltering: Boolean)(implicit se: CCCassFormatEncoder[Sub]): Future[Option[Row]] =
      session.executeAsync(prepareSelect[Sub, Query](table, selectable, allowFiltering, 0)).map(rs => Option(rs.one()))
    def apply[Query: CCCassFormatEncoder](table: String, selectable: Query)(implicit se: CCCassFormatEncoder[Sub]): Future[Option[Row]] =
      apply(table, selectable, false)
  }

  private[this] val sch = new SelectColumnsHelper[Nothing]
  private[this] val scah = new SelectColumnsAsyncHelper[Nothing]
  private[this] val scoh = new SelectColumnsOneHelper[Nothing]
  private[this] val scoah = new SelectColumnsOneAsyncHelper[Nothing]

  private[this] def prepareRawStatement(query: String, anyrefArgs: Seq[AnyRef]) = {
    val prepared = queryCache.get(Set(query), session.prepare(query))
    prepared.bind(anyrefArgs: _*)
  }

  def rawStatement(query: String, anyrefArgs: AnyRef*): ResultSet =
    session.execute(prepareRawStatement(query, anyrefArgs))
  def rawStatementAsync(query: String, anyrefArgs: AnyRef*): Future[ResultSet] =
    session.executeAsync(prepareRawStatement(query, anyrefArgs))
  def rawSelect(query: String, anyrefArgs: AnyRef*): Iterator[Row] =
    session.execute(prepareRawStatement(query, anyrefArgs)).iterator.asScala
  def rawSelectAsync(query: String, anyrefArgs: AnyRef*): Future[Iterator[Row]] =
    session.executeAsync(prepareRawStatement(query, anyrefArgs)).map(_.iterator.asScala)
  def rawSelectOne(query: String, anyrefArgs: AnyRef*): Option[Row] =
    Option(session.execute(prepareRawStatement(query, anyrefArgs)).one())
  def rawSelectOneAsync(query: String, anyrefArgs: AnyRef*): Future[Option[Row]] =
    session.executeAsync(prepareRawStatement(query, anyrefArgs)).map(rs => Option(rs.one()))
}