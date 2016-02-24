package com.weather.scalacass.util

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session

case class CassandraClient(hosts: List[String], port: Option[Int]) {
  val cluster = {
    val c = Cluster.builder().addContactPoints(hosts: _*)
    port.foreach(c.withPort)
    c.build()
  }
  val session = cluster.connect()

  def close() = cluster.close()
}
