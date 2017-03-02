import sbt._

/** Generate boiletplate classes for TupleXX
  *
  * Copied, with some modifications, from
  * [[https://github.com/milessabin/shapeless/blob/master/project/Boilerplate.scala Shapeless]]
  *
  * @author Miles Sabin
  * @author Kevin Wright
  * @author Travis Brown
  */
object Boilerplate {
  import scala.StringContext._

  implicit class BlockHelper(val sc: StringContext) extends AnyVal {
    def block(args: Any*): String = {
      val interpolated = sc.standardInterpolator(treatEscapes, args)
      val rawLines = interpolated.split('\n')
      val trimmedLines = rawLines.map(_.dropWhile(_.isWhitespace))
      trimmedLines.mkString("\n")
    }
  }

  val header = "// auto-generated boilerplate"
  val maxArity = 22

  class TemplateVals(val arity: Int) {
    val synTypes = (0 until arity).map(n => s"A$n")
    val synVals = (0 until arity).map(n => s"a$n")
    val `A..N` = synTypes.mkString(", ")
    val `a..n` = synVals.mkString(", ")
    val `_.._` = Seq.fill(arity)("_").mkString(", ")
    val `(A..N)` = if (arity == 1) "Tuple1[A0]" else synTypes.mkString("(", ", ", ")")
    val `(_.._)` = if (arity == 1) "Tuple1[_]" else Seq.fill(arity)("_").mkString("(", ", ", ")")
    val `(a..n)` = if (arity == 1) "Tuple1(a)" else synVals.mkString("(", ", ", ")")
  }

  /** Blocks in the templates below use a custom interpolator, combined with post-processing to
    * produce the body.
    *
    * - The contents of the `header` val is output first
    * - Then the first block of lines beginning with '|'
    * - Then the block of lines beginning with '-' is replicated once for each arity,
    *   with the `templateVals` already pre-populated with relevant vals for that arity
    * - Then the last block of lines prefixed with '|'
    *
    * The block otherwise behaves as a standard interpolated string with regards to variable
    * substitution.
    */
  trait Template {
    def filename(root: File): File
    def content(tv: TemplateVals): String
    def range: IndexedSeq[Int] = 1 to maxArity
    def body: String = {
      val headerLines = header.split('\n')
      val raw = range.map(n => content(new TemplateVals(n)).split('\n').filterNot(_.isEmpty))
      val preBody = raw.head.takeWhile(_.startsWith("|")).map(_.tail)
      val instances = raw.flatMap(_.filter(_.startsWith("-")).map(_.tail))
      val postBody = raw.head.dropWhile(_.startsWith("|")).dropWhile(_.startsWith("-")).map(_.tail)
      (headerLines ++ preBody ++ instances ++ postBody).mkString("\n")
    }
  }

  object GenProductDecoders extends Template {
    override def range: IndexedSeq[Int] = 1 to maxArity

    def filename(root: File): File = root / "com" / "weather" / "scalacass" / "ProductCCCassFormatDecoders.scala"

    def content(tv: TemplateVals): String = {
      import tv._

      val instances = synTypes.map(tpe => s"decode$tpe")

      val instanceMembers = synTypes.map(tpe => s"decode$tpe: CassFormatDecoder[$tpe]").mkString(", ")
      val names = synTypes.map(tpe => s"name$tpe")
      val memberNames = names.map(n => s"$n: String").mkString(", ")
      val results = (synVals zip instances zip names).map { case ((v, i), name) => s"$v <- $i.decode(r, $name)" }.mkString("; ")
      val fnCombine = s"f(${`a..n`})"

      block"""
        |package com.weather.scalacass
        |
        |import com.datastax.driver.core.Row
        |import scsession.SCStatement.RightBiasedEither
        |
        |private[scalacass] trait ProductCCCassFormatDecoders {
        -  /**
        -    * @group Product
        -    */
        -  final def forProduct$arity[${`A..N`}, Target]($memberNames)(f: (${`A..N`}) => Target)(implicit $instanceMembers): CCCassFormatDecoder[Target] =
        -    new CCCassFormatDecoder[Target] {
        -      def decode(r: Row): Result[Target] = for {
        -        $results
        -      } yield $fnCombine
        -    }
        |}
      """
    }
  }

  object GenProductEncoders extends Template {
    override def range: IndexedSeq[Int] = 1 to maxArity

    def filename(root: File): File = root / "com" / "weather" / "scalacass" / "ProductCCCassFormatEncoders.scala"

    def content(tv: TemplateVals): String = {
      import tv._

      val names = synTypes.map(tpe => s"name$tpe")
      val encodedTypes = synTypes.map(tpe => s"encoded$tpe")
      val instances = synTypes.map(tpe => s"encode$tpe")

      val memberNames = names.map(n => s"$n: String").mkString(", ")
      val instanceMembers = synTypes.map(tpe => s"encode$tpe: CassFormatEncoder[$tpe]").mkString(", ")
      val cassTypes = instances.map(i => s"$i.cassType").mkString(", ")
      val results = (encodedTypes zip instances zip synVals).map { case ((encodedTpe, i), v) => s"$encodedTpe <- $i.encode($v)"}.mkString("; ")
      val namesCombined = (names zip encodedTypes).map { case (n, encodedTpe) => s"($n, $encodedTpe)"}.mkString(", ")
      val queryCombined = (instances zip synVals zip names zip encodedTypes).map { case (((i, v), name), encodedTpe) => s"($i.withQuery($v, $name), $encodedTpe)"}.mkString(", ")

      block"""
        |package com.weather.scalacass
        |
        |import scsession.SCStatement.RightBiasedEither
        |
        |private[scalacass] trait ProductCCCassFormatEncoders {
        -  /**
        -    * @group Product
        -    */
        -  final def forProduct$arity[${`A..N`}, Source]($memberNames)(f: Source => (${`A..N`}))(implicit $instanceMembers): CCCassFormatEncoder[Source] =
        -    new CCCassFormatEncoder[Source] {
        -      val names = List($memberNames)
        -      val types = List($cassTypes)
        -      def encodeWithName(from: Source): Result[List[(String, AnyRef)]] = {
        -        val (${`a..n`}) = f(from)
        -        for {
        -          $results
        -        } yield List($namesCombined)
        -      }
        -      def encodeWithQuery(from: Source): Result[List[(String, AnyRef)]] = {
        -        val (${`a..n`}) = f(from)
        -        for {
        -          $results
        -        } yield List($queryCombined)
        -      }
        -    }
        |}
      """
    }
  }

  val templates: Seq[Template] = Seq(
    GenProductDecoders,
    GenProductEncoders
  )

  def gen(dir: File): Seq[File] = templates.map { template =>
    val tgtFile = template.filename(dir)
    IO.write(tgtFile, template.body)
    tgtFile
  }
}