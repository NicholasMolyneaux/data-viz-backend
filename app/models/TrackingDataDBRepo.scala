package models

import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ Future, ExecutionContext }


//https://github.com/playframework/play-scala-slick-example/blob/2.6.x/app/models/PersonRepository.scala
@Singleton
class TrackingDataDBRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  // We want the JdbcProfile for this provider
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  // These imports are important, the first one brings db into scope, which will let you do the actual db operations.
  // The second one brings the Slick DSL into scope, which lets you define the table and other queries.
  import dbConfig._
  import profile.api._

  /**
    * Here we define the summary table.
    */
  private class SummaryTable(tag: Tag) extends Table[PersonSummaryData](tag, Some("test"), "todos") {

    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Long]("id", O.PrimaryKey)

    /** The origin zone column */
    def origin = column[String]("origin")

    /** The destinatoion zone column */
    def destination = column[String]("destinatoin")

    /** The entry time column */
    def entryTime = column[Double]("entryTime")

    /** The exit time column */
    def exitTime = column[Double]("exitTime")


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


  /**
    * The starting point for all queries on the people table.
    */
  private val summary = TableQuery[SummaryTable]

  def list(): Future[Seq[PersonSummaryData]] = db.run {
    summary.result
  }
}
