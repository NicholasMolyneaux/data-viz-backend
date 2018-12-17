package models

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig, HasDatabaseConfigProvider}
import play.api.libs.concurrent.CustomExecutionContext
import play.api.{Logger, MarkerContext}
import slick.basic.StaticDatabaseConfig
import slick.jdbc.{GetResult, JdbcProfile, PostgresProfile}
import slick.jdbc.meta.MTable
import slick.jdbc.JdbcCapabilities._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import com.github.tminglei.slickpg._
import slick.driver.PostgresDriver

// postgres=# insert into byid (id, time, x, y) select cast(id as int) as id, array_agg(time order by time) as time,array_agg(x order by time) as x, array_agg(y order by time) as y  from lausannepiwtest.test37_trajectories group by id;


/**
  * A pure non-blocking interface for the PostRepository.
  */
trait TrackingDataRepository {

  def getPedListSummary(schema: String, name: String)(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

  def getPedListSummary(schema: String, name: String, ids: Vector[String])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

  def getPedSummary(schema: String, name: String, id: String)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]]

  def getInfrastructures(implicit mc: MarkerContext): Future[Iterable[InfrastructureSummary]]

  def createInfraIfNotExisting(infra: String, description: String)(implicit mc: MarkerContext): Future[Unit]

  def createWallsTable(infra: String, walls: Iterable[(Double, Double, Double, Double, Int)])(implicit mc: MarkerContext): Future[Unit]

  def getWalls(infra: String)(implicit mc: MarkerContext): Future[Iterable[WallData]]

  def createODZonesTable(infra: String, zones: Iterable[(String, Double, Double, Double, Double, Double, Double, Double, Double, Boolean)])(implicit mc: MarkerContext): Future[Unit]

  def getZones(infra: String)(implicit mc: MarkerContext): Future[Iterable[ZoneData]]

  def insertRowIntoTrajTable(schema: String, name: String, row: TrajRowData): Future[Unit]

  def insertTrajTableFile(schema: String, name: String, file: String): Future[Unit]

  def getTrajectories(infra: String, traj: String): Future[Iterable[TrajRowData]]

  def insertRowIntoPedSummaryTable(schema: String, name: String, data: PersonSummaryData): Future[Unit]

  def createTrajTables(schema: String, name: String): Future[Unit]

  def getListTrajectories(infra: String): Future[Iterable[(String, Double, Double)]]

  def insertIntoTrajectoriesList(infra: String, name: String, descr: String): Future[Unit]

  def updateTrajectoryProcessingState(infra: String, name: String, tf: Boolean): Future[Unit]

  def setTrajectoryTimeBounds(infra: String, name: String, tmin: Double, tmax: Double): Future[Unit]

  def dropInfra(infra: String): Future[Unit]

  def dropTraj(infra: String, traj: String): Future[Unit]

  def getGates(infra: String)(implicit mc: MarkerContext): Future[Iterable[(Double, Double, Double, Double)]]

  def getMonitoredArea(infra: String)(implicit mc: MarkerContext): Future[Iterable[MonitoredArea]]

  def createGatesTable(infra: String, zones: Iterable[(Double, Double, Double, Double)])(implicit mc: MarkerContext): Future[Unit]

  def createMonitoredAreasTable(infra: String, zones: Iterable[MonitoredArea])(implicit mc: MarkerContext): Future[Unit]

  def groupTrajectoriesByID(infra: String, traj: String): Future[Unit]

  def getTrajectoriesByID(infra: String, traj: String, ids: Option[Iterable[String]]): Future[Iterable[TrajDataByID]]

  def getPedIDs(infra: String, traj: String): Future[Iterable[String]]

  def insertRowIntoTempTrajTimeTable(infra: String, traj: String, rows: Iterable[(String, Double, Double, Double)]): Future[Unit]

  def groupTrajectoriesByTime(infra: String, traj: String): Future[Unit]

  def getTrajectoriesByTime(infra: String, traj: String, lb: Option[Double] = None, ub: Option[Double] = None): Future[Iterable[TrajDataByTime]]
}


trait test2 {
  self: HasDatabaseConfig[MyPGProfile] =>

  import profile.api._

