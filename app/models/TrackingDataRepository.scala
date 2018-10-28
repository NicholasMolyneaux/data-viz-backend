package models

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.CustomExecutionContext
import play.api.{Logger, MarkerContext}
import slick.basic.StaticDatabaseConfig
import slick.jdbc.{GetResult, JdbcProfile}
import slick.jdbc.meta.MTable
import slick.jdbc.JdbcCapabilities._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}


/**
  * A pure non-blocking interface for the PostRepository.
  */
trait TrackingDataRepository {

  def list()(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

  def get(id: Long)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]]

  def get(ids: Vector[Long])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

  def getInfrastructures(implicit mc: MarkerContext): Future[Iterable[InfrastructureSummary]]

  def createInfraIfNotExisting(infra: String, description: String)(implicit mc: MarkerContext): Future[Unit]

  def createWallsTable(infra: String, walls: Iterable[(Double, Double, Double, Double, Int)])(implicit mc: MarkerContext): Future[Unit]

  def getWalls(infra: String)(implicit mc: MarkerContext): Future[Iterable[WallData]]

  def createODZonesTable(infra: String, zones: Iterable[(String, Double, Double, Double, Double, Double, Double, Double, Double, Boolean)])(implicit mc: MarkerContext): Future[Unit]

  def getZones(infra: String)(implicit mc: MarkerContext): Future[Iterable[ZoneData]]


  def dropInfra(infra: String): Future[Unit]

  var schemaNameOld: String = "testNew"

}

trait Schema {
  def name: String
}

class SummaryExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "repository.dispatcher")

/**
  * A trivial implementation for the Post Repository.
  *
  * A custom execution context is used here to establish that blocking operations should be
  * executed in a different thread than Play's ExecutionContext, which is used for CPU bound tasks
  * such as rendering.
  */
@Singleton
class TrackingDataRepositoryImpl @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: SummaryExecutionContext) extends TrackingDataRepository {


  // We want the JdbcProfile for this provider
  private val dbConfig = dbConfigProvider.get[JdbcProfile]


  // These imports are important, the first one brings db into scope, which will let you do the actual db operations.
  // The second one brings the Slick DSL into scope, which lets you define the table and other queries.
  import dbConfig._
  import profile.api._

  // create table general_summary(id SERIAL PRIMARY KEY, name varchar(50), description varchar(1000));
  private class GeneralSummaryTable(tag: Tag) extends Table[InfrastructureSummary](tag, "general_summary") {

    /** The destinatoion zone column */
    def name = column[String]("name")

    /** The entry time column */
    def description = column[String]("description")

    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (name, description, id) <> ((InfrastructureSummary.apply _).tupled, InfrastructureSummary.unapply)
  }

  /**
    * Here we define the summary table.
    */
  private class WallTable(tag: Tag, schema: Option[String]) extends Table[WallData](tag, schema, "walls") {


    /** The origin zone column */
    def x1 = column[Double]("x1")

    /** The destinatoion zone column */
    def y1 = column[Double]("y1")

    /** The entry time column */
    def x2 = column[Double]("x2")

    /** The exit time column */
    def y2 = column[Double]("y2")

    def wtype = column[Int]("wtype")

    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (x1, y1, x2, y2, wtype, id) <> ((WallData.apply _).tupled, WallData.unapply)
  }

  /**
    * Here we define the summary table.
    */
  private class ZonesTable(tag: Tag) extends Table[ZoneData](tag, Some(schemaNameOld), "odzones") {


    /** The origin zone column */
    def name = column[String]("name")

    /** The destinatoion zone column */
    def ax = column[Double]("ax")

    def ay = column[Double]("ay")

    def bx = column[Double]("bx")

    def by = column[Double]("by")

    def cx = column[Double]("cx")

    def cy = column[Double]("cy")

    def dx = column[Double]("dx")

    def dy = column[Double]("dy")

