package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.ToolRunFields
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class ToolRunFieldsSort[E, D <: ToolRunFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "name" =>
        query.sortBy(q => (f(q).name.byOrder(ord), f(q).id))
      case _ => query
    }
  }
}
