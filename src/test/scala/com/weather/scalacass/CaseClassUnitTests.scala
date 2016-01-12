package com.weather.scalacass

import com.weather.scalacass.util.CassandraTester
import ScalaCass._

class CaseClassUnitTests extends CassandraTester("TestDB", "personTable", List("name varchar", "age int", "job varchar"), "((name))") {
  case class Person(name: String, age: Int)
  case class PersonWithOption(name: String, age: Int, job: Option[String])

  "case class with no Options" should "materialize" in {
    insert(Seq(("name", "asdf"), ("age", Int.box(22))))
    getOne.realize[Person] shouldBe Person("asdf", 22)
  }

  "case class with Options and not filled" should "realize" in {
    insert(Seq(("name", "asdf"), ("age", Int.box(22))))
    getOne.realize[PersonWithOption] shouldBe PersonWithOption("asdf", 22, None)
  }

  "case class With Options and filled" should "realize" in {
    insert(Seq(("name", "asdf"), ("age", Int.box(22)), ("job", "programmer")))
    getOne.realize[PersonWithOption] shouldBe PersonWithOption("asdf", 22, Some("programmer"))
  }
}
