package com.weather.scalacass.util

import org.cassandraunit.utils.EmbeddedCassandraServerHelper

abstract class CassandraUnitTester extends CassandraTester {
  override def beforeAll() = {
    super.beforeAll()
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(CassandraUnitInfo.cassYaml)
    _client = Some(CassandraClient(List("localhost"), Some(EmbeddedCassandraServerHelper.getNativeTransportPort)))
  }

  def beforeEach(): Unit = {}
  def afterEach(): Unit = {
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }

  before {
    beforeEach()
  }

  after {
    afterEach()
  }
}
