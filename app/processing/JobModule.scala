package processing

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class JobModule extends AbstractModule with AkkaGuiceSupport {
  override def configure() = {
    bindActor[ProcessorActor]("scheduler-actor")
    bind(classOf[Scheduler]).asEagerSingleton()
  }
}