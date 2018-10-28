package processing

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class Scheduler @Inject()(val system: ActorSystem, @Named("scheduler-actor") val schedulerActor: ActorRef)(implicit ec: ExecutionContext) {
  system.scheduler.schedule(0.microseconds, 60.seconds, schedulerActor, "process-traj")
  system.scheduler.schedule(0.microseconds, 15.seconds, schedulerActor, "process-infra")


}