package com.weather.scalacass

import com.datastax.driver.core.Row
import shapeless._, labelled._

trait CCCassFormat[T] {
  def decode(r: Row): Either[Throwable, T]
  def encode(o: T): List[(String, AnyRef)]
  def namesAndTypes: List[(String, String)]
}

trait LowPriorityCCCassFormat {
  implicit val hNilDecoder = new CCCassFormat[HNil] {
    def decode(r: Row) = Right(HNil)
    def encode(o: HNil) = Nil
    def namesAndTypes = Nil
  }

  implicit def hConsDecoder[K <: Symbol, H, T <: HList](implicit w: Witness.Aux[K], tdH: Lazy[CassFormat[H]], tdT: Lazy[CCCassFormat[T]]) =
    new CCCassFormat[FieldType[K, H] :: T] {
      def decode(r: Row) = for {
        h <- tdH.value.decode(r, w.value.name.toString).right
        t <- tdT.value.decode(r).right
      } yield field[K](h) :: t

      def encode(o: FieldType[K, H] :: T) = {
        val t = tdT.value.encode(o.tail)
        (w.value.name.toString, tdH.value.t2f(o.head)) :: t
      }

      def namesAndTypes = (w.value.name.toString, tdH.value.cassType) :: tdT.value.namesAndTypes
    }

  implicit def hListConverter[T, Repr](implicit gen: LabelledGeneric.Aux[T, Repr], sg: Lazy[CCCassFormat[Repr]]): CCCassFormat[T] =
    new CCCassFormat[T] {
      def decode(r: Row): Either[Throwable, T] = sg.value.decode(r).right.map(gen.from)
      def encode(o: T) = sg.value.encode(gen.to(o))
      def namesAndTypes = sg.value.namesAndTypes
    }
}

object CCCassFormat extends LowPriorityCCCassFormat
