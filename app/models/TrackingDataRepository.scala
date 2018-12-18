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
  *
  *
  * This is the container for the various methods which communicate with the DB.
  */
trait TrackingDataRepository {

  // List the summary per pedestrian which include their OD and TT.
  def getPedListSummary(schema: String, name: String)(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

  // Pedestrian summary list with a filter on the ID. Only pedestrians listed in the ids are returned.
  def getPedListSummary(schema: String, name: String, ids: Vector[String])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

  // Get the summary for a specific pedestrian
  def getPedSummary(schema: String, name: String, id: String)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]]

  // List all the available infrastructures
  def getInfrastructures(implicit mc: MarkerContext): Future[Iterable[InfrastructureSummary]]

  // Creates the specific infrastructure and the basic tables in the DB
  def createInfraIfNotExisting(infra: String, description: String)(implicit mc: MarkerContext): Future[Unit]

  // Create the collection of walls for a specific infrastructure
  def createWallsTable(infra: String, walls: Iterable[(Double, Double, Double, Double, Int)])(implicit mc: MarkerContext): Future[Unit]

  // Return the collection of walls for a specific infrastrcuture
  def getWalls(infra: String)(implicit mc: MarkerContext): Future[Iterable[WallData]]

  // Creates the table for storing the set of origin and destination zones.
  def createODZonesTable(infra: String, zones: Iterable[(String, Double, Double, Double, Double, Double, Double, Double, Double, Boolean)])(implicit mc: MarkerContext): Future[Unit]

  // returns the set of origin and destination zones
  def getZones(infra: String)(implicit mc: MarkerContext): Future[Iterable[ZoneData]]

  // insert of row of tracking data into the DB
  @deprecated
  def insertRowIntoTrajTable(schema: String, name: String, row: TrajRowData): Future[Unit]

  // insert the file using PSQL to avoid weird behaviour where some lines are being skipped.
  def insertTrajTableFile(schema: String, name: String, file: String): Future[Unit]

  // get the trajectories sorted by time
  def getTrajectories(infra: String, traj: String): Future[Iterable[TrajRowData]]

  // inserts a new row for a specific pedestrian into the table containing
  def insertRowIntoPedSummaryTable(schema: String, name: String, data: PersonSummaryData): Future[Unit]

  // Create the set of tables for a specific set of trajectory data
  def createTrajTables(schema: String, name: String): Future[Unit]

  // List the trajectories for a specific infra
  def getListTrajectories(infra: String): Future[Iterable[(String, Double, Double)]]

  // insert a trajectory summary into the list of traj summaries
  def insertIntoTrajectoriesList(infra: String, name: String, descr: String): Future[Unit]

  // change the processing state. This is requires as the processing takes time and this avoids showing unfinished data on the front end
  def updateTrajectoryProcessingState(infra: String, name: String, tf: Boolean): Future[Unit]

  // Set the time bounds for a specific trajectory
  def setTrajectoryTimeBounds(infra: String, name: String, tmin: Double, tmax: Double): Future[Unit]

  // delete an infrastructure and all the associated trajectories
  def dropInfra(infra: String): Future[Unit]

  // delete a specific set of trajectory data
  def dropTraj(infra: String, traj: String): Future[Unit]

  // get the collection of flow gates for a specific infra
  def getGates(infra: String)(implicit mc: MarkerContext): Future[Iterable[(Double, Double, Double, Double)]]

  // get the collection of monitored areas (controlled areas) where the density is measured
  def getMonitoredArea(infra: String)(implicit mc: MarkerContext): Future[Iterable[MonitoredArea]]

  // create the table for storing the gates of an infra
  def createGatesTable(infra: String, zones: Iterable[(Double, Double, Double, Double)])(implicit mc: MarkerContext): Future[Unit]

  // create the table for storing the monitored areas of an infra
  def createMonitoredAreasTable(infra: String, zones: Iterable[MonitoredArea])(implicit mc: MarkerContext): Future[Unit]

  // groups the trajectories by ID for intermediate processing
  def groupTrajectoriesByID(infra: String, traj: String): Future[Unit]

  // return the trajectories sorted by pedestrian ID
  def getTrajectoriesByID(infra: String, traj: String, ids: Option[Iterable[String]]): Future[Iterable[TrajDataByID]]

  // get the list of pedestrian IDs for a specific set of traj data
  def getPedIDs(infra: String, traj: String): Future[Iterable[String]]

  // temporary processing used for interpoalting the trajectories to regular intervals
  def insertRowIntoTempTrajTimeTable(infra: String, traj: String, rows: Iterable[(String, Double, Double, Double)]): Future[Unit]

  // groups the trajectories by regular times after intermediate processing
  def groupTrajectoriesByTime(infra: String, traj: String): Future[Unit]

  // returns the traj data at regular intervals
  def getTrajectoriesByTime(infra: String, traj: String, lb: Option[Double] = None, ub: Option[Double] = None): Future[Iterable[TrajDataByTime]]
}

