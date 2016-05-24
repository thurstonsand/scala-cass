package com.weather.scalacass

import com.datastax.driver.core.exceptions.InvalidTypeException
import org.joda.time.DateTime
import org.scalatest.OptionValues
import com.weather.scalacass.util.CassandraTester
import ScalaCass._

class ScalaCassUnitTests extends CassandraTester("testDB", "testTable", List("str varchar", "str2 ascii", "b blob",
  "d decimal", "f float", "net inet", "tid timeuuid", "vi varint", "i int", "bi bigint", "bool boolean", "dub double",
  "l list<varchar>", "m map<varchar, bigint>", "s set<double>", "ts timestamp", "id uuid", "sblob set<blob>"), List("str")) with OptionValues {
  def testType[GoodType: CassFormatDecoder, BadType: CassFormatDecoder](k: String, v: GoodType, default: GoodType)(implicit goodCF: CassFormatEncoder[GoodType]) = {
    val args = {
      val converted = goodCF.encode(v).getOrThrow.asInstanceOf[AnyRef]
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
      case _ =>
        res.as[GoodType](k) shouldBe v
        res.getAs[GoodType](k).value shouldBe v
        res.getOrElse(k, default) shouldBe v
        res.getOrElse(s"not$k", default) shouldBe default
    }

    an[IllegalArgumentException] should be thrownBy res.as[GoodType](s"not$k")
    an[InvalidTypeException] should be thrownBy res.as[BadType](k)
    an[IllegalArgumentException] should be thrownBy res.as[BadType](s"not$k")

    res.getAs[GoodType](s"not$k") shouldBe None
    res.getAs[BadType](k) shouldBe None
    res.getAs[BadType](s"not$k") shouldBe None

    case class TestCC(pkField: String, refField: GoodType)
    implicit val s = client.session
    val ss = new ScalaSession(dbName)
    val tname = s"test${TestCC.hashCode.toString.take(5)}"
    ss.createTable[TestCC](tname, 1, 0)(implicitly[CCCassFormatEncoder[TestCC]])
    val t1 = TestCC("t1", v)
    ss.insert(tname, t1)(implicitly[CCCassFormatEncoder[TestCC]])
    k match {
      case "b" =>
        ss.selectOne(tname, t1).flatMap(_.getAs[TestCC]).map(_.refField.asInstanceOf[Array[Byte]]).value should contain theSameElementsInOrderAs t1.refField.asInstanceOf[Array[Byte]]
      case "sblob" =>
        ss.selectOne(tname, t1).flatMap(_.getAs[TestCC]).flatMap(_.refField.asInstanceOf[Set[Array[Byte]]].headOption).value should contain theSameElementsInOrderAs t1.refField.asInstanceOf[Set[Array[Byte]]].head
      case _ =>
        ss.selectOne(tname, t1).flatMap(_.getAs[TestCC]).value shouldBe t1
    }
    ss.delete(tname, t1)
    ss.select(tname, t1).toList.map(_.as[TestCC]) shouldBe empty
  }

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
  "timestamp" should "be extracted correctly" in testType[DateTime, String]("ts", DateTime.now, DateTime.now.minusDays(20))
  "uuid" should "be extracted correctly" in testType[java.util.UUID, String]("id", java.util.UUID.randomUUID, java.util.UUID.randomUUID)
  "ascii" should "be extracted correctly" in testType[String, Int]("str2", "asdf", "fdsa")
  "blob" should "be extracted correctly (wrong basic)" in testType[Array[Byte], String]("b", "asdf".getBytes, "fdsa".getBytes)
  //  "blob" should "be extracted correctly (wrong type param)" in testType[Array[Byte], Array[Char]]("b", "asdf".getBytes, "fdsa".getBytes) // implicitly disallowed
  "inet" should "be extracted correctly" in testType[java.net.InetAddress, String]("net", java.net.InetAddress.getByName("localhost"), java.net.InetAddress.getByName("192.168.1.2"))
  "decimal" should "be extracted correctly" in testType[BigDecimal, Double]("d", BigDecimal(3.0), BigDecimal(2.0))
  "varint" should "be extracted correctly" in testType[BigInt, Long]("vi", 3, 2)
  "float" should "be extracted correctly" in testType[Float, Double]("f", 123.4f, 987.6f)
  "set<blob>" should "be extracted correctly" in testType[Set[Array[Byte]], Set[Double]]("sblob", Set("asdf".getBytes), Set("fdsa".getBytes))
  "counter" should "be extracted correctly" in {
    val pKey = "str"
    val k = "count"
    val counterTable = "counterTable"
    client.session.execute(s"CREATE TABLE $dbName.$counterTable ($pKey varchar, $k counter, PRIMARY KEY (($pKey)))")
    client.session.execute(s"UPDATE $dbName.$counterTable SET $k = $k + ? WHERE $pKey='asdf'", Long.box(1L))

    val res = client.session.execute(s"SELECT * FROM $dbName.$counterTable").one()
    res.as[Long](k) shouldBe 1
    an[IllegalArgumentException] should be thrownBy res.as[Long](s"not$k")
    an[InvalidTypeException] should be thrownBy res.as[String](k)
    an[IllegalArgumentException] should be thrownBy res.as[String](s"not$k")

    res.getAs[Long](k).value shouldBe 1
    res.getAs[Long](s"not$k") shouldBe None
    res.getAs[String](k) shouldBe None
    res.getAs[String](s"not$k") shouldBe None

    case class CounterCC(str: String, count: Long)
    val tname = "derivedtable"
    implicit val s = client.session
    val ss = ScalaSession(dbName)
    ss.createTable[CounterCC](tname, 1, 0)
    val t1 = CounterCC("t1", 1)
    ss.insert(tname, t1)
    ss.selectOne(tname, t1).value.as[CounterCC] shouldBe t1
    ss.delete(tname, t1)
    ss.select(tname, t1).toList shouldBe empty
  }
}