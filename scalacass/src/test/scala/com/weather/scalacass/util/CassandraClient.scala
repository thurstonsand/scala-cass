package com.weather.scalacass.util

import com.datastax.driver.core.Cluster

case class CassandraClient(hosts: List[String], port: Option[Int]) extends CassandraClientVersionSpecific {
  val cluster = {
    val c = Cluster.builder().addContactPoints(hosts: _*)
    port.foreach(c.withPort)
    clusterStartup(c)
    c.build()
  }
  val session = cluster.connect()

  def close() = cluster.close()
}
