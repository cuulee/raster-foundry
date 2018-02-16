package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}


object AoiDao extends Dao[AOI] {

  val tableName = "aois"

  val selectF =
    sql"""
      SELECT
        id, created_at, modified_at, organization_id,
        created_by, modified_by, owner, area, filters
      FROM
    """ ++ tableF

  def create(
    user: User,
    owner: Option[String],
    organizationId: UUID,
    area: Projected[MultiPolygon],
    filters: Json
  ): ConnectionIO[AOI] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = Ownership.checkOwner(user, owner)
    val userId = user.id
    println(id, now, ownerId, userId)
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, organization_id,
        created_by, modified_by, owner, area, filters)
      VALUES
        ($id, $now, $now, $organizationId,
        $userId, $userId, $ownerId, $area, $filters)
    """).update.withUniqueGeneratedKeys[AOI](
      "id", "created_at", "modified_at", "organization_id",
      "created_by", "modified_by", "owner", "area", "filters"
    )
  }

  def updateAOI(aoi: AOI, id: UUID, user: User) = ???

  def deleteAOI(id: UUID, user: User) = ???

  /** Create an AOI - note has to replicate some of this logic here:
    *
    *  for {
            a <- AOIs.insertAOI(aoi.toAOI(user))
            _ <- AoisToProjects.insert(AoiToProject(a.id, projectId, true, new Timestamp((new Date).getTime)))
          } yield a
        }) { a =>
          complete(StatusCodes.Created, a)
        }

    * @param aoi
    * @param projectId
    * @return
    */
  def createAOI(aoi: AOI, projectId: UUID, user: User): Future[AOI] = ???

}

object AoiJson {
  import io.circe._
  import scala.concurrent.Future
  // Potential strategy for replacement of `AOI.Create`
  def create(
    aoiJson: Json,
    user: User
  )(implicit xa: Transactor[IO]): Either[DecodingFailure, Future[AOI]] = {
    val c = aoiJson.hcursor
    (c.get[Option[String]]("owner"),
     c.get[UUID]("organizationId"),
     c.get[Projected[MultiPolygon]]("area"),
     c.get[Json]("filters"))
       .mapN({ case (owner, organizationId, area, filters) =>
         val creation = AoiDao.create(user, owner, organizationId, area, filters)
         creation.transact(xa).unsafeToFuture()
       })
  }
}
