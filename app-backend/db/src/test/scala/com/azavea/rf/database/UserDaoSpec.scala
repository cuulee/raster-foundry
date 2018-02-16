package com.azavea.rf.database

import com.azavea.rf.datamodel.User
import com.azavea.rf.database.meta.RFMeta._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class UserDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(UserDao.selectF.query[User]) }
}
