package com.weather.scalacass

import com.datastax.driver.core.exceptions.{TransportException, QueryExecutionException, NoHostAvailableException, BusyConnectionException, ConnectionException, DriverInternalError, PagingStateException, QueryExecutionException, UnsupportedFeatureException, UnsupportedProtocolVersionException}

trait NotRecoverableVersionSpecific {
  def apply(t: Throwable) = t match {
    case _: TransportException | _: QueryExecutionException | _: NoHostAvailableException |
         _: BusyConnectionException | _: ConnectionException | _: DriverInternalError |
         _: PagingStateException | _: QueryExecutionException | _: UnsupportedFeatureException |
         _: UnsupportedProtocolVersionException => true
    case _                                      => false
  }
}
