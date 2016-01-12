package com.weather.fastcass

import com.datastax.driver.core.Session
import com.datastax.driver.core.exceptions.InvalidTypeException
import org.joda.time.DateTime
import org.scalatest.{FlatSpec, OptionValues, Matchers}
import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._
import com.weather.fastcass.CassandraHelper.CaseClassRealizer._

import util.EmbedCassandra
import CassandraHelper._

class CaseClassUnitTests extends FlatSpec with Matchers with EmbedCassandra with OptionValues {
  var session: Session = null
  private val db = "TestDB"
  case class Person(name: String, age: Int)
  case class PersonWithOption(name: Option[String], age: Option[Int], job: Option[String])
  private val personTable = "personTable"

  override def beforeAll(): Unit = {
    super.beforeAll()
    session = client.session
  }

  before {
    session.execute(s"CREATE KEYSPACE $db WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};")
    session.execute(s"""CREATE TABLE $db.$personTable (name varchar, age int, PRIMARY KEY ((name)))""")
  }

  private def insert(pairs: Seq[(String, AnyRef)]) = {
    val (strs, objs) = pairs.foldLeft(Seq.empty[String], Seq.empty[AnyRef]) { case ((accStr, acc), (nStr, n)) =>
      (nStr +: accStr, n +: acc)
    }
    session.execute(s"INSERT INTO $db.$personTable ${strs.mkString("(", ",", ")")} VALUES ${objs.map(_ => "?").mkString("(", ",", ")")}", objs: _*)
  }
  private def getOne = session.execute(s"SELECT * FROM $db.$personTable").one()

  "case class with no Options" should "materialize" in {
    insert(Seq(("name", "asdf"), ("age", Int.box(22))))
    getOne.realize[Person](realizeCaseClass[Person]) shouldBe Person("asdf", 22)
  }

  "case class with Options" should "materialize even with empty" in {
    insert(Seq(("name", "asdf"), ("age", Int.box(22))))
    import com.weather.fastcass.CassandraHelper.CaseClassRealizer._
    getOne.realize[PersonWithOption](realizeCaseClass[PersonWithOption]) shouldBe PersonWithOption(Some("asdf"), Some(22), None)
  }

}
