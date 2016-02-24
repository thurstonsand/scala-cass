package com.weather.scalacass.util

import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.scalatest.{Matchers, BeforeAndAfterAll, BeforeAndAfter, FlatSpec}
import scala.collection.JavaConverters._

trait EmbedCassandra extends FlatSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll {
  var client: CassandraClient = null
  override def beforeAll() {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE)
    client = CassandraClient(List("localhost"), Some(EmbeddedCassandraServerHelper.getNativeTransportPort))
  }

  after { EmbeddedCassandraServerHelper.cleanEmbeddedCassandra() }

  override def afterAll() { client.close() }
}