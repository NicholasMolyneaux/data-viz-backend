package controllers

import javax.inject.{Inject, Provider}
import models.{InfrastructureSummary, TrackingDataRepository}
import play.api.MarkerContext
import routers.SummaryDataRouter
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

class ReadInfrastructureInfoResourceHandler @Inject()(routerProvider: Provider[SummaryDataRouter],
                                                      trackingDataRepo: TrackingDataRepository)(implicit ec: ExecutionContext) {


  def readInfrastructures(implicit mc: MarkerContext): Future[InfrastructureListResource] = {
    trackingDataRepo.getInfrastructures.map { infraList => createInfrastructures(infraList) }
  }

  def deleteInfrastructure(infra: String)(implicit mc: MarkerContext): Future[Unit] = {
    trackingDataRepo.dropInfra(infra)
  }

  private def createInfrastructures(p: Iterable[InfrastructureSummary]): InfrastructureListResource = {
    InfrastructureListResource(p)
  }
}

case class InfrastructureListResource(data: Iterable[InfrastructureSummary])

object InfrastructureListResource {
  /**
    * Mapping to write a PostResource out as a JSON value.
    */
  implicit val implicitWrites = new Writes[InfrastructureListResource] {
    def writes(summary: InfrastructureListResource): JsValue = {
      Json.toJson(
        summary.data.map(d => Map("name" -> d.name, "description" -> d.description))
      )
    }
  }
}
