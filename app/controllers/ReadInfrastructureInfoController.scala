package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.data.Form
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import views.html.helper.form

import scala.concurrent.{ExecutionContext, Future}

class ReadInfrastructureInfoController @Inject()(cc: ReadInfrastructureInfoControllerComponents)(implicit ec: ExecutionContext)
  extends ReadDBBaseController(cc) {

  private val logger = Logger(getClass)

  def getInfraList: Action[AnyContent] = PostAction.async { implicit request =>
    logger.info("getting infrastructure (schema) list: ")
    readDBInfoHandler.readInfrastructures.map { infraList =>
      Ok(Json.toJson(infraList))//.withHeaders(("Access-Control-Allow-Origin", "*"))
    }
  }

  def deleteInfra(infra: String): Action[AnyContent] = PostAction { implicit request =>
    readDBInfoHandler.deleteInfrastructure(infra)
    Ok(s"Deleted $infra. ").withHeaders(("Access-Control-Allow-Origin", "*"))
  }
}