  /*class Companies(tag: Tag) extends Table[Company](tag, "COMPANY") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def props = column[JsValue]("PROPS")

    def * = (id.?, name, props) <> (Company.tupled, Company.unapply _)
  }*/
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
class TrackingDataRepositoryImpl @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: SummaryExecutionContext) extends TrackingDataRepository with test2 with HasDatabaseConfigProvider[MyPGProfile] {

  import profile.api._

  // We want the JdbcProfile for this provider
  //private val dbConfig = dbConfigProvider.get[test]


  // These imports are important, the first one brings db into scope, which will let you do the actual db operations.
  // The second one brings the Slick DSL into scope, which lets you define the table and other queries.
  //import dbConfig._
  //import profile.api._

  // create table general_summary(id SERIAL PRIMARY KEY, name varchar(50), description varchar(1000));
  private class GeneralSummaryTable(tag: Tag) extends Table[InfrastructureSummary](tag, "general_summary") {

    /** The destinatoion zone column */
    def name = column[String]("name")

    /** The entry time column */
    def description = column[String]("description")

    def xmin = column[Double]("x_min")

    def xmax = column[Double]("x_max")

    def ymin = column[Double]("y_min")

    def ymax = column[Double]("y_max")


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
    def * = (name, description, xmin, xmax, ymin, ymax, id) <> ((InfrastructureSummary.apply _).tupled, InfrastructureSummary.unapply)
  }

  // CREATE  TABLE trajectory_summary (infra VARCHAR(100), name VARCHAR(100), description VARCHAR(1000), isprocessed BOOLEAN, t_min DOUBLE PRECISION, t_max DOUBLE PRECISION);
  private class TrajectorySummaryTable(tag: Tag) extends Table[(String, String, String, Boolean, Double, Double)](tag, "trajectory_summary") {


    /** The destinatoion zone column */
    def infra = column[String]("infra")

    /** The entry time column */
    def name = column[String]("name")

    def description = column[String]("description")

    def isprocessed = column[Boolean]("isprocessed")

    def tmin = column[Double]("t_min")

    def tmax = column[Double]("t_max")

    /** The ID column, which is the primary key, and auto incremented */
    //def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (infra, name, description, isprocessed, tmin, tmax) // <> ((InfrastructureSummary.apply _).tupled, InfrastructureSummary.unapply)  }
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


  private class GatesTable(tag: Tag, schema: Option[String]) extends Table[(Double, Double, Double, Double)](tag, schema, "gates") {


    /** The origin zone column */
    def x1 = column[Double]("x1")

    /** The destinatoion zone column */
    def y1 = column[Double]("y1")

    /** The entry time column */
    def x2 = column[Double]("x2")

    /** The exit time column */
    def y2 = column[Double]("y2")

    /** The ID column, which is the primary key, and auto incremented */
    //def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (x1, y1, x2, y2)
  }

  private class MonitoredAreasTable(tag: Tag, schema: Option[String]) extends Table[MonitoredArea](tag, schema, "monitoredareas") {


    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")


    /** The origin zone column */
    def x1 = column[Double]("x1")

    /** The destinatoion zone column */
    def y1 = column[Double]("y1")

    /** The entry time column */
    def x2 = column[Double]("x2")

    /** The exit time column */
    def y2 = column[Double]("y2")

    /** The origin zone column */
    def x3 = column[Double]("x3")

    /** The destinatoion zone column */
    def y3 = column[Double]("y3")

    /** The entry time column */
    def x4 = column[Double]("x4")

    /** The exit time column */
    def y4 = column[Double]("y4")


    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (name, x1, y1, x2, y2, x3, y3, x4, y4, id) <> ((MonitoredArea.apply _).tupled, MonitoredArea.unapply)
  }

  /**
    * Here we define the summary table.
    */
  private class ZonesTable(tag: Tag, schema: Option[String]) extends Table[ZoneData](tag, schema, "odzones") {


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
  private class PersonSummaryTable(tag: Tag, schema: Option[String], name: String) extends Table[PersonSummaryData](tag, schema, name) {

    /** The ID column, which is the primary key, and auto incremented */
    def id = column[String]("id", O.PrimaryKey)

    /** The origin zone column */
    def origin = column[String]("origin")

