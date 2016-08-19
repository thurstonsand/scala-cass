package com.weather.scalacass

import com.datastax.driver.core.{Cluster, DataType}
import com.datastax.driver.extras.codecs.joda.{DateTimeCodec, InstantCodec, LocalDateCodec, LocalTimeCodec}

package object joda {
  def register(c: Cluster): Unit = {
    val tt = c.getMetadata.newTupleType(DataType.timestamp, DataType.varchar)
    c.getConfiguration.getCodecRegistry.register(new DateTimeCodec(tt), LocalDateCodec.instance, LocalTimeCodec.instance, InstantCodec.instance)
  }
}
