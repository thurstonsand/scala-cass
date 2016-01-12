package com.weather.scalacass

import com.datastax.driver.core.Session
import com.datastax.driver.core.exceptions.InvalidTypeException
import org.joda.time.DateTime
import org.scalatest.{FlatSpec, OptionValues, Matchers}
import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._

import com.weather.scalacass.util.{CassandraTester, EmbedCassandra}
import ScalaCass._


class ScalaCassUnitTests extends CassandraTester("testDB", "testTable", List("str varchar", "str2 ascii", "b blob",
  "d decimal", "f float", "net inet", "tid timeuuid", "vi varint", "i int", "bi bigint", "bool boolean", "dub double",
  "l list<varchar>", "m map<varchar, bigint>", "s set<double>", "ts timestamp", "id uuid", "sblob set<blob>"), "((str))") with OptionValues {
  def testType[GoodType: TypeTag : RowDecoder, BadType: TypeTag : RowDecoder](k: String, v: GoodType, default: GoodType, convert: (GoodType) => AnyRef) = {
    val args = {
      val converted = convert(v)
      if(k == "str") Seq((k, converted)) else Seq((k, converted), ("str", "asdf"))
    }
    insert(args)
    val res = getOne
    typeOf[GoodType] match {
      case t if t <:< typeOf[Iterable[Array[Byte]]] =>
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
    an [IllegalArgumentException] should be thrownBy res.as[GoodType](s"not$k")
    an [InvalidTypeException] should be thrownBy res.as[BadType](k)
    an [IllegalArgumentException] should be thrownBy res.as[BadType](s"not$k")

    res.getAs[GoodType](s"not$k") shouldBe None
    res.getAs[BadType](k) shouldBe None
    res.getAs[BadType](s"not$k") shouldBe None
  }

  "strings" should "be extracted correctly" in testType[String, Int]("str", "asdf", "qwerty", t => t)
  "ints" should "be extracted correctly" in testType[Int, String]("i", 1234, 9876, t => Int.box(t))
  "bigints" should "be extracted correctly" in testType[Long, String]("bi", 1234, 9876, t => Long.box(t))
  "boolean" should "be extracted correctly" in testType[Boolean, Int]("bool", true, false, t => Boolean.box(t))
  "double" should "be extracted correctly" in testType[Double, String]("dub", 123.4, 987.6, t => Double.box(t))
  "list" should "be extracted correctly (wrong basic)" in testType[List[String], String]("l", List("as", "df"), List("fd", "sa"), t => t.asJava)
  "list" should "be extracted correctly (wrong type param)" in testType[List[String], List[Int]]("l", List("as", "df"), List("fd", "sa"), t => t.asJava)
  "map" should "be extracted correctly (wrong basic)" in testType[Map[String, Long], String]("m", Map("asdf" -> 10L), Map("fdsa" -> -10L), t => t.mapValues(Long.box).asJava)
  "map" should "be extracted correctly (wrong 1st type param)" in testType[Map[String, Long], Map[Long, Long]]("m", Map("asdf" -> 10L), Map("fdsa" -> -10L), t => t.mapValues(Long.box).asJava)
  "map" should "be extracted correctly (wrong 2nd type param)" in testType[Map[String, Long], Map[String, Int]]("m", Map("asdf" -> 10L), Map("fdsa" -> -10L), t => t.mapValues(Long.box).asJava)
  "set" should "be extracted correctly (wrong basic)" in testType[Set[Double], String]("s", Set(123.4), Set(987.6), t => t.map(Double.box).asJava)
  "set" should "be extracted correctly (wrong type param)" in testType[Set[Double], Set[String]]("s", Set(123.4), Set(987.6), t => t.map(Double.box).asJava)
  "timestamp" should "be extracted correctly" in testType[DateTime, String]("ts", DateTime.now, DateTime.now.minusDays(20), t => t.toDate)
  "uuid" should "be extracted correctly" in testType[java.util.UUID, String]("id", java.util.UUID.randomUUID, java.util.UUID.randomUUID, t => t)
  "ascii" should "be extracted correctly" in testType[String, Int]("str2", "asdf", "fdsa", t => t)
  "blob" should "be extracted correctly (wrong basic)" in testType[Array[Byte], String]("b", "asdf".getBytes, "fdsa".getBytes, t => java.nio.ByteBuffer.wrap(t))
//  "blob" should "be extracted correctly (wrong type param)" in testType[Array[Byte], Array[Char]]("b", "asdf".getBytes, "fdsa".getBytes, t => java.nio.ByteBuffer.wrap(t)) // implicitly disallowed
  "inet" should "be extracted correctly" in testType[java.net.InetAddress, String]("net", java.net.InetAddress.getByName("localhost"), java.net.InetAddress.getByName("google.com"), t => t)
  "decimal" should "be extracted correctly" in testType[BigDecimal, Double]("d", BigDecimal(3.0), BigDecimal(2.0), t => t.underlying)
  "varint" should "be extracted correctly" in testType[BigDecimal, Double]("d", BigDecimal(3.0), BigDecimal(2.0), t => t.underlying)
  "float" should "be extracted correctly" in testType[Float, Double]("f", 123.4f, 987.6f, t => Float.box(t))
  "set<blob>" should "be extracted correctly" in testType[Set[Array[Byte]], Set[Double]]("sblob", Set("asdf".getBytes), Set("fdsa".getBytes), t => t.map(java.nio.ByteBuffer.wrap).asJava)
  "counter" should "be extracted correctly" in {
    val pKey = "str"
    val k = "count"
    val counterTable = "counterTable"
    session.execute(s"CREATE TABLE $dbName.$counterTable ($pKey varchar, $k counter, PRIMARY KEY (($pKey)))")
    session.execute(s"UPDATE $dbName.$counterTable SET $k = $k + ? WHERE $pKey='asdf'", Long.box(1L))

    val res = session.execute(s"SELECT * FROM $dbName.$counterTable").one()
    res.as[Long](k) shouldBe 1
    an [IllegalArgumentException] should be thrownBy res.as[Long](s"not$k")
    an [InvalidTypeException] should be thrownBy res.as[String](k)
    an [IllegalArgumentException] should be thrownBy res.as[String](s"not$k")

    res.getAs[Long](k).value shouldBe 1
    res.getAs[Long](s"not$k") shouldBe None
    res.getAs[String](k) shouldBe None
    res.getAs[String](s"not$k") shouldBe None
  }
}