/**
  * Used for extending slick with more convenient features
  */
trait HasDBConfig {
  self: HasDatabaseConfig[MyPGProfile] =>

  import profile.api._

}

/**
  * class used for implicit injection into DB connection
  *
  * @param actorSystem
  */
class TrajectoryDBExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "repository.dispatcher")

/**
  * Class defining the interface with the DB which stores the data. This should be cut into smaller chunks ideally.
  *
  * The first part contains defintions of the various tables, while the second part containes the implementations of the
  * abstract functions defined in [[TrackingDataRepository]].
  *
  *
  * A custom execution context is used here to establish that blocking operations should be
  * executed in a different thread than Play's ExecutionContext, which is used for CPU bound tasks
  * such as rendering.
  */
@Singleton
class TrackingDataRepositoryImpl @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: TrajectoryDBExecutionContext) extends TrackingDataRepository with HasDBConfig with HasDatabaseConfigProvider[MyPGProfile] {

  import profile.api._

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////// DEFINITION OF THE DB TABLES //////////////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
    * Table definition for the infrastructure summary.
    *
    * This table must be created manually for a new DB
    * create table general_summary(id SERIAL PRIMARY KEY, name varchar(50), description varchar(1000));
    *
    * @param tag unclear parameter https://stackoverflow.com/questions/20599438/slick-2-0-0-m3-table-definition-clarification-on-the-tag-attribute
    */
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

  /**
    * Definition of the table for storing the summary of trajectory data sets.
    *
    * This table must be created by hand in new DB
    *
    * CREATE  TABLE trajectory_summary (infra VARCHAR(100), name VARCHAR(100), description VARCHAR(1000), isprocessed BOOLEAN, t_min DOUBLE PRECISION, t_max DOUBLE PRECISION);
    *
    *
    * @param tag
    */
  private class TrajectorySummaryTable(tag: Tag) extends Table[(String, String, String, Boolean, Double, Double)](tag, "trajectory_summary") {


    /** The destinatoion zone column */
    def infra = column[String]("infra")

    /** The entry time column */
    def name = column[String]("name")

    def description = column[String]("description")

    // indication whether the processing has finished or not.
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
    * Definition of the table for storing the walls (infrastructure). There is one of these tables per infra set
    * @param tag
    * @param schema infra name of the corresponding table
    */
  private class WallTable(tag: Tag, schema: Option[String]) extends Table[WallData](tag, schema, "walls") {


    def x1 = column[Double]("x1")

    def y1 = column[Double]("y1")

    def x2 = column[Double]("x2")

    def y2 = column[Double]("y2")

    // type of wall
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
    * Gates table corresponding to a specific infrastructure
    * @param tag
    * @param schema name of the infra
    */
  private class GatesTable(tag: Tag, schema: Option[String]) extends Table[(Double, Double, Double, Double)](tag, schema, "gates") {


