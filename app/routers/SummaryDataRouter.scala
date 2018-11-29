package routers

import controllers.SummaryController
import javax.inject.Inject
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

/**
  * Routes and URLs to the PostResource controller.
  */
class SummaryDataRouter @Inject()(controller: SummaryController) extends SimpleRouter {
  val prefix = "/api"

  def link(id: String): String = {
    import com.netaporter.uri.dsl._
    val url = prefix / id.toString
    url.toString()
  }

  override def routes: Routes = {

    /*case GET(p"/id/$infra/$name/$id") => {
      if (id.contains(",")) {
        controller.showList(infra, name, id)
      } else {
        controller.show(infra, name, id)
      }
    }*/

    case GET(p"/$infra/$name") =>
      controller.index(infra, name)

  }

}
