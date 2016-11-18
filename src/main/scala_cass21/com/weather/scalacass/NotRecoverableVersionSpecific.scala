package com.weather.scalacass

import com.datastax.driver.core.exceptions.{NoHostAvailableException, QueryExecutionException, DriverInternalError, PagingStateException, UnsupportedFeatureException}

trait NotRecoverableVersionSpecific {
  def apply(t: Throwable): Boolean = t match {
    case _: NoHostAvailableException | _: QueryExecutionException | _: DriverInternalError | _: PagingStateException |
      _: UnsupportedFeatureException => true
    case _ => false
  }
}