package com.weather.scalacass.util

import com.datastax.driver.core.Cluster

trait CassandraClientVersionSpecific {
  def clusterStartup(cb: Cluster.Builder): Cluster.Builder = cb
}
