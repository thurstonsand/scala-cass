package com.weather.scalacass

import com.weather.scalacass.ScalaSession.UpdateBehavior
import com.weather.scalacass.util.CassandraWithTableTester
import org.scalatest.OptionValues
import com.weather.scalacass.syntax._

object UpdateBehaviorTests {
  val db = "testDB"
  val table = "testTable"
}
class UpdateBehaviorTests extends CassandraWithTableTester(UpdateBehaviorTests.db, UpdateBehaviorTests.table, List("str varchar", "l list<varchar>",
  "s set<double>"), List("str")) with OptionValues {
  import UpdateBehaviorTests.table
  lazy val ss = ScalaSession("testDB")(client.session)

  case class Query(str: String)
  case class Insert(str: String, l: List[String], s: Set[Double])
  val baseStr = "some item"
  val base = Insert(baseStr, List("asdf"), Set(1.0))
  val baseQuery = Query(baseStr)
  def insertOne(i: Insert = base) = ss.insert(table, i)

  "explicit replacement" should "act as before" in {
    case class Replacing(l: UpdateBehavior.Replace[List, String], s: UpdateBehavior.Replace[Set, Double])
    val instance = Replacing(List("fdsa"), Set(2.0))

    insertOne()
    ss.update(table, instance, baseQuery)

    val res = ss.selectOne(table, baseQuery).value.as[Insert]
    res.str shouldBe baseStr
    res.l should contain theSameElementsAs instance.l.coll
    res.s should contain theSameElementsAs instance.s.coll
  }

  "implicit replacement" should "also act as before" in {
    case class ReplacingImplicit(l: List[String], s: Set[Double])
    val instance = ReplacingImplicit(List("fafa"), Set(3.0))

    insertOne()
    ss.update(table, instance, baseQuery)

    val res = ss.selectOne(table, baseQuery).value.as[Insert]
    res.str shouldBe baseStr
    res.l should contain theSameElementsAs instance.l
    res.s should contain theSameElementsAs instance.s
  }

  "add" should "combine the two entries" in {
    case class Adding(l: UpdateBehavior.Add[List, String], s: UpdateBehavior.Add[Set, Double])
    val instance = Adding(List("afaf"), Set(4.0))

    insertOne()
    ss.update(table, instance, baseQuery)

    val res = ss.selectOne(table, baseQuery).value.as[Insert]
    res.str shouldBe baseStr
    res.l should contain theSameElementsAs base.l ::: instance.l.coll
    res.s should contain theSameElementsAs base.s ++ instance.s.coll
  }

  "subtract" should "subtract from the original entry" in {
    case class Subtracting(l: UpdateBehavior.Subtract[List, String], s: UpdateBehavior.Subtract[Set, Double])
    val instance = Subtracting(List("another str"), Set(5.0))

    val expandedBase = base.copy(l = instance.l.coll ::: base.l, s = instance.s.coll ++ base.s)
    insertOne(expandedBase)

    val preres = ss.selectOne(table, baseQuery).value.as[Insert]
    preres.str shouldBe baseStr
    preres.l should contain theSameElementsAs expandedBase.l
    preres.s should contain theSameElementsAs expandedBase.s

    ss.update(table, instance, baseQuery)

    val res = ss.selectOne(table, baseQuery).value.as[Insert]
    res.str shouldBe baseStr
    res.l should contain theSameElementsAs base.l
    res.s should contain theSameElementsAs base.s
  }
}