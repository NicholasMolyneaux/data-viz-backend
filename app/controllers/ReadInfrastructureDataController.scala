package controllers

import javax.inject.Inject
import models._
import play.api.{Logger, MarkerContext}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.{ExecutionContext, Future}

class ReadInfrastructureDataController @Inject()(cc: ReadInfrastructureInfoControllerComponents, trackingDataRepo: TrackingDataRepository)(implicit ec: ExecutionContext)
  extends ReadDBBaseController(cc) {

  private val logger = Logger(getClass)


  def getWallCollection(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getWalls(infra).map(d => Ok(Json.toJson(WallContainer(d))))
  }

  def getZoneCollection(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getZones(infra).map(d => Ok(Json.toJson(ZoneContainer(d))))
  }
  def getZoneNames(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getZones(infra).map(d => Ok(Json.toJson(d.map(_.name))))
  }

  def getGateCollection(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getGates(infra).map(d => Ok(Json.toJson(GateContainer(d))))
  }

  def getMonitoredAreasCollection(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getMonitoredArea(infra).map(d => Ok(Json.toJson(MonitoredAreaContainer(d))))
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
        "x3" -> data.cx.toString,
        "y3" -> data.cy.toString,
        "x4" -> data.dx.toString,
        "y4" -> data.dy.toString,
        "isod" -> data.isOD.toString,
        "id" -> 0.toString)))
    }
  }
}

  case class MonitoredAreaContainer(data: Iterable[MonitoredArea])
  object MonitoredAreaContainer {
    implicit val implicitWallDataWrites: Writes[MonitoredAreaContainer] = new Writes[MonitoredAreaContainer] {
      def writes(d: MonitoredAreaContainer): JsValue = {
        Json.toJson(d.data.map(data => Map(
          "name" -> data.name,
          "x1" -> data.x1.toString,
          "y1" -> data.y1.toString,
          "x2" -> data.x2.toString,
          "y2" -> data.y2.toString,
          "x3" -> data.x3.toString,
          "y3" -> data.y3.toString,
          "x4" -> data.x4.toString,
          "y4" -> data.y4.toString
        )))
      }
    }
  }

    case class GateContainer(data: Iterable[(Double, Double, Double, Double)])

    object GateContainer {
      implicit val implicitWallDataWrites: Writes[GateContainer] = new Writes[GateContainer] {
        def writes(d: GateContainer): JsValue = {
          Json.toJson(d.data.map(data => Map(
            "x1" -> data._1.toString,
            "y1" -> data._2.toString,
            "x2" -> data._3.toString,
            "y2" -> data._4.toString)))
        }
      }
    }

  }