    // first x coord
    def x1 = column[Double]("x1")

    //first y coord
    def y1 = column[Double]("y1")

    // second x coord
    def x2 = column[Double]("x2")

    // second y coord
    def y2 = column[Double]("y2")

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

  /**
    * Tables containing the controlled areas where the density will be computed
    * @param tag
    * @param schema name of the infra
    */
  private class MonitoredAreasTable(tag: Tag, schema: Option[String]) extends Table[MonitoredArea](tag, schema, "monitoredareas") {


    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")


    def x1 = column[Double]("x1")

    def y1 = column[Double]("y1")

    def x2 = column[Double]("x2")

    def y2 = column[Double]("y2")

    def x3 = column[Double]("x3")

    def y3 = column[Double]("y3")

    def x4 = column[Double]("x4")

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
    * Tables storing the OD zones for each infra
    * @param tag
    * @param schema name of the infra
    */
  private class ZonesTable(tag: Tag, schema: Option[String]) extends Table[ZoneData](tag, schema, "odzones") {


    def name = column[String]("name")

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
    * Defintion of the table containing the summary of the trip for each pedestrian. One row per pedestrian
    *
    * @param tag
    * @param infra name of the infra
    * @param traj name of the traj data
    */
  private class PersonSummaryTable(tag: Tag, infra: Option[String], traj: String) extends Table[PersonSummaryData](tag, infra, traj) {

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

    /** Total travel time */
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


  /**
    * One row per line in the original csv data.
    *
    * @param tag
    * @param infra infra name
    * @param traj traj name
    */
  private class TrajRowTable(tag: Tag, infra: Option[String], traj: String) extends Table[TrajRowData](tag, infra, traj) {

    def id = column[String]("id")

    def time = column[Double]("time")

    def x = column[Double]("x")

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

  /**
    * Defintion of the table containing the trajectory grouped by pedestrian ID. This is usefull for accessing the
    * data by id without needing to do the grouping in the front end. Contains three ordered vectors for the time, x and y
    * coordinates.
    *
    * @param tag
    * @param infra
    * @param traj
    */
  private class TrajByIDTable(tag: Tag, infra: Option[String], traj: String) extends Table[TrajDataByID](tag, infra, traj) {


    def id = column[String]("id")

    def time = column[List[Double]]("time")

    def x = column[List[Double]]("x")

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


  /**
    * Data sorted by time. For each time, three vectors containing the pedestrian ID, x and y coordinates.
    * @param tag
    * @param schema
    * @param name
    */
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

  /**
    * Temporay table used for intermediate processing. data by time at regular times.
    *
    * @param tag
    * @param schema
    * @param name
    */
  private class TrajByTimeTEMPTable(tag: Tag, schema: Option[String], name: String) extends Table[(String, Double, Double, Double)](tag, schema, name) {

    def id = column[String]("id")

    def time = column[Double]("time")

    def x = column[Double]("x")

    def y = column[Double]("y")

    /**
      * This is the tables default "projection".
      *
      * It defines how the columns are converted to and from the Person object.
      *
      * In this case, we are simply passing the id, name and page parameters to the Person case classes
      * apply and unapply methods.
      */
    def * = (id, time, x, y)
  }


  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////// INTERFACE TO THE DB TABLES ///////////////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // general summary table
  private val generalSummary = TableQuery[GeneralSummaryTable]

  // trajectory summary table
  private val trajectorySummary = TableQuery[TrajectorySummaryTable]

  // pedestrian summary table: one per traj set
  private def pedestrianSummaryTable(schema: String, name: String) = new TableQuery(new PersonSummaryTable(_, Some(schema), name))

  // walls table: one per infra
  private def wallTable(schemaName: String) = new TableQuery(new WallTable(_, Some(schemaName)))

  // OD zones table: one per infra
  private def ODZonesTable(schema: String) = new TableQuery(new ZonesTable(_, Some(schema)))

