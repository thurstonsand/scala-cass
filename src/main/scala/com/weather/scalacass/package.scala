package com.weather

package object scalacass {
  type Result[T] = Either[Throwable, T]
}
