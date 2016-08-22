package com.weather.scalacass

import com.datastax.driver.core.{Cluster, DataType}
import com.datastax.driver.extras.codecs.jdk8.{InstantCodec, LocalDateCodec, LocalTimeCodec, ZonedDateTimeCodec}

package object jdk8 {
  def register(c: Cluster): Unit = {
    val tt = c.getMetadata.newTupleType(DataType.timestamp, DataType.varchar)
    c.getConfiguration.getCodecRegistry.register(new ZonedDateTimeCodec(tt), LocalDateCodec.instance, LocalTimeCodec.instance, InstantCodec.instance)
    (): Unit
  }
}
