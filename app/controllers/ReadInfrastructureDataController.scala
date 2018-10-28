package controllers

import javax.inject.Inject
import models.{TrackingDataRepository, WallData, ZoneData}
import play.api.{Logger, MarkerContext}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.{ExecutionContext, Future}

class ReadInfrastructureDataController @Inject()(cc: ReadInfrastructureInfoControllerComponents, trackingDataRepo: TrackingDataRepository)(implicit ec: ExecutionContext)
  extends ReadDBBaseController(cc) {

  private val logger = Logger(getClass)


  def getWallCollection(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getWalls(infra).map(d => Ok(Json.toJson(WallContainer(d))).withHeaders(("Access-Control-Allow-Origin", "*")))
  }

  def getZoneCollection(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getZones(infra).map(d => Ok(Json.toJson(ZoneContainer(d))).withHeaders(("Access-Control-Allow-Origin", "*")))
  }
}

case class WallContainer(data: Iterable[WallData])
object WallContainer {
  implicit val implicitWallDataWrites: Writes[WallContainer] = new Writes[WallContainer] {
    def writes(d: WallContainer): JsValue = {
      Json.toJson(d.data.map(data => Map("x1" -> data.x1.toString, "y1" -> data.y1.toString, "x2" -> data.x2.toString, "y2" -> data.y2.toString, "wtype" -> data.wtype.toString, "id" -> 0.toString)))
    }
  }
}

case class ZoneContainer(data: Iterable[ZoneData])
object ZoneContainer {
  implicit val implicitWallDataWrites: Writes[ZoneContainer] = new Writes[ZoneContainer] {
    def writes(d: ZoneContainer): JsValue = {
      Json.toJson(d.data.map(data => Map(
        "name" -> data.name,
        "x1" -> data.ax.toString,
        "y1" -> data.ay.toString,
        "x2" -> data.bx.toString,
        "y2" -> data.by.toString,
        "x3" -> data.ax.toString,
        "y3" -> data.ay.toString,
        "x4" -> data.bx.toString,
        "y4" -> data.by.toString,
        "isod" -> data.isOD.toString,
        "id" -> 0.toString)))
    }
  }
}