    def isod = column[Boolean]("isod")

    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)


    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (name, ax, ay, bx, by, cx, cy, dx, dy, isod, id) <> ((ZoneData.apply _).tupled, ZoneData.unapply)
  }

  /**
    * Here we define the summary table.
    */
  private class SummaryTable(tag: Tag) extends Table[PersonSummaryData](tag, Some(schemaNameOld), "summary") {

    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Long]("id", O.PrimaryKey)

    /** The origin zone column */
    def origin = column[String]("origin")

    /** The destinatoion zone column */
    def destination = column[String]("destination")

    /** The entry time column */
    def entryTime = column[Double]("entrytime")

    /** The exit time column */
    def exitTime = column[Double]("exittime")


    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (id, origin, destination, entryTime, exitTime) <> ((PersonSummaryData.apply _).tupled, PersonSummaryData.unapply)
  }


  import dbConfig.profile.api._

  /**
    * The starting point for all queries on the Summary table.
    */
  private val summary = TableQuery[SummaryTable]
  private def wallTable(schemaName: String) = new TableQuery( new WallTable(_, Some(schemaName)))
  private val generalSummary = TableQuery[GeneralSummaryTable]
  private val ODZonesTable = TableQuery[ZonesTable]


  override def list()(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
    summary.result
  }

  override def get(id: Long)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]] = db.run {
    summary.filter(r => r.id === id).result.headOption
  }

  override def get(ids: Vector[Long])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
    (for {r <- summary if r.id inSetBind ids} yield r).result
  }

  override def getInfrastructures(implicit mc: MarkerContext): Future[Iterable[InfrastructureSummary]] = db.run {
    //db.run{MTable.getTables.map(tables => InfrastructureList(tables.flatMap(t => t.name.schema).distinct))}
    generalSummary.result
  }

  //import slick.driver.SQLiteDriver.api._
  import profile.api._

  def createInfraIfNotExisting(infra: String, descr: String)(implicit mc: MarkerContext) = {
    val exists = db.run {
      MTable.getTables.map(tables => tables.flatMap(t => t.name.schema).contains(infra))
    }
    exists.collect {
      case true => {
        Logger.warn(s"Schema $infra already exists. ")
        db.run(DBIO.seq(
          generalSummary += InfrastructureSummary(infra, descr))
        )
      }
      case false => {
        Logger.warn(s"Schema $infra needs to be created:")
        db.run(DBIO.seq(
          sqlu"""CREATE SCHEMA #$infra""",
          generalSummary += InfrastructureSummary(infra, descr))
        )
      }
    }
  }


  def createWallsTable(infra: String, walls: Iterable[(Double, Double, Double, Double, Int)])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.walls needs to be created. Inserting " + walls.size + " walls.")
    db.run(DBIO.seq(
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.walls (id SERIAL PRIMARY  KEY, x1 DOUBLE PRECISION , y1 DOUBLE PRECISION , x2 DOUBLE PRECISION , y2 DOUBLE PRECISION , wtype INTEGER)""",
      wallTable(infra) ++= walls.map(w => WallData(w._1, w._2, w._3, w._4, w._5))
    )
    )
  }

  implicit val getWallDataResult = GetResult(r => WallData(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextInt()))


  def getWalls(infra: String)(implicit mc: MarkerContext): Future[Iterable[WallData]] = {
    //this.schemaNameOld = infra
    /*db.run {
        sql"""SELECT * from #${infra}.walls""".as[WallData]
    }*/
    db.run{ wallTable(infra).result}

  }

  def createODZonesTable(infra: String, zones: Iterable[(String, Double, Double, Double, Double, Double, Double, Double, Double, Boolean)])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.graph needs to be created:")
    db.run {
      DBIO.seq(
        sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.odzones (name VARCHAR(50), ax DOUBLE PRECISION , ay DOUBLE PRECISION , bx DOUBLE PRECISION, by DOUBLE PRECISION, cx DOUBLE PRECISION , cy DOUBLE PRECISION , dx DOUBLE PRECISION , dy DOUBLE PRECISION , isod BOOLEAN, id SERIAL PRIMARY KEY)""",
        ODZonesTable ++= zones.map(od => ZoneData(od._1, od._2, od._3, od._4, od._5, od._6, od._7, od._8, od._9, od._10))
      )
    }
  }

  def getZones(infra: String)(implicit mc: MarkerContext): Future[Iterable[ZoneData]] = {
    this.schemaNameOld = infra
    db.run { ODZonesTable.filter(_.isod).result }
  }

  def dropInfra(infra: String): Future[Unit] = {
    Logger.warn(s"Dropping infrastructure (schema) $infra !")
    db.run {
      DBIO.seq(
        generalSummary.filter(_.name === infra).delete,
        sqlu""" DROP SCHEMA #${infra} CASCADE """
      )
    }
  }

}



