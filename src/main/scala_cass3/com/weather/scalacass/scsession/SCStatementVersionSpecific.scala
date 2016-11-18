package com.weather.scalacass.scsession

import QueryBuildingBlock.If

trait SCUpdateStatementVersionSpecific { this: SCUpdateStatement =>
  def ifNotExists: SCUpdateStatement = copy(ifBlock = If.IfNotExists)
}