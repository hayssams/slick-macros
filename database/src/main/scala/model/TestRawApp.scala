package model

import scala.slick.driver.H2Driver.simple._
import scala.language.existentials
import java.lang.reflect.Method
import scala.slick.SlickException
import scala.reflect.ClassTag
import scala.slick.jdbc.{ GetResult, StaticQuery => Q }
import Q.interpolation
import scala.language.dynamics
import language.experimental.macros
import scala.slick.lifted.MappedProjection
import TupleMethods._
import scala.slick.profile.BasicDriver
import scala.slick.jdbc.JdbcBackend
import scala.slick.profile._
import slickmacros.annotations.ModelMacro._
import slickmacros.dao.Crud._

object TestRawApp extends App {

  object UserRights extends Enumeration {
    type UserRights = Value
    val ADMIN = Value(1)
    val GUEST = Value(2)
  }
  import UserRights._
  implicit def UserRightsTypeMapper = MappedColumnType.base[UserRights.Value, Int](
    {
      it => it.id
    },
    {
      id => UserRights(id)
    })
  case class Company(id: Option[Long], name: String, website: String, large: Array[Byte], var dateCreated: java.sql.Timestamp = null, var lastUpdated: java.sql.Timestamp = null) {
    def xid = id.getOrElse(throw new Exception("Object has no id yet"))
  }

  class CompanyTable(tag: Tag) extends Table[Company](tag, "company") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def website = column[String]("website")
    def large = column[Array[Byte]]("large")
    def dateCreated = column[java.sql.Timestamp]("dateCreated");
    def lastUpdated = column[java.sql.Timestamp]("lastUpdated");

    def * = (id.$qmark, name, website, large, dateCreated, lastUpdated).shaped <> ({
      case (id, name, website, large, dateCreated, lastUpdated) => Company(id, name, website, large, dateCreated, lastUpdated)
    },
      { x: Company => Some((x.id, x.name, x.website, x.large, x.dateCreated, x.lastUpdated)) })

    // def * = (id.?, name, website, large, dateCreated, lastUpdated) <> (Company.tupled, Company.unapply _)
    def forInsert = (name ~ website ~ large ~ dateCreated ~ lastUpdated).shaped <> ({ t => Company(None, t._1, t._2, t._3, t._4, t._5) }, { (c: Company) => Some((c.name, c.website, c.large, c.dateCreated, c.lastUpdated)) })
    def members = memberQuery.where(_.companyId === id)
  }
  val companyQuery = TableQuery[CompanyTable]

  case class Address(num: Int, road: String, zip: String)

  case class Member(id: Option[Long], login: String, rights: UserRights, add: Address, companyId: Long, managerId: Option[Long]) {
    val companyQ = companyQuery.findBy(_.id)
    def company(implicit session: Session) = companyQuery.where(((x$1) => x$1.id.$eq$eq$eq(companyId))).first; //companyQ.first(companyId)
    def xid = id.getOrElse(throw new Exception("Object has no id yet"))

  }

  class MemberTable(tag: Tag) extends Table[Member](tag, "member") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc);
    def * = (id.?, login, rights, (num, road, zip), companyId, managerId).shaped <> ({
      case (id, login, rights, add, companyId, managerId) =>
        Member(id, login, rights, Address.tupled.apply(add), companyId, managerId)
    }, { x: Member => Some((x.id, x.login, x.rights, Address.unapply(x.add).get, x.companyId, x.managerId)) })

    def login = column[String]("login");
    def num = column[Int]("num");
    def road = column[String]("road");
    def zip = column[String]("zip");
    def rights = column[UserRights]("rights");
    def managerId = column[Option[Long]]("managerId");
    def companyId = column[Long]("companyId");
    def company = foreignKey("member2company", companyId, companyQuery)(_.id)
    // ({ t => Member(None, t._1, t._2, t._3, t._4) }, { (x: Member) => Some((x.login, x.rights, x.companyId, x.managerId)) });
    def forInsert = (login, rights, (num, road, zip), companyId, managerId).shaped <> ({
      case (login, rights, add, companyId, managerId) =>
        Member(None, login, rights, Address.tupled.apply(add), companyId, managerId)
    }, { x: Member => Some((x.login, x.rights, Address.unapply(x.add).get, x.companyId, x.managerId)) })
  }
  val memberQuery = TableQuery[MemberTable]

  case class Project(id: Option[Long], name: String, companyId: Long) {
    def xid = id.getOrElse(throw new Exception("Object has no id yet"))
    def members = Project2Members.where(_.projectId === id)
    def company(implicit session: Session) = companyQuery.where(_.id === companyId).first
  }

  class ProjectTable(tag: Tag) extends Table[Project](tag, "project") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def * = id.? ~ name ~ companyId <> (Project.tupled, Project.unapply _)
    def name = column[String]("name");
    def companyId = column[Long]("companyId");
    def forInsert = (name ~ companyId).shaped <> ({ t => Project(None, t._1, t._2) }, { (x: Project) => Some((x.name, x.companyId)) });
    def companyFK = foreignKey("project2company", companyId, companyQuery)(_.id)
  }
  val projectQuery = TableQuery[ProjectTable]

  //object ProjectDAO extends Crud[Project, ProjectTable](projectQuery) {}

  case class Project2Member(projectId: Long, memberId: Long) {
    def project(implicit session: Session) = projectQuery.where(((x$1) => x$1.id.$eq$eq$eq(projectId))).first;
    def member(implicit session: Session) = memberQuery.where(((x$1) => x$1.id.$eq$eq$eq(memberId))).first
  }

  class Project2MemberTable(tag: Tag) extends Table[Project2Member](tag, "project2member") {
    def projectId = column[Long]("projectId");
    def memberId = column[Long]("memberId");
    def * = (projectId ~ memberId).shaped <> (Project2Member.tupled, Project2Member.unapply _)
    def projectFK = foreignKey("project2member2project", projectId, projectQuery)(((x$1) => x$1.id));
    def memberFK = foreignKey("project2member2member", memberId, memberQuery)(((x$1) => x$1.id))
  }
  val Project2Members = TableQuery[Project2MemberTable]
  companyQuery.ddl.createStatements.foreach(println)
  projectQuery.ddl.createStatements.foreach(println)
  memberQuery.ddl.createStatements.foreach(println)

}