  // Gates table: one per infra
  private def gatesTable(schema: String) = new TableQuery(new GatesTable(_, Some(schema)))

  // controlled areas: one per infra
  private def monitoredAreasTable(schema: String) = new TableQuery(new MonitoredAreasTable(_, Some(schema)))

  // raw trajectory data: one per traj set
  private def trajectoryTable(schema: String, name: String): TableQuery[TrajRowTable] = new TableQuery(new TrajRowTable(_, Some(schema), name))

  // trajectories grouped by ID: one per traj set
  private def trajectoryByIDTable(schema: String, name: String): TableQuery[TrajByIDTable] = new TableQuery(new TrajByIDTable(_, Some(schema), name))

  // trajectories grouped by time: one per traj set
  private def trajectoryByTimeTable(schema: String, name: String): TableQuery[TrajByTimeTable] = new TableQuery(new TrajByTimeTable(_, Some(schema), name))

  // temporary table used for storing intemediat eprocessing results. This table is deleted once the processing is finished.
  private def trajectoryByTimeTEMPTable(schema: String, name: String): TableQuery[TrajByTimeTEMPTable] = new TableQuery(new TrajByTimeTEMPTable(_, Some(schema), name))


  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////// ACTIONS ON THE DB ////////////////////////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
    * Implicit conversion from the [[WallData]] object to a collection of [[Double]] for the DB interface
    */
  implicit val getWallDataResult = GetResult(r => WallData(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextInt()))


  /**
    * Implicit conversion from the [[ZoneData]] object to a collection of [[Double]] and other primtive type for the DB interface
    */
  implicit val getZoneDataResult = GetResult(r => ZoneData(r.nextString(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextBoolean(), r.nextInt()))


  ////////////////////////////// CREATION AND INSERTION ///////////////////////////////////////////////////////

  /**
    * Creates a new infra in the DB after having checked that it does not already exist.
    *
    * @param infra infra name
    * @param descr description of the infrastructure
    * @param mc
    * @return
    */
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
        Logger.warn(s"Schema $infra created:")
        db.run(DBIO.seq(
          sqlu"""CREATE SCHEMA #$infra""",
          generalSummary += InfrastructureSummary(infra, descr))
        )
      }
    }
  }

  /**
    * add a table for the walls and then populates it. This action also updates the general summary with the bounds
    * coming from the walls.
    * @param infra
    * @param walls
    * @param mc
    * @return
    */
  def createWallsTable(infra: String, walls: Iterable[(Double, Double, Double, Double, Int)])(implicit mc: MarkerContext) = {

    // computes the bounds from the wall data
    val xmin: Double = walls.flatMap(w => Vector(w._1, w._3)).min
    val xmax: Double = walls.flatMap(w => Vector(w._1, w._3)).max
    val ymin: Double = walls.flatMap(w => Vector(w._2, w._4)).min
    val ymax: Double = walls.flatMap(w => Vector(w._2, w._4)).max

    Logger.warn(s"Table ${infra}.walls is created. Inserting " + walls.size + " walls.")

    db.run(DBIO.seq(
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.walls (id SERIAL PRIMARY  KEY, x1 DOUBLE PRECISION , y1 DOUBLE PRECISION , x2 DOUBLE PRECISION , y2 DOUBLE PRECISION , wtype INTEGER)""",
      wallTable(infra) ++= walls.map(w => WallData(w._1, w._2, w._3, w._4, w._5)),
      sqlu""" UPDATE general_summary SET x_min=#${xmin}, x_max=#${xmax}, y_min=#${ymin}, y_max=#${ymax} WHERE name='#${infra}' """
    )
    )
  }


