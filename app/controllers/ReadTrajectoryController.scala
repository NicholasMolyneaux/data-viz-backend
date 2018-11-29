package controllers

import javax.inject.Inject
import models._
import play.api.{Logger, MarkerContext}
import play.api.mvc.{Action, AnyContent}

import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

class ReadTrajectoryController @Inject()(cc: ReadTrajectoryControllerComponents, trackingDataRepo: TrackingDataRepository)(implicit ec: ExecutionContext)
  extends ReadTrajectoryBaseController(cc) {

  private val logger = Logger(getClass)

  def getListTrajectories(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getListTrajectories(infra).map(d => Ok(Json.toJson(d.map(dd => Json.obj("name" -> dd._1.toString, "tmin" -> dd._2, "tmax" -> dd._3)))))
  }

  def getListPedIDs(infra: String, traj: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getPedIDs(infra, traj).map(v => Ok(Json.toJson(v)))
  }

  def getTrajectoriesByID(infra: String, traj: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getTrajectoriesByID(infra, traj, None).map(d => Ok(Json.toJson(TrajContainerID(d))))
  }

  def getTrajectoriesByIDWithFilter(infra: String, traj: String, ids: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getTrajectoriesByID(infra, traj, Some(ids.split(",").toVector)).map(d => Ok(Json.toJson(TrajContainerID(d))))
  }

  def getTrajectoriesByTime(infra: String, traj: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getTrajectoriesByTime(infra, traj).map(d => Ok(Json.toJson(TrajContainerTime(d.toVector.sortBy(_.time).map(v => TrajDataByTimeJson(v.time, v.id.zip(v.x.zip(v.y)).map(vv => TrajPoint(vv._1, vv._2._1, vv._2._2))))))))
  }

  def getTrajectoriesByTimeLB(infra: String, traj: String, lb: Double): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getTrajectoriesByTime(infra, traj, lb=Some(lb)).map(d => Ok(Json.toJson(TrajContainerTime(d.toVector.sortBy(_.time).map(v => TrajDataByTimeJson(v.time, v.id.zip(v.x.zip(v.y)).map(vv => TrajPoint(vv._1, vv._2._1, vv._2._2))))))))
  }

  def getTrajectoriesByTimeUB(infra: String, traj: String, ub: Double): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getTrajectoriesByTime(infra, traj, ub=Some(ub)).map(d => Ok(Json.toJson(TrajContainerTime(d.toVector.sortBy(_.time).map(v => TrajDataByTimeJson(v.time, v.id.zip(v.x.zip(v.y)).map(vv => TrajPoint(vv._1, vv._2._1, vv._2._2))))))))
  }

  def getTrajectoriesByTimeLBUB(infra: String, traj: String, lb: Double, ub: Double): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getTrajectoriesByTime(infra, traj, lb=Some(lb), ub=Some(ub)).map(d => Ok(Json.toJson(TrajContainerTime(d.toVector.sortBy(_.time).map(v => TrajDataByTimeJson(v.time, v.id.zip(v.x.zip(v.y)).map(vv => TrajPoint(vv._1, vv._2._1, vv._2._2))))))))
  }

  implicit val TrajDataByIDWrites: Writes[TrajDataByID] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "time").write[List[Double]] and
      (JsPath \ "x").write[List[Double]] and
      (JsPath \ "y").write[List[Double]]
    ) (unlift(TrajDataByID.unapply))

  case class TrajContainerID(data: Iterable[TrajDataByID])

  object TrajContainerID {

    implicit val implicitTrajByIDDataWrites: Writes[TrajContainerID] = new Writes[TrajContainerID] {
      def writes(d: TrajContainerID): JsValue = {
        Json.toJson(d.data.map(data => Json.toJson(data)))
      }
    }
  }

  case class TrajPoint(id: String, x: Double, y: Double)
  implicit val TTrajPointWrites: Writes[TrajPoint] = (
    (JsPath \ "id").write[String] and
     (JsPath \ "x").write[Double] and
      (JsPath \ "y").write[Double]
    ) (unlift(TrajPoint.unapply))

  case class TrajDataByTimeJson(time: Double, data: List[TrajPoint])
  implicit val TrajDataByTimeWrites: Writes[TrajDataByTimeJson]= (
    (JsPath \ "time").write[Double] and
      (JsPath \ "data").write[List[TrajPoint]]
    ) (unlift(TrajDataByTimeJson.unapply))


  case class TrajContainerTime(data: Iterable[TrajDataByTimeJson])

  object TrajContainerTime {
    implicit val implicitTrajByIDDataWrites: Writes[TrajContainerTime] = new Writes[TrajContainerTime] {
      def writes(d: TrajContainerTime): JsValue = {
        Json.toJson(d.data.map(data => Json.toJson(data)))
      }
    }
  }
/*
  def getZoneCollection(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getZones(infra).map(d => Ok(Json.toJson(ZoneContainer(d))))
  }
  def getZoneNames(infra: String): Action[AnyContent] = Action.async { implicit request =>
    trackingDataRepo.getZones(infra).map(d => Ok(Json.toJson(d.map(_.name))))
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
  }*/
}

