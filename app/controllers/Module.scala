import com.google.inject.AbstractModule
import javax.inject._
import models.{TrackingDataRepository, TrackingDataRepositoryImpl}
import net.codingwell.scalaguice.ScalaModule
import play.api.{Configuration, Environment}

/**
  * Sets up custom components for Play.
  *
  * https://www.playframework.com/documentation/latest/ScalaDependencyInjection
  */
class Module(environment: Environment, configuration: Configuration)
  extends AbstractModule
    with ScalaModule {

  override def configure() = {
    bind[TrackingDataRepository].to[TrackingDataRepositoryImpl].in[Singleton]
  }
}
