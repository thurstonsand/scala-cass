package com.weather.scalacass

import com.datastax.driver.core._, exceptions.InvalidQueryException
import com.google.common.util.concurrent.{FutureCallback, Futures}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future}
import ScalaCass._

object ScalaSession {
  import scala.language.implicitConversions
  private implicit def resultSetFutureToScalaFuture(f: ResultSetFuture): Future[ResultSet] = {
    val p = Promise[ResultSet]()
    Futures.addCallback(
      f,
      new FutureCallback[ResultSet] {
        def onSuccess(r: ResultSet) = p success r; (): Unit
        def onFailure(t: Throwable) = p failure t; (): Unit
      }
    )
    p.future
  }
  def apply(keyspace: String, keyspaceProperties: Option[String] = None)(implicit session: Session): ScalaSession = {
    keyspaceProperties.foreach { prop =>
      val withClause = if (prop.length > 0) s" WITH $prop" else ""
      session.execute(s"CREATE KEYSPACE IF NOT EXISTS $keyspace" + withClause)
    }
    new ScalaSession(keyspace)
  }
}

class ScalaSession(val keyspace: String)(implicit val session: Session) {
  import ScalaSession.resultSetFutureToScalaFuture

  private[this] def keyspaceMeta = session.getCluster.getMetadata.getKeyspace(keyspace)

  private[this] val queryCache = new LRUCache[Set[String], PreparedStatement](100)

  private[this] def numParams(table: String, includeColumns: Int) = {
    val numPrimaryKeys = session.getCluster.getMetadata.getKeyspace(keyspace).getTable(table).getPrimaryKey.size
    if (includeColumns <= 0) numPrimaryKeys
    else includeColumns min numPrimaryKeys
  }

  private[this] def clean[T: CCCassFormatEncoder](toClean: T): (List[String], List[AnyRef]) =
    clean(implicitly[CCCassFormatEncoder[T]].encode(toClean).getOrThrow)
  private[this] def clean[T: CCCassFormatEncoder](toClean: T, table: String, includeColumns: Int): (List[String], List[AnyRef]) =
    clean(implicitly[CCCassFormatEncoder[T]].encode(toClean).getOrThrow.take(numParams(table, includeColumns)))
  @SuppressWarnings(Array("org.brianmckenna.wartremover.warts.Any", "org.brianmckenna.wartremover.warts.AsInstanceOf", "org.brianmckenna.wartremover.warts.IsInstanceOf"))
  private[this] def clean[T](toClean: List[(String, AnyRef)]): (List[String], List[AnyRef]) = toClean.filter(_._2 match {
    case None => false
    case _    => true
  }).map {
    case (str, Some(anyref: AnyRef)) => (str, anyref)
    case other                       => other
  }.unzip

  def dropKeyspace(): ResultSet = session.execute(s"DROP KEYSPACE $keyspace")

  def createTable[T: CCCassFormatEncoder](name: String, numPartitionKeys: Int, numClusteringKeys: Int, tableProperties: String = ""): ResultSet = {
    if (numPartitionKeys <= 0) throw new InvalidQueryException("Cassandra: need to include at least one partition key")
    val allColumns = implicitly[CCCassFormatEncoder[T]].namesAndTypes
    if (numPartitionKeys + numClusteringKeys > allColumns.length) throw new InvalidQueryException(s"Cassandra: too many partition+clustering keys for table $name")
    val (partitionKeys, rest) = allColumns.splitAt(numPartitionKeys)
    val clusteringKeys = rest.take(numClusteringKeys)
    val pk = s"${partitionKeys.map(_._1).mkString("(", ", ", ")")}"
    val fullKey = if (numClusteringKeys > 0) s"($pk, ${clusteringKeys.map(_._1).mkString(", ")})" else s"($pk)"
    val withClause = if (tableProperties.length > 0) s" WITH $tableProperties" else ""
    session.execute(s"CREATE TABLE $keyspace.$name (${allColumns.map(nt => s"${nt._1} ${nt._2}").mkString(", ")}, PRIMARY KEY $fullKey)" + withClause)
  }

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

  private[this] def prepareInsertRaw(query: String, anyrefArgs: Seq[AnyRef]) = {
    val prepared = queryCache.get(Set(query), session.prepare(query))
    prepared.bind(anyrefArgs)
  }
  def insertRaw(query: String, anyrefArgs: AnyRef*): ResultSet =
    session.execute(prepareInsertRaw(query, anyrefArgs))
  def insertRawAsync(query: String, anyrefArgs: AnyRef*): Future[ResultSet] =
    session.executeAsync(prepareInsertRaw(query, anyrefArgs))

