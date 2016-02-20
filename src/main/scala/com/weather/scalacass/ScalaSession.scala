package com.weather.scalacass

import com.datastax.driver.core.{Session, ResultSet, ResultSetFuture, PreparedStatement, BoundStatement}
import com.google.common.util.concurrent.{FutureCallback, Futures}
import scala.language.implicitConversions
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import ScalaCass._

import scala.concurrent.{Promise, Future}

class ScalaSession(val keyspace: String)(implicit val session: Session) {
  implicit def resultSetFutureToScala(f: ResultSetFuture): Future[ResultSet] = {
    val p = Promise[ResultSet]()
    Futures.addCallback(f,
      new FutureCallback[ResultSet] {
        def onSuccess(r: ResultSet) = p success r
        def onFailure(t: Throwable) = p failure t
      })
    p.future
  }

  private val queryCache = new LRUCache[Set[String], PreparedStatement](100)
  //  private val tableCache = new LRUCache[String, String](10)

  //  private val tablesMetadata = session.getCluster.getMetadata.getKeyspace(keyspace).getTables.asScala
  //  private def getTableInfo(table: String) = {
  //    tablesMetadata.collectFirst {
  //      case t if t.getName == table =>
  //        t.getPrimaryKey.asScala.exists(_.toString)
  //    }
  //  }

  private def clean[T: CCCassFormat](toClean: T): (List[String], List[AnyRef]) = clean(implicitly[CCCassFormat[T]].encode(toClean))
  private def clean(toClean: List[(String, AnyRef)]): (List[String], List[AnyRef]) = toClean.filter(_._2 match {
    case None => false
    case _    => true
  }).map {
    case (str, Some(anyref: AnyRef)) => (str, anyref)
    case other               => other
  }.unzip

  def createTable[T: CCCassFormat](name: String, numPartitionKeys: Int, numClusteringKeys: Int): ResultSet = {
    val allColumns = implicitly[CCCassFormat[T]].namesAndTypes
    val (partitionKeys, rest) = allColumns.splitAt(numPartitionKeys)
    val clusteringKeys = rest.take(numClusteringKeys)
    val pk = s"${partitionKeys.map(_._1).mkString("(", ", ", ")")}"
    val fullKey = if (numClusteringKeys > 0) s"($pk, ${clusteringKeys.map(_._1).mkString(", ")})" else s"($pk)"
    session.execute(s"CREATE TABLE $keyspace.$name (${allColumns.map(nt => s"${nt._1} ${nt._2}").mkString(", ")}, PRIMARY KEY $fullKey)")
  }

  private def prepareInsert[T: CCCassFormat](table: String, insertable: T): BoundStatement = {
    val (strArgs, anyrefArgs) = clean(insertable)
    val prepared = queryCache.get(strArgs.toSet + table + "INSERT",
      session.prepare(s"INSERT INTO $keyspace.$table ${strArgs.mkString("(", ",", ")")} VALUES ${List.fill(anyrefArgs.length)("?").mkString("(", ",", ")")}"))
    prepared.bind(anyrefArgs: _*)
  }

  def insert[T: CCCassFormat](table: String, insertable: T): ResultSet = session.execute(prepareInsert(table, insertable))
  def insertAsync[T: CCCassFormat](table: String, insertable: T): Future[ResultSet] = session.executeAsync(prepareInsert(table, insertable))

  def numParams(table: String, numColumns: Option[Int]) = {
    val numPrimaryKeys = session.getCluster.getMetadata.getKeyspace(keyspace).getTable(table).getPrimaryKey.size
    numColumns.map(_ min numPrimaryKeys) getOrElse numPrimaryKeys
  }
  private def prepareDelete[T: CCCassFormat](table: String, deletable: T, numColumns: Option[Int]): BoundStatement = {
    val fullDeleted = implicitly[CCCassFormat[T]].encode(deletable)
    val (strArgs, anyrefArgs) = clean(fullDeleted.take(numParams(table, numColumns)))
    val prepared = queryCache.get(strArgs.toSet + table + "DELETE",
      session.prepare(s"DELETE FROM $keyspace.$table WHERE ${strArgs.map(s => s"$s = ?").mkString(" AND ")}"))
    prepared.bind(anyrefArgs: _*)
  }
  // numColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
  def delete[T: CCCassFormat](table: String, deletable: T, includeColumns: Int = 0): ResultSet =
    session.execute(prepareDelete(table, deletable, if (includeColumns == 0) None else Some(includeColumns)))
  def deleteAsync[T: CCCassFormat](table: String, deletable: T, numColumns: Int = 0): Future[ResultSet] =
    session.executeAsync(prepareDelete(table, deletable, if (numColumns == 0) None else Some(numColumns)))

  def prepareSelect[T: CCCassFormat](table: String, selectable: T, numColumns: Option[Int], allowFiltering: Boolean, limit: Long) = {
    val fullSelected = implicitly[CCCassFormat[T]].encode(selectable)
    val (strArgs, anyrefArgs) = clean(fullSelected.take(numParams(table, numColumns)))
    val prepared = queryCache.get(strArgs.toSet + table + "SELECT", {
      val limitStr = if (limit > 0) s" LIMIT $limit" else ""
      val filteringStr = if (allowFiltering) " ALLOW FILTERING" else ""
      session.prepare(s"SELECT * FROM $keyspace.$table WHERE ${strArgs.map(s => s"$s = ?").mkString(" AND ")}" + limitStr + filteringStr)
    })
    prepared.bind(anyrefArgs: _*)
  }
  def select[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Iterator[T] =
    session.execute(prepareSelect(table, selectable, if (includeColumns == 0) None else Some(includeColumns), allowFiltering, limit)).iterator.asScala.map(_.getAs[T]).collect{ case Some(r) => r }
  def selectAsync[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Future[Iterator[T]] =
    session.executeAsync(prepareSelect(table, selectable, if (includeColumns == 0) None else Some(includeColumns), allowFiltering, limit)).map(_.iterator.asScala.map(_.getAs[T]).collect{ case Some(r) => r })
  def selectOne[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Option[T] =
    Option(session.execute(prepareSelect(table, selectable, if (includeColumns == 0) None else Some(includeColumns), allowFiltering, limit)).one()).flatMap(_.getAs[T])
  def selectOneAsync[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Future[Option[T]] =
    session.executeAsync(prepareSelect(table, selectable, if (includeColumns == 0) None else Some(includeColumns), allowFiltering, limit)).map(rs => Option(rs.one()).flatMap(_.getAs[T]))
}