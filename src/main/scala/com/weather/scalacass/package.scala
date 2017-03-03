package com.weather

package object scalacass {
  type Result[T] = Either[Throwable, T]
  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class AnyOps[A](self: A) {
    def ===(other: A): Boolean = self == other
    def !==(other: A): Boolean = self != other
    def isNull: Boolean = self == null
  }
}
