package controllers

import javax.inject.{Inject, Provider}
import models._
import play.api.MarkerContext
import play.api.libs.json._
import routers.SummaryDataRouter

import scala.concurrent.{ExecutionContext, Future}

/**
  * DTO for displaying post information.
  */
case class PersonSummaryResource(id: String, origin: String, destination: String, entryTime: Double, exitTime: Double, link: String)

object PersonSummaryResource {

  /**
    * Mapping to write a PostResource out as a JSON value.
    */
  implicit val implicitWrites = new Writes[PersonSummaryResource] {
    def writes(summary: PersonSummaryResource): JsValue = {
      Json.obj(
        "id" -> summary.id,
        "o" -> summary.origin,
        "d" -> summary.destination,
        "en" -> summary.entryTime,
        "ex" -> summary.exitTime
      )
    }
  }
}

/**
  * Controls access to the backend data, returning [[PersonSummaryResource]]
  */
class SummaryResourceHandler @Inject()(routerProvider: Provider[SummaryDataRouter],
                                       trackingDataRepo: TrackingDataRepository)(implicit ec: ExecutionContext) {

  def lookup(schema: String, name: String, id: String)(implicit mc: MarkerContext): Future[Option[PersonSummaryResource]] = {
    val summaryFuture = trackingDataRepo.getPedSummary(schema, name, id)
    summaryFuture.map { maybePostData =>
      maybePostData.map { summaryData =>
        createPostResource(summaryData)
      }
    }
  }

  def lookup(schema: String, name: String, ids: Vector[String])(implicit mc: MarkerContext): Future[Iterable[PersonSummaryResource]] = {
    val summaryFuture = trackingDataRepo.getPedListSummary(schema, name, ids)
    summaryFuture.map { maybePostData =>
      maybePostData.map { summaryData => createPostResource(summaryData)
      }
    }
  }


  def find(schema: String, name: String)(implicit mc: MarkerContext): Future[Iterable[PersonSummaryResource]] = {
    trackingDataRepo.getPedListSummary(schema, name).map { summaryDataList =>
      summaryDataList.map(summaryData => createPostResource(summaryData))
    }
  }

  private def createPostResource(p: PersonSummaryData): PersonSummaryResource = {
    PersonSummaryResource(p.id, p.origin, p.destination, p.entryTime, p.exitTime, routerProvider.get.link(p.id))
  }

}