  /**
    * Creates the table for the OD zones and inserts the data directly into it.
    * @param infra
    * @param zones
    * @param mc
    * @return
    */
  def createODZonesTable(infra: String, zones: Iterable[(String, Double, Double, Double, Double, Double, Double, Double, Double, Boolean)])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.graph is created and populated.")
    db.run {
      DBIO.seq(
        sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.odzones (name VARCHAR(50), ax DOUBLE PRECISION , ay DOUBLE PRECISION , bx DOUBLE PRECISION, by DOUBLE PRECISION, cx DOUBLE PRECISION , cy DOUBLE PRECISION , dx DOUBLE PRECISION , dy DOUBLE PRECISION , isod BOOLEAN, id SERIAL PRIMARY KEY)""",
        ODZonesTable(infra) ++= zones.map(od => ZoneData(od._1, od._2, od._3, od._4, od._5, od._6, od._7, od._8, od._9, od._10))
      )
    }
  }

  /**
    * Creates the table for storing the collection of gates
    *
    * @param infra
    * @param zones
    * @param mc
    * @return
    */
  def createGatesTable(infra: String, zones: Iterable[(Double, Double, Double, Double)])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.gates needs to be created:")
    db.run {
      DBIO.seq(
        sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.gates (id SERIAL PRIMARY KEY, x1 DOUBLE PRECISION , y1 DOUBLE PRECISION , x2 DOUBLE PRECISION, y2 DOUBLE PRECISION)""",
        gatesTable(infra) ++= zones
      )
    }
  }

