package processing

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import processing.InfrastructureProcessing
import processing.TrajectoryProcessing

class SchedulerInfra @Inject()(val system: ActorSystem, @Named("processor-infra") val schedulerActor: ActorRef)(implicit ec: ExecutionContext) {
  system.scheduler.schedule(10.microseconds, 60.seconds, schedulerActor, "process-infra")
}

class SchedulerTraj @Inject()(val system: ActorSystem, @Named("processor-traj") val schedulerActor: ActorRef)(implicit ec: ExecutionContext) {
  system.scheduler.schedule(10.microseconds, 30.seconds, schedulerActor, "process-traj")
}