    /** The destinatoion zone column */
    def destination = column[String]("destination")

    /** The entry time column */
    def entryTime = column[Double]("entry_time")

    /** The exit time column */
    def exitTime = column[Double]("exit_time")

    def tt = column[Double]("tt")

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (id, origin, destination, entryTime, exitTime, tt) <> ((PersonSummaryData.apply _).tupled, PersonSummaryData.unapply)
  }


  private class TrajRowTable(tag: Tag, schema: Option[String], name: String) extends Table[TrajRowData](tag, schema, name) {


    /** The origin zone column */
    def id = column[String]("id")

    /** The destinatoion zone column */
    def time = column[Double]("time")

    /** The entry time column */
    def x = column[Double]("x")

    /** The exit time column */
    def y = column[Double]("y")

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (id, time, x, y) <> ((TrajRowData.apply _).tupled, TrajRowData.unapply)
  }

  private class TrajByIDTable(tag: Tag, schema: Option[String], name: String) extends Table[TrajDataByID](tag, schema, name) {


    /** The origin zone column */
    def id = column[String]("id")

    /** The destinatoion zone column */
    def time = column[List[Double]]("time")

    /** The entry time column */
    def x = column[List[Double]]("x")

    /** The exit time column */
    def y = column[List[Double]]("y")

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (id, time, x, y) <> ((TrajDataByID.apply _).tupled, TrajDataByID.unapply)
  }


  private class TrajByTimeTable(tag: Tag, schema: Option[String], name: String) extends Table[TrajDataByTime](tag, schema, name) {


    /** The origin zone column */
    def time = column[Double]("time")

    /** The destinatoion zone column */
    def id = column[List[String]]("id")

    /** The entry time column */
    def x = column[List[Double]]("x")

    /** The exit time column */
    def y = column[List[Double]]("y")

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (time, id, x, y) <> ((TrajDataByTime.apply _).tupled, TrajDataByTime.unapply)
  }

  private class TrajByTimeTEMPTable(tag: Tag, schema: Option[String], name: String) extends Table[(String, Double, Double, Double)](tag, schema, name) {

    /** The destinatoion zone column */
    def id = column[String]("id")

    /** The origin zone column */
    def time = column[Double]("time")

    /** The entry time column */
    def x = column[Double]("x")

    /** The exit time column */
    def y = column[Double]("y")

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (id, time, x, y) // <> ((TrajByTimeTEMPTable.apply _).tupled, TrajByTimeTEMPTable.unapply)
  }

  //import dbConfig.profile.api._

  /**
    * The starting point for all queries on the Summary table.
    */
  private val generalSummary = TableQuery[GeneralSummaryTable]
  private val trajectorySummary = TableQuery[TrajectorySummaryTable]

  private def pedestrianSummaryTable(schema: String, name: String) = new TableQuery(new PersonSummaryTable(_, Some(schema), name))

  private def wallTable(schemaName: String) = new TableQuery(new WallTable(_, Some(schemaName)))

  private def ODZonesTable(schema: String) = new TableQuery(new ZonesTable(_, Some(schema)))

  private def gatesTable(schema: String) = new TableQuery(new GatesTable(_, Some(schema)))

  private def monitoredAreasTable(schema: String) = new TableQuery(new MonitoredAreasTable(_, Some(schema)))

  private def trajectoryTable(schema: String, name: String): TableQuery[TrajRowTable] = new TableQuery(new TrajRowTable(_, Some(schema), name))

  private def trajectoryByIDTable(schema: String, name: String): TableQuery[TrajByIDTable] = new TableQuery(new TrajByIDTable(_, Some(schema), name))

  private def trajectoryByTimeTable(schema: String, name: String): TableQuery[TrajByTimeTable] = new TableQuery(new TrajByTimeTable(_, Some(schema), name))

  private def trajectoryByTimeTEMPTable(schema: String, name: String): TableQuery[TrajByTimeTEMPTable] = new TableQuery(new TrajByTimeTEMPTable(_, Some(schema), name))


  def getPedListSummary(schema: String, name: String)(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
    pedestrianSummaryTable(schema, name + "_summary").result
  }

