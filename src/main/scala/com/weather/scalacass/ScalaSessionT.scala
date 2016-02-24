package com.weather.scalacass

import com.datastax.driver.core._, exceptions.InvalidQueryException
import com.google.common.util.concurrent.{FutureCallback, Futures}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future}
import ScalaCass._


trait ScalaSessionT {
  object ScalaSession {
    import scala.language.implicitConversions
    private implicit def resultSetFutureToScalaFuture(f: ResultSetFuture): Future[ResultSet] = {
      val p = Promise[ResultSet]()
      Futures.addCallback(f,
        new FutureCallback[ResultSet] {
          def onSuccess(r: ResultSet) = p success r
          def onFailure(t: Throwable) = p failure t
        })
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

    private val queryCache = new LRUCache[Set[String], PreparedStatement](100)

    private def numParams(table: String, numColumns: Int) = {
      val numPrimaryKeys = session.getCluster.getMetadata.getKeyspace(keyspace).getTable(table).getPrimaryKey.size
      if (numColumns <= 0) numPrimaryKeys
      else numColumns min numPrimaryKeys
    }

    private def clean[T: CCCassFormat](toClean: T): (List[String], List[AnyRef]) = clean(implicitly[CCCassFormat[T]].encode(toClean))
    private def clean[T: CCCassFormat](toClean: T, table: String, numColumns: Int): (List[String], List[AnyRef]) =
      clean(implicitly[CCCassFormat[T]].encode(toClean).take(numParams(table, numColumns)))
    private def clean[T](toClean: List[(String, AnyRef)]): (List[String], List[AnyRef]) = toClean.filter(_._2 match {
      case None => false
      case _    => true
    }).map {
      case (str, Some(anyref: AnyRef)) => (str, anyref)
      case other                       => other
    }.unzip

    def dropKeyspace(): Unit = session.execute(s"DROP KEYSPACE $keyspace")

    def createTable[T: CCCassFormat](name: String, numPartitionKeys: Int, numClusteringKeys: Int, tableProperties: String = ""): ResultSet = {
      if (numPartitionKeys <= 0) throw new InvalidQueryException("Cassandra: need to include at least one partition key")
      val allColumns = implicitly[CCCassFormat[T]].namesAndTypes
      val (partitionKeys, rest) = allColumns.splitAt(numPartitionKeys)
      val clusteringKeys = rest.take(numClusteringKeys)
      val pk = s"${partitionKeys.map(_._1).mkString("(", ", ", ")")}"
      val fullKey = if (numClusteringKeys > 0) s"($pk, ${clusteringKeys.map(_._1).mkString(", ")})" else s"($pk)"
      val withClause = if (tableProperties.length > 0) s" WITH $tableProperties" else ""
      session.execute(s"CREATE TABLE $keyspace.$name (${allColumns.map(nt => s"${nt._1} ${nt._2}").mkString(", ")}, PRIMARY KEY $fullKey)" + withClause)
    }

    def dropTable(table: String): Unit = session.execute(s"DROP TABLE $keyspace.$table")

    private def prepareInsert[T: CCCassFormat](table: String, insertable: T): BoundStatement = {
      val (strArgs, anyrefArgs) = clean(insertable)
      val prepared = queryCache.get(strArgs.toSet + table + "INSERT",
        session.prepare(s"INSERT INTO $keyspace.$table ${strArgs.mkString("(", ",", ")")} VALUES ${List.fill(anyrefArgs.length)("?").mkString("(", ",", ")")}"))
      prepared.bind(anyrefArgs: _*)
    }

    // numColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
    def insert[T: CCCassFormat](table: String, insertable: T): ResultSet = session.execute(prepareInsert(table, insertable))
    def insertAsync[T: CCCassFormat](table: String, insertable: T): Future[ResultSet] = session.executeAsync(prepareInsert(table, insertable))

    private def prepareDelete[T: CCCassFormat](table: String, deletable: T, numColumns: Int): BoundStatement = {
      val (strArgs, anyrefArgs) = clean(deletable, table, numColumns)
      val prepared = queryCache.get(strArgs.toSet + table + "DELETE",
        session.prepare(s"DELETE FROM $keyspace.$table WHERE ${strArgs.map(s => s"$s = ?").mkString(" AND ")}"))
      prepared.bind(anyrefArgs: _*)
    }
    // numColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
    def delete[T: CCCassFormat](table: String, deletable: T, includeColumns: Int = 0): ResultSet =
      session.execute(prepareDelete(table, deletable, includeColumns))
    def deleteAsync[T: CCCassFormat](table: String, deletable: T, numColumns: Int = 0): Future[ResultSet] =
      session.executeAsync(prepareDelete(table, deletable, numColumns))

    def prepareSelect[T: CCCassFormat](table: String, selectable: T, numColumns: Int, allowFiltering: Boolean, limit: Long) = {
      val (strArgs, anyrefArgs) = clean(selectable, table, numColumns)
      val prepared = queryCache.get(strArgs.toSet + table + "SELECT", {
        val limitStr = if (limit > 0) s" LIMIT $limit" else ""
        val filteringStr = if (allowFiltering) " ALLOW FILTERING" else ""
        session.prepare(s"SELECT * FROM $keyspace.$table WHERE ${strArgs.map(s => s"$s = ?").mkString(" AND ")}" + limitStr + filteringStr)
      })
      prepared.bind(anyrefArgs: _*)
    }
    // numColumns: specify the number of columns, as represented from left to right in the case class, to include in the WHERE clause for the delete
    def select[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Iterator[T] =
      session.execute(prepareSelect(table, selectable, includeColumns, allowFiltering, limit)).iterator.asScala.map(_.getAs[T]).collect{ case Some(r) => r }
    def selectAsync[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Future[Iterator[T]] =
      session.executeAsync(prepareSelect(table, selectable, includeColumns, allowFiltering, limit)).map(_.iterator.asScala.map(_.getAs[T]).collect{ case Some(r) => r })
    def selectOne[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Option[T] =
      Option(session.execute(prepareSelect(table, selectable, includeColumns, allowFiltering, limit)).one()).flatMap(s => s.getAs[T])
    def selectOneAsync[T: CCCassFormat](table: String, selectable: T, includeColumns: Int = 0, allowFiltering: Boolean = false, limit: Long = 0): Future[Option[T]] =
      session.executeAsync(prepareSelect(table, selectable, includeColumns, allowFiltering, limit)).map(rs => Option(rs.one()).flatMap(_.getAs[T]))
  }
}
