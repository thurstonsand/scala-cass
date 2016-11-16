package com.weather.scalacass

import com.datastax.driver.core.exceptions.QueryExecutionException

class WrongPrimaryKeySizeException(m: String) extends QueryExecutionException(m)