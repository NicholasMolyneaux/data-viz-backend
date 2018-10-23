package models

  import akka.actor.ActorSystem
  import javax.inject.{Inject, Singleton}
  import play.api.db.slick.DatabaseConfigProvider
  import play.api.libs.concurrent.CustomExecutionContext
  import play.api.{Logger, MarkerContext}
  import slick.jdbc.JdbcProfile

  import scala.concurrent.Future



  /**
    * A pure non-blocking interface for the PostRepository.
    */
  trait TrackingDataRepository {

    def list()(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

    def get(id: Long)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]]

    def get(ids: Vector[Long])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]]

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

    /**
      * Here we define the summary table.
      */
    private class SummaryTable(tag: Tag) extends Table[PersonSummaryData](tag, Some("test"), "summary") {

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
      * The starting point for all queries on the people table.
      */
    private val summary = TableQuery[SummaryTable]

    override def list()(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
      summary.result
    }

    override def get(id: Long)(implicit mc: MarkerContext): Future[Option[PersonSummaryData]] = db.run {
      summary.filter(r => r.id === id).result.headOption
    }

    override def get(ids: Vector[Long])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryData]] = db.run {
      (for {r <- summary if r.id inSetBind ids} yield r).result
    }
  }