  def getPedSummary(schema: String, name: String, id: String)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]] = db.run {
    pedestrianSummaryTable(schema, name + "_summary").filter(r => r.id === id).result.headOption
  }

  def getPedListSummary(schema: String, name: String, ids: Vector[String])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
    (for {r <- pedestrianSummaryTable(schema, name + "_summary") if r.id inSetBind ids} yield r).result
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
    val xmin: Double = walls.flatMap(w => Vector(w._1, w._3)).min
    val xmax: Double = walls.flatMap(w => Vector(w._1, w._3)).max
    val ymin: Double = walls.flatMap(w => Vector(w._2, w._4)).min
    val ymax: Double = walls.flatMap(w => Vector(w._2, w._4)).max

    Logger.warn(s"Table ${infra}.walls needs to be created. Inserting " + walls.size + " walls.")

    db.run(DBIO.seq(
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.walls (id SERIAL PRIMARY  KEY, x1 DOUBLE PRECISION , y1 DOUBLE PRECISION , x2 DOUBLE PRECISION , y2 DOUBLE PRECISION , wtype INTEGER)""",
      wallTable(infra) ++= walls.map(w => WallData(w._1, w._2, w._3, w._4, w._5)),
      /*(for { v <- generalSummary if v.name === infra } yield v.xmin).update(xmin),
      (for { v <- generalSummary if v.name === infra } yield v.xmax).update(xmax),
      (for { v <- generalSummary if v.name === infra } yield v.ymin).update(ymin),
      (for { v <- generalSummary if v.name === infra } yield v.ymax).update(ymax),*/
      sqlu""" UPDATE general_summary SET x_min=#${xmin}, x_max=#${xmax}, y_min=#${ymin}, y_max=#${ymax} WHERE name='#${infra}' """
    )
    )
  }

  implicit val getWallDataResult = GetResult(r => WallData(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextInt()))


  def getWalls(infra: String)(implicit mc: MarkerContext): Future[Iterable[WallData]] = {
    db.run {
      wallTable(infra).result
    }
  }

  implicit val getZoneDataResult = GetResult(r => ZoneData(r.nextString(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextBoolean(), r.nextInt()))

  def getZones(infra: String)(implicit mc: MarkerContext): Future[Iterable[ZoneData]] = {
    db.run {
      ODZonesTable(infra).filter(_.isod).result
    }
  }

  def getGates(infra: String)(implicit mc: MarkerContext): Future[Iterable[(Double, Double, Double, Double)]] = {
    db.run {
      gatesTable(infra).result
    }
  }

  def getMonitoredArea(infra: String)(implicit mc: MarkerContext): Future[Iterable[MonitoredArea]] = {
    db.run {
      monitoredAreasTable(infra).result
    }
  }


  def createODZonesTable(infra: String, zones: Iterable[(String, Double, Double, Double, Double, Double, Double, Double, Double, Boolean)])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.graph needs to be created:")
    db.run {
      DBIO.seq(
        sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.odzones (name VARCHAR(50), ax DOUBLE PRECISION , ay DOUBLE PRECISION , bx DOUBLE PRECISION, by DOUBLE PRECISION, cx DOUBLE PRECISION , cy DOUBLE PRECISION , dx DOUBLE PRECISION , dy DOUBLE PRECISION , isod BOOLEAN, id SERIAL PRIMARY KEY)""",
        ODZonesTable(infra) ++= zones.map(od => ZoneData(od._1, od._2, od._3, od._4, od._5, od._6, od._7, od._8, od._9, od._10))
      )
    }
  }

  def createGatesTable(infra: String, zones: Iterable[(Double, Double, Double, Double)])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.gates needs to be created:")
    db.run {
      DBIO.seq(
        sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.gates (id SERIAL PRIMARY KEY, x1 DOUBLE PRECISION , y1 DOUBLE PRECISION , x2 DOUBLE PRECISION, y2 DOUBLE PRECISION)""",
        gatesTable(infra) ++= zones
      )
    }
  }

  def createMonitoredAreasTable(infra: String, monitoredAreas: Iterable[MonitoredArea])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.gates needs to be created:")
    db.run {
      DBIO.seq(
        sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.monitoredareas (name VARCHAR(100), x1 DOUBLE PRECISION , y1 DOUBLE PRECISION , x2 DOUBLE PRECISION, y2 DOUBLE PRECISION, x3 DOUBLE PRECISION , y3 DOUBLE PRECISION , x4 DOUBLE PRECISION, y4 DOUBLE PRECISION, id SERIAL PRIMARY KEY)""",
        monitoredAreasTable(infra) ++= monitoredAreas
      )
    }
  }

  def dropInfra(infra: String): Future[Unit] = {
    //Logger.warn(s"Dropping infrastructure (schema) $infra !")
    db.run {
      DBIO.seq(
        trajectorySummary.filter(_.infra === infra).delete,
        generalSummary.filter(_.name === infra).delete,
        sqlu""" DROP SCHEMA #${infra} CASCADE """
      )
    }
  }

  def dropTraj(infra: String, traj: String): Future[Unit] = {
    //Logger.warn(s"Dropping infrastructure (schema) $infra !")
    db.run {
      DBIO.seq(
        trajectorySummary.filter(t => t.infra === infra && t.name === traj).delete,
        sqlu""" DROP TABLE #${infra}.#${traj}_trajectories""",
        sqlu""" DROP TABLE #${infra}.#${traj}_summary""",
        sqlu""" DROP TABLE #${infra}.#${traj}_trajectories_id""",
        sqlu""" DROP TABLE #${infra}.#${traj}_trajectories_time"""
      )
    }
  }


  def createTrajTables(infra: String, traj: String): Future[Unit] = {
    db.run(DBIO.seq(
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories (id VARCHAR(100), time DOUBLE PRECISION , x DOUBLE PRECISION , y DOUBLE PRECISION)""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_summary (id VARCHAR(100), origin VARCHAR(50), destination VARCHAR(50), entry_time DOUBLE PRECISION , exit_time DOUBLE PRECISION, tt DOUBLE PRECISION )""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories_id (id VARCHAR(100), time DOUBLE PRECISION [], x DOUBLE PRECISION [], y DOUBLE PRECISION [])""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories_time_temp (id VARCHAR(100), time DOUBLE PRECISION, x DOUBLE PRECISION, y DOUBLE PRECISION)""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories_time (time DOUBLE PRECISION, id VARCHAR(100)[], x DOUBLE PRECISION[], y DOUBLE PRECISION[])"""
    ))
  }


  def insertRowIntoTrajTable(schema: String, name: String, row: TrajRowData): Future[Unit] = {
    //if (row.id == "gpwR0AIW" && row.time < 27123.0) {println(row)}
    //println(s"INSERT INTO ${schema}.${name}_trajectories VALUES (\'${row.id}\', ${row.time}, ${row.x}, ${row.y})")
    db.run {
      DBIO.seq(
        sqlu"""INSERT INTO #${schema}.#${name}_trajectories VALUES ('#${row.id}', #${row.time}, #${row.x}, #${row.y})"""
        //trajectoryTable(schema, name + "_trajectories") += row
      )
    }
  }

  def insertTrajTableFile(schema: String, name: String, file: String): Future[Unit] = {
    //if (row.id == "gpwR0AIW" && row.time < 27123.0) {println(row)}
    println(s"INSERT INTO ${schema}.${name}_trajectories FROM '${file}' WITH (FORMAT csv)")
    db.run {
      DBIO.seq(
        sqlu"""COPY #${schema}.#${name}_trajectories FROM '#${file}' WITH (FORMAT csv)"""
        //trajectoryTable(schema, name + "_trajectories") += row
      )
    }
  }

  def insertRowIntoPedSummaryTable(schema: String, name: String, data: PersonSummaryData): Future[Unit] = {
    //Logger.warn(data.toString)
    //Logger.warn("schema= " + schema + ", traj= " + name)
    db.run {
      DBIO.seq(
        pedestrianSummaryTable(schema, name + "_summary") += data
      )
    }
  }

  def getListTrajectories(infra: String): Future[Iterable[(String, Double, Double)]] = {
    Logger.warn("getting traj list for: " + infra)
    db.run {
      (for {r <- trajectorySummary if r.infra === infra && r.isprocessed} yield (r.name, r.tmin, r.tmax)).result
    }
  }

  def insertIntoTrajectoriesList(infra: String, name: String, descr: String): Future[Unit] = db.run {
    DBIO.seq(
      trajectorySummary += ((infra, name, descr, false, 0.0, 0.0))
    )
  }

  def updateTrajectoryProcessingState(infra: String, name: String, tf: Boolean): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""UPDATE trajectory_summary SET isprocessed=#${tf} where (name='#${name}' AND infra='#${infra}')"""
      )
    }
  }

  def setTrajectoryTimeBounds(infra: String, name: String, tmin: Double, tmax: Double): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""UPDATE trajectory_summary SET t_min=#${tmin}, t_max=#${tmax} where (name='#${name}' AND infra='#${infra}')"""
      )
    }
  }

  def groupTrajectoriesByID(infra: String, traj: String): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""INSERT INTO #${infra}.#${traj}_trajectories_id (id, time, x, y) SELECT id AS id, array_agg(time ORDER BY time) AS time, array_agg(x ORDER BY time) AS x, array_agg(y ORDER BY time) AS y FROM #${infra}.#${traj}_trajectories GROUP BY id"""
      )
    }
  }

  def groupTrajectoriesByTime(infra: String, traj: String): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""INSERT INTO #${infra}.#${traj}_trajectories_time (time, id, x, y) SELECT time AS time, array_agg(id ORDER BY id) AS time, array_agg(x ORDER BY id) AS x, array_agg(y ORDER BY id) AS y FROM #${infra}.#${traj}_trajectories_time_temp GROUP BY time""",
        sqlu"""DROP TABLE #${infra}.#${traj}_trajectories_time_temp"""
      )
    }
  }


  def insertRowIntoTempTrajTimeTable(infra: String, traj: String, rows: Iterable[(String, Double, Double, Double)]): Future[Unit] = {
    //Logger.warn(rows.mkString("\n"))
    db.run {
      DBIO.seq {
        trajectoryByTimeTEMPTable(infra, traj + "_trajectories_time_temp") ++= rows
      }
    }
    //DBIO.seq(
    //  sqlu"""INSERT INTO #${infra}.#${traj}_trajectories_time_temp VALUES (#${row._1}, #${row._2}, #${row._3}, #${row._4})"""
    //)
    //}
  }

  //implicit val getTrajDataByIDResult = GetResult(r => WallData(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextInt()))

  def getTrajectoriesByID(infra: String, traj: String, ids: Option[Iterable[String]]): Future[Iterable[TrajDataByID]] = {
    if (ids.isDefined) {
      db.run {
        (for {r <- trajectoryByIDTable(infra, traj + "_trajectories_id") if r.id inSetBind ids.get} yield r).result
      }
    } else {
      db.run {
        trajectoryByIDTable(infra, traj + "_trajectories_id").result
      }
    }
  }

  def getPedIDs(infra: String, traj: String): Future[Iterable[String]] = {
    db.run {
      (for (r <- trajectoryByIDTable(infra, traj + "_trajectories_id")) yield {
        r.id
      }).result
    }
  }


  def getTrajectories(infra: String, traj: String): Future[Iterable[TrajRowData]] = {
    db.run {
      trajectoryTable(infra, traj + "_trajectories").result
    }
  }

  def getTrajectoriesByTime(infra: String, traj: String, lb: Option[Double] = None, ub: Option[Double] = None): Future[Iterable[TrajDataByTime]] = {
    if (lb.isDefined && ub.isEmpty) {
      db.run {
        trajectoryByTimeTable(infra, traj + "_trajectories_time").filter(t => t.time >= lb.get).result
      }
    } else if (lb.isEmpty && ub.isDefined) {
      db.run {
        trajectoryByTimeTable(infra, traj + "_trajectories_time").filter(t => t.time <= ub.get).result
      }
    } else if (lb.isDefined && ub.isDefined) {
      db.run {
        trajectoryByTimeTable(infra, traj + "_trajectories_time").filter(t => t.time >= lb.get && t.time <= ub.get).result
      }
    } else {
      db.run {
        trajectoryByTimeTable(infra, traj + "_trajectories_time").result
      }
    }
  }
}