  /**
    * Creates the table and inserts the monitored areas into the DB.
    *
    * @param infra
    * @param monitoredAreas
    * @param mc
    * @return
    */
  def createMonitoredAreasTable(infra: String, monitoredAreas: Iterable[MonitoredArea])(implicit mc: MarkerContext) = {
    Logger.warn(s"Table ${infra}.gates needs to be created:")
    db.run {
      DBIO.seq(
        sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.monitoredareas (name VARCHAR(100), x1 DOUBLE PRECISION , y1 DOUBLE PRECISION , x2 DOUBLE PRECISION, y2 DOUBLE PRECISION, x3 DOUBLE PRECISION , y3 DOUBLE PRECISION , x4 DOUBLE PRECISION, y4 DOUBLE PRECISION, id SERIAL PRIMARY KEY)""",
        monitoredAreasTable(infra) ++= monitoredAreas
      )
    }
  }


  /**
    * Insert a row into the pedestrian summary table.
    *
    * @param schema
    * @param name
    * @param data
    * @return
    */
  def insertRowIntoPedSummaryTable(schema: String, name: String, data: PersonSummaryData): Future[Unit] = {
    //Logger.warn(data.toString)
    //Logger.warn("schema= " + schema + ", traj= " + name)
    db.run {
      DBIO.seq(
        pedestrianSummaryTable(schema, name + "_summary") += data
      )
    }
  }

  ///////////////////////////////////////////// GETTERS ///////////////////////////////////////////////////////

  /**
    * Get the full pedestrian summary table.
    * @param schema infra name
    * @param name traj name
    * @param mc
    * @return Collection of [[PersonSummaryData]]
    */
  def getPedListSummary(schema: String, name: String)(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
    pedestrianSummaryTable(schema, name + "_summary").result
  }

  /**
    * Get the full pedestrian summary table.
    * @param schema infra name
    * @param name traj name
    * @param id pedestrian ID to get the summary for
    * @param mc
    * @return  [[PersonSummaryData]] for the requested pedestrian
    */
  def getPedSummary(schema: String, name: String, id: String)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]] = db.run {
    pedestrianSummaryTable(schema, name + "_summary").filter(r => r.id === id).result.headOption
  }

  /**
    * Get the full pedestrian summary table.
    * @param schema infra name
    * @param name traj name
    * @param ids list of pedestrians to ge the summary for
    * @param mc
    * @return Collection of [[PersonSummaryData]]
    */
  def getPedListSummary(schema: String, name: String, ids: Vector[String])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
    (for {r <- pedestrianSummaryTable(schema, name + "_summary") if r.id inSetBind ids} yield r).result
  }

  /**
    * Get te list of all the available infrastructures with their descriptions
    * @param mc
    * @return
    */
  def getInfrastructures(implicit mc: MarkerContext): Future[Iterable[InfrastructureSummary]] = db.run {
    generalSummary.result
  }

  /**
    * Returns the list of processed trajectory states.
    * @param infra
    * @return
    */
  def getListTrajectories(infra: String): Future[Iterable[(String, Double, Double)]] = {
    Logger.warn("getting traj list for: " + infra)
    db.run {
      (for {r <- trajectorySummary if r.infra === infra && r.isprocessed} yield (r.name, r.tmin, r.tmax)).result
    }
  }


  /**
    * Get all the walls for a specific infra
    * @param infra
    * @param mc
    * @return
    */
  def getWalls(infra: String)(implicit mc: MarkerContext): Future[Iterable[WallData]] = {
    db.run {
      wallTable(infra).result
    }
  }


  /**
    *
    * @param infra
    * @param mc
    * @return
    */
  def getZones(infra: String)(implicit mc: MarkerContext): Future[Iterable[ZoneData]] = {
    db.run {
      ODZonesTable(infra).filter(_.isod).result
    }
  }

  /**
    * Get the collection of Gates for a specific infra
    * @param infra
    * @param mc
    * @return
    */
  def getGates(infra: String)(implicit mc: MarkerContext): Future[Iterable[(Double, Double, Double, Double)]] = {
    db.run {
      gatesTable(infra).result
    }
  }

  /**
    * get the collection of monitored areas for a specific infra
    * @param infra
    * @param mc
    * @return
    */
  def getMonitoredArea(infra: String)(implicit mc: MarkerContext): Future[Iterable[MonitoredArea]] = {
    db.run {
      monitoredAreasTable(infra).result
    }
  }

  /**
    * Get the full trajectory sorted by time, possibly with bounds. All combinations are possible for the
    * usage of the bounds. Either no bounds, only upper, only upper or upper and lower can be specified.
    *
    * @param infra infra name
    * @param traj traj name
    * @param lb lower bound on time
    * @param ub upper bound on time
    * @return
    */
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

  /**
    * Returns the set of trajectores for a given infra.
    * @param infra
    * @param traj
    * @return
    */
  @deprecated
  def getTrajectories(infra: String, traj: String): Future[Iterable[TrajRowData]] = {
    db.run {
      trajectoryTable(infra, traj + "_trajectories").result
    }
  }

  /**
    * Gets the set of pedestrian IDs for a specific trajectory data set.
    *
    * @param infra
    * @param traj
    * @return
    */
  def getPedIDs(infra: String, traj: String): Future[Iterable[String]] = {
    db.run {
      (for (r <- trajectoryByIDTable(infra, traj + "_trajectories_id")) yield {
        r.id
      }).result
    }
  }


  /**
    * Get the trajectories grouped by pedestrian ID.
    *
    * @param infra
    * @param traj
    * @param ids
    * @return
    */
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


  ///////////////////////////////////////////// DELETION //////////////////////////////////////////////////////

  /**
    *  DANGEROUS ! deletes and infrastructure and all the associated trajectories
    *
    * @param infra
    * @return
    */
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

  /**
    * Deletes a specific set of trajectory data
    *
    * @param infra
    * @param traj
    * @return
    */
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

  ////////////////////////////////////////// DATA PROCESSING //////////////////////////////////////////////////


  /**
    * Creates the empty tables for starting the data processing
    *
    * @param infra
    * @param traj
    * @return
    */
  def createTrajTables(infra: String, traj: String): Future[Unit] = {
    db.run(DBIO.seq(
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories (id VARCHAR(100), time DOUBLE PRECISION , x DOUBLE PRECISION , y DOUBLE PRECISION)""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_summary (id VARCHAR(100), origin VARCHAR(50), destination VARCHAR(50), entry_time DOUBLE PRECISION , exit_time DOUBLE PRECISION, tt DOUBLE PRECISION )""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories_id (id VARCHAR(100), time DOUBLE PRECISION [], x DOUBLE PRECISION [], y DOUBLE PRECISION [])""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories_time_temp (id VARCHAR(100), time DOUBLE PRECISION, x DOUBLE PRECISION, y DOUBLE PRECISION)""",
      sqlu"""CREATE TABLE IF NOT EXISTS #${infra}.#${traj}_trajectories_time (time DOUBLE PRECISION, id VARCHAR(100)[], x DOUBLE PRECISION[], y DOUBLE PRECISION[])"""
    ))
  }

