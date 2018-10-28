package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.data.Form
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import views.html.helper.form

import scala.concurrent.{ExecutionContext, Future}

class SummaryController @Inject()(cc: PostControllerComponents)(implicit ec: ExecutionContext)
  extends PostBaseController(cc) {

  private val logger = Logger(getClass)

  def index: Action[AnyContent] = PostAction.async { implicit request =>
    logger.trace("index: ")
    smmaryResourceHandler.find.map { posts =>
      Ok(Json.toJson(posts))
    }
  }

  def show(id: Long): Action[AnyContent] = PostAction.async { implicit request =>
    logger.trace(s"show: id = $id")
    smmaryResourceHandler.lookup(id).map { post =>
      Ok(Json.toJson(post))
    }
  }

  def show(ids: Vector[Long]): Action[AnyContent] = PostAction.async { implicit request =>
    logger.trace(s"show: id = $ids")
    smmaryResourceHandler.lookup(ids).map { post =>
      Ok(Json.toJson(post))
    }
  }
}