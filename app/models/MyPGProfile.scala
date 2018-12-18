package models

import com.github.tminglei.slickpg._

/**
  * This is an extension of the basic play-slick which allows more complex data types to be passed between
  * Scala and PostgreSQL
  */
trait MyPGProfile extends ExPostgresProfile with PgArraySupport {

  override val api = new API with ArrayImplicits {}

}

object MyPGProfile extends MyPGProfile