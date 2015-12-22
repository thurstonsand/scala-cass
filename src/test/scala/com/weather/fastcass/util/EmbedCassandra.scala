package com.weather.fastcass.util

import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, FlatSpec}
import scalaz._, Scalaz._

trait EmbedCassandra extends FlatSpec with BeforeAndAfter with BeforeAndAfterAll {
  var client: CassandraClient = null
  override def beforeAll() {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE)
    client = CassandraClient(List("localhost"), EmbeddedCassandraServerHelper.getNativeTransportPort.some)
  }

  after { EmbeddedCassandraServerHelper.cleanEmbeddedCassandra() }

  override def afterAll() { client.close() }
}