  private[this] def prepareDelete[T: CCCassFormatEncoder](table: String, deletable: T, includeColumns: Int): BoundStatement = {
    val (strArgs, anyrefArgs) = clean(deletable, table, includeColumns)
    val prepared = queryCache.get(
      strArgs.toSet + table + "DELETE",
      session.prepare(s"DELETE FROM $keyspace.$table WHERE ${strArgs.map(s => s"$s = ?").mkString(" AND ")}")
    )
    prepared.bind(anyrefArgs: _*)
  }
  // includeColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
  def delete[T: CCCassFormatEncoder](table: String, deletable: T, includeColumns: Int = 0): ResultSet =
    session.execute(prepareDelete(table, deletable, includeColumns))
  def deleteAsync[T: CCCassFormatEncoder](table: String, deletable: T, includeColumns: Int = 0): Future[ResultSet] =
    session.executeAsync(prepareDelete(table, deletable, includeColumns))

  private[this] def prepareRawDelete(query: String, anyrefArgs: Seq[AnyRef]) = {
    val prepared = queryCache.get(Set(query), session.prepare(query))
    prepared.bind(anyrefArgs)
  }
  def deleteRaw(query: String, anyrefArgs: AnyRef*): ResultSet =
    session.execute(prepareRawDelete(query, anyrefArgs))
  def deleteRawAsync(query: String, anyrefArgs: AnyRef*): Future[ResultSet] =
    session.executeAsync(prepareRawDelete(query, anyrefArgs))

  private[this] def prepareSelect[T: CCCassFormatEncoder](table: String, selectable: T, includeColumns: Int, limit: Long) = {
    val (strArgs, anyrefArgs) = clean(selectable, table, includeColumns)
    val prepared = queryCache.get(strArgs.toSet + table + "SELECT", {
      val limitStr = if (limit > 0) s" LIMIT $limit" else ""
      val filteringStr = if (keyspaceMeta.getTable(table).getPrimaryKey.size() < anyrefArgs.length) s" ALLOW FILTERING" else ""
      session.prepare(s"SELECT * FROM $keyspace.$table WHERE ${strArgs.map(s => s"$s = ?").mkString(" AND ")}" + limitStr + filteringStr)
    })
    prepared.bind(anyrefArgs: _*)
  }
  // includeColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
  def select[T: CCCassFormatEncoder](table: String, selectable: T, includeColumns: Int = 0, limit: Long = 0): Iterator[Row] =
    session.execute(prepareSelect(table, selectable, includeColumns, limit)).iterator.asScala
  def selectAsync[T: CCCassFormatEncoder](table: String, selectable: T, includeColumns: Int = 0, limit: Long = 0): Future[Iterator[Row]] =
    session.executeAsync(prepareSelect(table, selectable, includeColumns, limit)).map(_.iterator.asScala)
  def selectOne[T: CCCassFormatEncoder](table: String, selectable: T, includeColumns: Int = 0, limit: Long = 0): Option[Row] =
    Option(session.execute(prepareSelect(table, selectable, includeColumns, limit)).one())
  def selectOneAsync[T: CCCassFormatEncoder](table: String, selectable: T, includeColumns: Int = 0, limit: Long = 0): Future[Option[Row]] =
    session.executeAsync(prepareSelect(table, selectable, includeColumns, limit)).map(rs => Option(rs.one()))

  private[this] def prepareRawSelect(query: String, anyrefArgs: Seq[AnyRef]) = {
    val prepared = queryCache.get(Set(query), session.prepare(query))
    prepared.bind(anyrefArgs: _*)
  }
  def selectRaw[T: CCCassFormatDecoder](query: String, anyrefArgs: AnyRef*): Iterator[Row] =
    session.execute(prepareRawSelect(query, anyrefArgs)).iterator.asScala
  def selectRawAsync[T: CCCassFormatDecoder](query: String, anyrefArgs: AnyRef*): Future[Iterator[Row]] =
    session.executeAsync(prepareRawSelect(query, anyrefArgs)).map(_.iterator.asScala)
  def selectOneRaw[T: CCCassFormatDecoder](query: String, anyrefArgs: AnyRef*): Option[Row] =
    Option(session.execute(prepareRawSelect(query, anyrefArgs)).one())
  def selectOneAsync[T: CCCassFormatDecoder](query: String, anyrefArgs: AnyRef*): Future[Option[Row]] =
    session.executeAsync(prepareRawSelect(query, anyrefArgs)).map(rs => Option(rs.one()))
}