package com.weather.scalacass.util

import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.collection.JavaConverters._

trait DockerCassandraService2 extends DockerKit {

  val DefaultCqlPort = 9042

  val cassandraContainer = DockerContainer("whisk/cassandra:2.1.8")
    .withPorts(DefaultCqlPort -> Some(DefaultCqlPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("Starting listening for CQL clients on"))

  abstract override def dockerContainers = cassandraContainer :: super.dockerContainers
}

trait DockerCassandra extends FlatSpec with Matchers with BeforeAndAfter with DockerCassandraService2 with DockerTestKit {
  implicit val pc = PatienceConfig(Span(60, Seconds), Span(1, Second))
  var client: CassandraClient = _
  override def beforeAll(): Unit = {
    super.beforeAll()
    client = CassandraClient(List("dockerhost"), Some(DefaultCqlPort))
  }

  after {
    val keyspaces = client.cluster.getMetadata.getKeyspaces.asScala.map(_.getName).filterNot(ks => ks == "system_traces" || ks == "system")
    keyspaces.foreach { k => println(s"dropping keyspace $k with tables ${client.cluster.getMetadata.getKeyspace(k).getTables.asScala.map(_.getName).mkString(", ")}"); client.session.execute(s"drop keyspace $k") }
  }

  override def afterAll(): Unit = {
    client.close()
    super.afterAll()
  }
}
