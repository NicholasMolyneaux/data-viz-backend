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

  def index(schema: String, name: String): Action[AnyContent] = PostAction.async { implicit request =>
    logger.trace("index: ")
    smmaryResourceHandler.find(schema, name).map { posts =>
      Ok(Json.toJson(posts))
    }
  }

  def show(schema: String, name: String, id: String): Action[AnyContent] = PostAction.async { implicit request =>
    if (id.contains(",")) {
      smmaryResourceHandler.lookup(schema, name, id.split(",").toVector).map { post =>
        Ok(Json.toJson(post))
      }
    }
    else {
      smmaryResourceHandler.lookup(schema, name, id).map { post =>
        Ok(Json.toJson(post))
      }
      /*logger.trace(s"show: id = $id")
    smmaryResourceHandler.lookup(schema, name, id).map { post =>
      Ok(Json.toJson(post))
    }*/
    }


  }

  def showList(schema: String, name: String, ids: String): Action[AnyContent] = PostAction.async { implicit request =>
    logger.trace(s"show: id = $ids")
    smmaryResourceHandler.lookup(schema, name, ids.split(",").toVector).map { post =>
      Ok(Json.toJson(post))
    }
  }

}