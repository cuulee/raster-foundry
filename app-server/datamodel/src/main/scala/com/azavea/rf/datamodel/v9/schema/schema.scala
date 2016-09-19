package com.azavea.rf.datamodel.v9.schema
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object tables extends {
  val profile = com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
} with tables

import com.azavea.rf.datamodel.enums._

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait tables {
  val profile: com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Organizations.schema ++ Scenes.schema ++ Users.schema ++ UsersToOrganizations.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Organizations
   *  @param id Database column id SqlType(uuid), PrimaryKey
   *  @param createdAt Database column created_at SqlType(timestamp)
   *  @param modifiedAt Database column modified_at SqlType(timestamp)
   *  @param name Database column name SqlType(varchar), Length(255,true) */
  case class OrganizationsRow(id: java.util.UUID, createdAt: java.sql.Timestamp, modifiedAt: java.sql.Timestamp, name: String)
  /** GetResult implicit for fetching OrganizationsRow objects using plain SQL queries */
  implicit def GetResultOrganizationsRow(implicit e0: GR[java.util.UUID], e1: GR[java.sql.Timestamp], e2: GR[String]): GR[OrganizationsRow] = GR{
    prs => import prs._
    OrganizationsRow.tupled((<<[java.util.UUID], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[String]))
  }
  /** Table description of table organizations. Objects of this class serve as prototypes for rows in queries. */
  class Organizations(_tableTag: Tag) extends Table[OrganizationsRow](_tableTag, "organizations") {
    def * = (id, createdAt, modifiedAt, name) <> (OrganizationsRow.tupled, OrganizationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(name)).shaped.<>({r=>import r._; _1.map(_=> OrganizationsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid), PrimaryKey */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
    /** Database column created_at SqlType(timestamp) */
    val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column modified_at SqlType(timestamp) */
    val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
    /** Database column name SqlType(varchar), Length(255,true) */
    val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
  }
  /** Collection-like TableQuery object for table Organizations */
  lazy val Organizations = new TableQuery(tag => new Organizations(tag))

  /** Entity class storing rows of table Scenes
   *  @param id Database column id SqlType(uuid), PrimaryKey
   *  @param createdAt Database column created_at SqlType(timestamp)
   *  @param modifiedAt Database column modified_at SqlType(timestamp)
   *  @param organizationId Database column organization_id SqlType(uuid)
   *  @param createdBy Database column created_by SqlType(varchar), Length(255,true)
   *  @param modifiedBy Database column modified_by SqlType(varchar), Length(255,true)
   *  @param ingestSizeBytes Database column ingest_size_bytes SqlType(int4)
   *  @param visibility Database column visibility SqlType(visibility)
   *  @param resolutionMeters Database column resolution_meters SqlType(float4)
   *  @param tags Database column tags SqlType(_text), Length(2147483647,false)
   *  @param datasource Database column datasource SqlType(varchar), Length(255,true)
   *  @param sceneMetadata Database column scene_metadata SqlType(jsonb), Length(2147483647,false)
   *  @param cloudCover Database column cloud_cover SqlType(float4), Default(None)
   *  @param acquisitionDate Database column acquisition_date SqlType(timestamp), Default(None)
   *  @param thumbnailStatus Database column thumbnail_status SqlType(job_status)
   *  @param boundaryStatus Database column boundary_status SqlType(job_status)
   *  @param status Database column status SqlType(job_status) */
  case class ScenesRow(id: java.util.UUID, createdAt: java.sql.Timestamp, modifiedAt: java.sql.Timestamp, organizationId: java.util.UUID, createdBy: String, modifiedBy: String, ingestSizeBytes: Int, visibility: Visibility, resolutionMeters: Float, tags: List[String], datasource: String, sceneMetadata: Map[String, Any], cloudCover: Option[Float] = None, acquisitionDate: Option[java.sql.Timestamp] = None, thumbnailStatus: JobStatus, boundaryStatus: JobStatus, status: JobStatus)
  /** GetResult implicit for fetching ScenesRow objects using plain SQL queries */
  implicit def GetResultScenesRow(implicit e0: GR[java.util.UUID], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Int], e4: GR[Visibility], e5: GR[Float], e6: GR[List[String]], e7: GR[Map[String, Any]], e8: GR[Option[Float]], e9: GR[Option[java.sql.Timestamp]], e10: GR[JobStatus]): GR[ScenesRow] = GR{
    prs => import prs._
    ScenesRow.tupled((<<[java.util.UUID], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[java.util.UUID], <<[String], <<[String], <<[Int], <<[Visibility], <<[Float], <<[List[String]], <<[String], <<[Map[String, Any]], <<?[Float], <<?[java.sql.Timestamp], <<[JobStatus], <<[JobStatus], <<[JobStatus]))
  }
  /** Table description of table scenes. Objects of this class serve as prototypes for rows in queries. */
  class Scenes(_tableTag: Tag) extends Table[ScenesRow](_tableTag, "scenes") {
    def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, ingestSizeBytes, visibility, resolutionMeters, tags, datasource, sceneMetadata, cloudCover, acquisitionDate, thumbnailStatus, boundaryStatus, status) <> (ScenesRow.tupled, ScenesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(organizationId), Rep.Some(createdBy), Rep.Some(modifiedBy), Rep.Some(ingestSizeBytes), Rep.Some(visibility), Rep.Some(resolutionMeters), Rep.Some(tags), Rep.Some(datasource), Rep.Some(sceneMetadata), cloudCover, acquisitionDate, Rep.Some(thumbnailStatus), Rep.Some(boundaryStatus), Rep.Some(status)).shaped.<>({r=>import r._; _1.map(_=> ScenesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13, _14, _15.get, _16.get, _17.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid), PrimaryKey */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
    /** Database column created_at SqlType(timestamp) */
    val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column modified_at SqlType(timestamp) */
    val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
    /** Database column organization_id SqlType(uuid) */
    val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
    /** Database column created_by SqlType(varchar), Length(255,true) */
    val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
    /** Database column modified_by SqlType(varchar), Length(255,true) */
    val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
    /** Database column ingest_size_bytes SqlType(int4) */
    val ingestSizeBytes: Rep[Int] = column[Int]("ingest_size_bytes")
    /** Database column visibility SqlType(visibility) */
    val visibility: Rep[Visibility] = column[Visibility]("visibility")
    /** Database column resolution_meters SqlType(float4) */
    val resolutionMeters: Rep[Float] = column[Float]("resolution_meters")
    /** Database column tags SqlType(_text), Length(2147483647,false) */
    val tags: Rep[List[String]] = column[List[String]]("tags", O.Length(2147483647,varying=false))
    /** Database column datasource SqlType(varchar), Length(255,true) */
    val datasource: Rep[String] = column[String]("datasource", O.Length(255,varying=true))
    /** Database column scene_metadata SqlType(jsonb), Length(2147483647,false) */
    val sceneMetadata: Rep[Map[String, Any]] = column[Map[String, Any]]("scene_metadata", O.Length(2147483647,varying=false))
    /** Database column cloud_cover SqlType(float4), Default(None) */
    val cloudCover: Rep[Option[Float]] = column[Option[Float]]("cloud_cover", O.Default(None))
    /** Database column acquisition_date SqlType(timestamp), Default(None) */
    val acquisitionDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("acquisition_date", O.Default(None))
    /** Database column thumbnail_status SqlType(job_status) */
    val thumbnailStatus: Rep[JobStatus] = column[JobStatus]("thumbnail_status")
    /** Database column boundary_status SqlType(job_status) */
    val boundaryStatus: Rep[JobStatus] = column[JobStatus]("boundary_status")
    /** Database column status SqlType(job_status) */
    val status: Rep[JobStatus] = column[JobStatus]("status")

    /** Foreign key referencing Organizations (database name scenes_organization_id_fkey) */
    lazy val organizationsFk = foreignKey("scenes_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Users (database name scenes_created_by_fkey) */
    lazy val usersFk2 = foreignKey("scenes_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Users (database name scenes_modified_by_fkey) */
    lazy val usersFk3 = foreignKey("scenes_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Scenes */
  lazy val Scenes = new TableQuery(tag => new Scenes(tag))

  /** Entity class storing rows of table Users
   *  @param id Database column id SqlType(varchar), PrimaryKey, Length(255,true) */
  case class UsersRow(id: String)
  /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
  implicit def GetResultUsersRow(implicit e0: GR[String]): GR[UsersRow] = GR{
    prs => import prs._
    UsersRow(<<[String])
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends Table[UsersRow](_tableTag, "users") {
    def * = id <> (UsersRow, UsersRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = Rep.Some(id).shaped.<>(r => r.map(_=> UsersRow(r.get)), (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(varchar), PrimaryKey, Length(255,true) */
    val id: Rep[String] = column[String]("id", O.PrimaryKey, O.Length(255,varying=true))
  }
  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag))

  /** Entity class storing rows of table UsersToOrganizations
   *  @param userId Database column user_id SqlType(varchar), Length(255,true)
   *  @param organizationId Database column organization_id SqlType(uuid)
   *  @param role Database column role SqlType(varchar), Length(255,true)
   *  @param createdAt Database column created_at SqlType(timestamp)
   *  @param modifiedAt Database column modified_at SqlType(timestamp) */
  case class UsersToOrganizationsRow(userId: String, organizationId: java.util.UUID, role: String, createdAt: java.sql.Timestamp, modifiedAt: java.sql.Timestamp)
  /** GetResult implicit for fetching UsersToOrganizationsRow objects using plain SQL queries */
  implicit def GetResultUsersToOrganizationsRow(implicit e0: GR[String], e1: GR[java.util.UUID], e2: GR[java.sql.Timestamp]): GR[UsersToOrganizationsRow] = GR{
    prs => import prs._
    UsersToOrganizationsRow.tupled((<<[String], <<[java.util.UUID], <<[String], <<[java.sql.Timestamp], <<[java.sql.Timestamp]))
  }
  /** Table description of table users_to_organizations. Objects of this class serve as prototypes for rows in queries. */
  class UsersToOrganizations(_tableTag: Tag) extends Table[UsersToOrganizationsRow](_tableTag, "users_to_organizations") {
    def * = (userId, organizationId, role, createdAt, modifiedAt) <> (UsersToOrganizationsRow.tupled, UsersToOrganizationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(userId), Rep.Some(organizationId), Rep.Some(role), Rep.Some(createdAt), Rep.Some(modifiedAt)).shaped.<>({r=>import r._; _1.map(_=> UsersToOrganizationsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column user_id SqlType(varchar), Length(255,true) */
    val userId: Rep[String] = column[String]("user_id", O.Length(255,varying=true))
    /** Database column organization_id SqlType(uuid) */
    val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
    /** Database column role SqlType(varchar), Length(255,true) */
    val role: Rep[String] = column[String]("role", O.Length(255,varying=true))
    /** Database column created_at SqlType(timestamp) */
    val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column modified_at SqlType(timestamp) */
    val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")

    /** Primary key of UsersToOrganizations (database name users_to_organizations_pkey) */
    val pk = primaryKey("users_to_organizations_pkey", (userId, organizationId))

    /** Foreign key referencing Organizations (database name users_to_organizations_organization_id_fkey) */
    lazy val organizationsFk = foreignKey("users_to_organizations_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Users (database name users_to_organizations_user_id_fkey) */
    lazy val usersFk = foreignKey("users_to_organizations_user_id_fkey", userId, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table UsersToOrganizations */
  lazy val UsersToOrganizations = new TableQuery(tag => new UsersToOrganizations(tag))
}

object Version{
  def version = 9
}