  /**
    * Inserts the raw data into the table one row at a time. Not used anymore because if was skipping
    * lines for some reason.
    * @param schema
    * @param name
    * @param row
    * @return
    */
  @deprecated
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

  /**
    * Insert a full CSV file into the raw trajectory table at once using postgres.
    *
    * @param schema
    * @param name
    * @param file
    * @return
    */
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


  /**
    * Inserts a trajectory summary into the table
    *
    * @param infra
    * @param name
    * @param descr
    * @return
    */
  def insertIntoTrajectoriesList(infra: String, name: String, descr: String): Future[Unit] = db.run {
    DBIO.seq(
      trajectorySummary += ((infra, name, descr, false, 0.0, 0.0))
    )
  }

  /**
    * Change the state of the trajectory processing. This is needed as the processing takes time.
    * @param infra
    * @param name
    * @param tf
    * @return
    */
  def updateTrajectoryProcessingState(infra: String, name: String, tf: Boolean): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""UPDATE trajectory_summary SET isprocessed=#${tf} where (name='#${name}' AND infra='#${infra}')"""
      )
    }
  }

  /**
    * Update the time bounds for the specified set of traj data.
    * @param infra
    * @param name
    * @param tmin
    * @param tmax
    * @return
    */
  def setTrajectoryTimeBounds(infra: String, name: String, tmin: Double, tmax: Double): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""UPDATE trajectory_summary SET t_min=#${tmin}, t_max=#${tmax} where (name='#${name}' AND infra='#${infra}')"""
      )
    }
  }

  /**
    * Gropus the trajectories by ID ba using an SQL statement directly in postgres.
    * @param infra
    * @param traj
    * @return
    */
  def groupTrajectoriesByID(infra: String, traj: String): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""INSERT INTO #${infra}.#${traj}_trajectories_id (id, time, x, y) SELECT id AS id, array_agg(time ORDER BY time) AS time, array_agg(x ORDER BY time) AS x, array_agg(y ORDER BY time) AS y FROM #${infra}.#${traj}_trajectories GROUP BY id"""
      )
    }
  }

  /**
    * Makes the call to SQL  to group the data by time.
    * @param infra
    * @param traj
    * @return
    */
  def groupTrajectoriesByTime(infra: String, traj: String): Future[Unit] = {
    db.run {
      DBIO.seq(
        sqlu"""INSERT INTO #${infra}.#${traj}_trajectories_time (time, id, x, y) SELECT time AS time, array_agg(id ORDER BY id) AS time, array_agg(x ORDER BY id) AS x, array_agg(y ORDER BY id) AS y FROM #${infra}.#${traj}_trajectories_time_temp GROUP BY time""",
        sqlu"""DROP TABLE #${infra}.#${traj}_trajectories_time_temp"""
      )
    }
  }

  /**
    * Insert the temporary data into the deidcated table
    * @param infra
    * @param traj
    * @param rows
    * @return
    */
  def insertRowIntoTempTrajTimeTable(infra: String, traj: String, rows: Iterable[(String, Double, Double, Double)]): Future[Unit] = {
    db.run {
      DBIO.seq {
        trajectoryByTimeTEMPTable(infra, traj + "_trajectories_time_temp") ++= rows
      }
    }
  }







}



