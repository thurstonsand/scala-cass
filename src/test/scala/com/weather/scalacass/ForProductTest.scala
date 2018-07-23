package com.weather.scalacass

import com.weather.scalacass.scsession.ActionUnitTests
import com.weather.scalacass.syntax._

case class CustomTable(myStr: String, myLong: Long, someiValue: Int)
object CustomTable {
  implicit val encoder: CCCassFormatEncoder[CustomTable] = CCCassFormatEncoder.forProduct3("str", "l", "i")(ct => (ct.myStr, ct.myLong, ct.someiValue))
  implicit val decoder: CCCassFormatDecoder[CustomTable] = CCCassFormatDecoder.forProduct3("str", "l", "i")((myStr: String, myLong: Long, someiValue: Int) => CustomTable(myStr, myLong, someiValue))
}

case class CustomSelect(myStr: String)
object CustomSelect {
  implicit val encoder: CCCassFormatEncoder[CustomSelect] = CCCassFormatEncoder.forProduct1("str")(cs => cs.myStr)
  implicit val decoder: CCCassFormatDecoder[CustomSelect] = CCCassFormatDecoder.forProduct1("str")((myStr: String) => CustomSelect(myStr))
}

class ForProductTest extends ActionUnitTests {
  "forProduct encoder/decoder" should "work even with different names" in {
    val row = CustomTable("mystr", 1234L, 4321)
    val insertStatement = ss.insert(table, row)
    insertStatement.execute()

    val selectStatement = ss.selectOneStar(table, CustomSelect("mystr"))
    selectStatement.execute().right.value.value.as[CustomTable] shouldBe row
  }
}
