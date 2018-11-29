package controllers

import javax.inject.{Inject, Provider}
import models.{InfrastructureSummary, TrackingDataRepository}
import play.api.MarkerContext
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}
import routers.SummaryDataRouter

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import javax.inject.Inject
import models._
import play.api.{Logger, MarkerContext}
import play.api.mvc.{Action, AnyContent}

import play.api.libs.functional.syntax._
import play.api.libs.json._


class ReadInfrastructureInfoResourceHandler @Inject()(cc: ReadInfrastructureInfoControllerComponents,
                                                       routerProvider: Provider[SummaryDataRouter],
                                                      trackingDataRepo: TrackingDataRepository)(implicit ec: ExecutionContext) extends ReadDBBaseController(cc){

/*
  def readInfrastructures(implicit mc: MarkerContext): Future[InfrastructureListResource] = Action.async { implicit request =>
    trackingDataRepo.getInfrastructures.map { infraList => Ok(Json.toJson(InfrastructureListResource(infraList))) }
  }

  private def createInfrastructures(p: Iterable[InfrastructureSummary]): InfrastructureListResource = {
    InfrastructureListResource(p)
  }
*/

  implicit val InfrastructureSummaryWrites: Writes[InfrastructureSummary] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "description").write[String] and
      (JsPath \ "xmin").write[Double] and
      (JsPath \ "xmax").write[Double] and
      (JsPath \ "ymin").write[Double] and
      (JsPath \ "ymax").write[Double] and
      (JsPath \ "id").write[Int]
    ) (unlift(InfrastructureSummary.unapply))

  case class InfrastructureListResource(data: Iterable[InfrastructureSummary])


  object InfrastructureListResource {
    /**
      * Mapping to write a PostResource out as a JSON value.
      */
    implicit val implicitWrites: Writes[InfrastructureListResource] = new Writes[InfrastructureListResource] {
      def writes(summary: InfrastructureListResource): JsValue = {
        Json.toJson(summary.data.map(data => Json.toJson(data)))
      }
    }
  }


}

