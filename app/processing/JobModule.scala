package processing

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class JobModule extends AbstractModule with AkkaGuiceSupport {
  override def configure() = {
    bindActor[TrajectoryProcessing]("processor-traj")
    bindActor[InfrastructureProcessing]("processor-infra")

    bind(classOf[SchedulerInfra]).asEagerSingleton()
    bind(classOf[SchedulerTraj]).asEagerSingleton()
  }
}