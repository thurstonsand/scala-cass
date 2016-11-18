package com.weather.scalacass.util

import com.datastax.driver.core.{Cluster, CodecRegistry}
import com.datastax.driver.extras.codecs

trait CassandraClientVersionSpecific {
  def clusterStartup(cb: Cluster.Builder): Cluster.Builder = {
    val registry = CodecRegistry.DEFAULT_INSTANCE
    registry.register(codecs.jdk8.InstantCodec.instance, codecs.jdk8.LocalDateCodec.instance,
      codecs.jdk8.LocalTimeCodec.instance, codecs.joda.InstantCodec.instance, codecs.joda.LocalDateCodec.instance,
      codecs.joda.LocalTimeCodec.instance)
    cb.withCodecRegistry(registry)
  }
}
