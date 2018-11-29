package controllers

import javax.inject.Inject
import models.{InfrastructureSummary, TrackingDataRepository}
import play.api.{Configuration, Logger}
import play.api.data.Form
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{JsPath, JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent, Result}
import views.html.helper.form
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

class ReadInfrastructureInfoController @Inject()(cc: ReadInfrastructureInfoControllerComponents,
                                                 trackingDataRepo: TrackingDataRepository,
                                                 config: Configuration)(implicit ec: ExecutionContext)
  extends ReadDBBaseController(cc) {

  private val logger = Logger(getClass)

  def getInfraList: Action[AnyContent] = PostAction.async { implicit request =>
    logger.info("getting infrastructure (schema) list: ")
    trackingDataRepo.getInfrastructures.map { infraList => Ok(Json.toJson(InfrastructureListResource(infraList))) }
  }

  def deleteInfra(infra: String, pwd: String): Action[AnyContent] = PostAction { implicit request =>
    if (pwd == config.get[String]("data.uploadkey.traj")) {
      trackingDataRepo.dropInfra(infra)
      Ok(s"Deleted $infra. ")
    } else {
      BadRequest("Key is wrong !")
    }
  }

  def deleteTrajectory(infra: String, traj: String, pwd: String): Action[AnyContent] = PostAction { implicit request =>
    if (pwd == config.get[String]("data.uploadkey.traj")) {
      trackingDataRepo.dropTraj(infra, traj)
      Ok(s"Deleted $traj from $infra.")
    } else {
      BadRequest("Key is wrong !")
    }
  }


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