package com.weather.scalacass.joda

import com.datastax.driver.core.{ Cluster, DataType }
import com.google.common.reflect.TypeToken
import com.weather.scalacass.{ CassFormatDecoder, CassFormatEncoder }
import com.weather.scalacass.CassFormatEncoder.sameTypeCassFormatEncoder
import com.weather.scalacass.CassFormatDecoderVersionSpecific.codecCassFormatDecoder
import org.joda.time.{ DateTime, Instant, LocalDate, LocalTime }

object Implicits {
  implicit val timeEncoder: CassFormatEncoder[LocalTime] = sameTypeCassFormatEncoder(DataType.time)
  implicit val timeDecoder: CassFormatDecoder[LocalTime] = codecCassFormatDecoder(TypeToken.of(classOf[LocalTime]))

  implicit val dateEncoder: CassFormatEncoder[LocalDate] = sameTypeCassFormatEncoder(DataType.date)
  implicit val dateDecoder: CassFormatDecoder[LocalDate] = codecCassFormatDecoder(TypeToken.of(classOf[LocalDate]))

  implicit val instantEncoder: CassFormatEncoder[Instant] = sameTypeCassFormatEncoder(DataType.timestamp)
  implicit val instantDecoder: CassFormatDecoder[Instant] = codecCassFormatDecoder(TypeToken.of(classOf[Instant]))

  implicit def timestampEncoder(implicit cluster: Cluster): CassFormatEncoder[DateTime] =
    sameTypeCassFormatEncoder(cluster.getMetadata.newTupleType(DataType.timestamp, DataType.varchar))
  implicit val timestampDecoder: CassFormatDecoder[DateTime] = codecCassFormatDecoder(TypeToken.of(classOf[DateTime]))
}
