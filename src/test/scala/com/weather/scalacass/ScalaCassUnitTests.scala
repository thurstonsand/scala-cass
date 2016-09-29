package com.weather.scalacass

import com.weather.scalacass.ScalaCassUnitTestsVersionSpecific.BadTypeException
import org.scalatest.OptionValues
import com.weather.scalacass.util.CassandraWithTableTester
import com.datastax.driver.core.exceptions.InvalidTypeException
import syntax._

abstract class ScalaCassUnitTests extends CassandraWithTableTester("testDB", "testTable", ScalaCassUnitTestsVersionSpecific.extraHeaders ::: List("str varchar", "str2 ascii", "b blob",
  "d decimal", "f float", "net inet", "tid timeuuid", "vi varint", "i int", "bi bigint", "bool boolean", "dub double",
  "l list<varchar>", "m map<varchar, bigint>", "s set<double>", "id uuid", "sblob set<blob>, tup tuple<int, varchar>"), List("str")) with OptionValues {
  def testType[GoodType: CassFormatDecoder, BadType: CassFormatDecoder](k: String, v: GoodType, default: GoodType)(implicit goodCF: CassFormatEncoder[GoodType]) = {
    val args = {
      val converted = goodCF.encode(v).fold(throw _, identity).asInstanceOf[AnyRef]
      if (k == "str") Seq((k, converted)) else Seq((k, converted), ("str", "asdf"))
    }
    insert(args)
    val res = getOne
    k match {
      case "sblob" =>
        val known = v.asInstanceOf[Iterable[Array[Byte]]].head
        res.as[GoodType](k).asInstanceOf[Iterable[Array[Byte]]].head should contain theSameElementsInOrderAs known
        res.getAs[GoodType](k).map(_.asInstanceOf[Iterable[Array[Byte]]].head).value should contain theSameElementsInOrderAs known

        res.getOrElse(k, default).asInstanceOf[Iterable[Array[Byte]]].head should contain theSameElementsInOrderAs known
        res.getOrElse(s"not$k", default).asInstanceOf[Iterable[Array[Byte]]].head shouldBe default.asInstanceOf[Iterable[Array[Byte]]].head

        res.attemptAs[GoodType](k).right.toOption.map(_.asInstanceOf[Iterable[Array[Byte]]].head).value should contain theSameElementsInOrderAs known
      case _ =>
        res.as[GoodType](k) shouldBe v
        res.getAs[GoodType](k).value shouldBe v
        res.getOrElse(k, default) shouldBe v
        res.getOrElse(s"not$k", default) shouldBe default
        res.attemptAs[GoodType](k).right.toOption.value shouldBe v
    }

    an[IllegalArgumentException] should be thrownBy res.as[GoodType](s"not$k")
    a[BadTypeException] should be thrownBy res.as[BadType](k)
    an[IllegalArgumentException] should be thrownBy res.as[BadType](s"not$k")

    res.getAs[GoodType](s"not$k") shouldBe None
    res.getAs[BadType](k) shouldBe None
    res.getAs[BadType](s"not$k") shouldBe None

    res.attemptAs[GoodType](s"not$k").left.toOption.value shouldBe an[IllegalArgumentException]
    res.attemptAs[BadType](k).left.toOption.value shouldBe a[BadTypeException]
    res.attemptAs[BadType](s"not$k").left.toOption.value shouldBe an[IllegalArgumentException]

    case class TestCC(pkField: String, refField: GoodType)
    case class QueryCC(pkField: String)
    val ss = new ScalaSession(dbName)
    val tname = s"testdb${scala.util.Random.alphanumeric.take(12).mkString}"
    ss.createTable[TestCC](tname, 1, 0)(CCCassFormatEncoder[TestCC])
    val t1 = TestCC("t1", v)
    val q1 = QueryCC(t1.pkField)
    ss.insert(tname, t1)(CCCassFormatEncoder[TestCC])
    k match {
      case "b" =>
        ss.selectOne(tname, ScalaSession.NoQuery()).flatMap(_.getAs[TestCC]).map(_.refField.asInstanceOf[Array[Byte]]).value should contain theSameElementsInOrderAs t1.refField.asInstanceOf[Array[Byte]]
      case "sblob" =>
        ss.selectOne(tname, ScalaSession.NoQuery()).flatMap(_.getAs[TestCC]).flatMap(_.refField.asInstanceOf[Set[Array[Byte]]].headOption).value should contain theSameElementsInOrderAs t1.refField.asInstanceOf[Set[Array[Byte]]].head
      case _ =>
        ss.selectOne(tname, q1).flatMap(_.getAs[TestCC]).value shouldBe t1
    }
    ss.delete(tname, q1)
    ss.select(tname, q1).toList.map(_.as[TestCC]) shouldBe empty
    ss.dropTable(tname)
  }
}
class ScalaCassUnitTestsAll extends ScalaCassUnitTests with ScalaCassUnitTestsVersionSpecific {
  "strings" should "be extracted correctly" in testType[String, Int]("str", "asdf", "qwerty")
  "ints" should "be extracted correctly" in testType[Int, String]("i", 1234, 9876)
  "bigints" should "be extracted correctly" in testType[Long, String]("bi", 1234, 9876)
  "boolean" should "be extracted correctly" in testType[Boolean, Int]("bool", true, false)
  "double" should "be extracted correctly" in testType[Double, String]("dub", 123.4, 987.6)
  "list" should "be extracted correctly (wrong basic)" in testType[List[String], String]("l", List("as", "df"), List("fd", "sa"))
  "list" should "be extracted correctly (wrong type param)" in testType[List[String], List[Int]]("l", List("as", "df"), List("fd", "sa"))
  "map" should "be extracted correctly (wrong basic)" in testType[Map[String, Long], String]("m", Map("asdf" -> 10L), Map("fdsa" -> -10L))
  "map" should "be extracted correctly (wrong 1st type param)" in testType[Map[String, Long], Map[Long, Long]]("m", Map("asdf" -> 10L), Map("fdsa" -> -10L))
  "map" should "be extracted correctly (wrong 2nd type param)" in testType[Map[String, Long], Map[String, Int]]("m", Map("asdf" -> 10L), Map("fdsa" -> -10L))
  "set" should "be extracted correctly (wrong basic)" in testType[Set[Double], String]("s", Set(123.4), Set(987.6))
  "set" should "be extracted correctly (wrong type param)" in testType[Set[Double], Set[String]]("s", Set(123.4), Set(987.6))
  "uuid" should "be extracted correctly" in testType[java.util.UUID, String]("id", java.util.UUID.randomUUID, java.util.UUID.randomUUID)
  "ascii" should "be extracted correctly" in testType[String, Int]("str2", "asdf", "fdsa")
  "blob" should "be extracted correctly (wrong basic)" in testType[Array[Byte], String]("b", "asdf".getBytes, "fdsa".getBytes)
  "inet" should "be extracted correctly" in testType[java.net.InetAddress, String]("net", java.net.InetAddress.getByName("localhost"), java.net.InetAddress.getByName("192.168.1.2"))
  "decimal" should "be extracted correctly" in testType[BigDecimal, Double]("d", BigDecimal(3.0), BigDecimal(2.0))
  "varint" should "be extracted correctly" in testType[BigInt, Long]("vi", 3, 2)
  "float" should "be extracted correctly" in testType[Float, Double]("f", 123.4f, 987.6f)
  "set<blob>" should "be extracted correctly" in testType[Set[Array[Byte]], Set[Double]]("sblob", Set("asdf".getBytes), Set("fdsa".getBytes))
  "tup<int, varchar>" should "be extracted correctly (wrong basic)" in testType[(Int, String), String]("tup", (4, "fdsa"), (5, "asas"))
  "tup<int, varchar>" should "be extracted correctly (wrong 1st type)" in testType[(Int, String), (String, String)]("tup", (4, "fdsa"), (5, "qqwer"))
  "tup<int, varchar>" should "be extracted correctly (wrong arity)" in {
    val goodValue = (4, "fdsa")
    val args = Seq(("tup", implicitly[CassFormatEncoder[(Int, String)]].encode(goodValue).fold(throw _, identity).asInstanceOf[AnyRef]), ("str", "asdf"))
    insert(args)
    val res = getOne
    res.as[(Int, String)]("tup") shouldBe goodValue
    res.getAs[(Int, String)]("tup").value shouldBe goodValue
    res.getOrElse("tup", (5, "qqwe")) shouldBe goodValue
    res.getOrElse("nottup", (5, "qqwe")) shouldBe ((5, "qqwe"))

    an[IllegalArgumentException] should be thrownBy res.as[(Int, String)]("nottup")
    an[InvalidTypeException] should be thrownBy res.as[(Int, String, String)]("tup")
    an[InvalidTypeException] should be thrownBy res.as[Tuple1[Int]]("tup")
    a[BadTypeException] should be thrownBy res.as[(String, Int)]("tup")
  }
  "counter" should "be extracted correctly" in {
    val pKey = "str"
    val k = "count"
    val counterTable = "counterTable"
    client.session.execute(s"CREATE TABLE $dbName.$counterTable ($pKey varchar, $k counter, PRIMARY KEY (($pKey)))")
    client.session.execute(s"UPDATE $dbName.$counterTable SET $k = $k + ? WHERE $pKey='asdf'", Long.box(1L))

    val res = client.session.execute(s"SELECT * FROM $dbName.$counterTable").one()
    res.as[Long](k) shouldBe 1
    an[IllegalArgumentException] should be thrownBy res.as[Long](s"not$k")
    a[BadTypeException] should be thrownBy res.as[String](k)
    an[IllegalArgumentException] should be thrownBy res.as[String](s"not$k")

    res.getAs[Long](k).value shouldBe 1
    res.getAs[Long](s"not$k") shouldBe None
    res.getAs[String](k) shouldBe None
    res.getAs[String](s"not$k") shouldBe None

    case class CounterCC(str: String, count: Long)
    case class QueryCC(str: String)
    val tname = "derivedtable"
    val ss = ScalaSession(dbName)
    ss.createTable[CounterCC](tname, 1, 0)
    val t1 = CounterCC("t1", 1)
    val q1 = QueryCC(t1.str)
    ss.insert(tname, t1)
    ss.selectOne(tname, q1).value.as[CounterCC] shouldBe t1
    ss.delete(tname, q1)
    ss.select(tname, q1).toList shouldBe empty
  }
}