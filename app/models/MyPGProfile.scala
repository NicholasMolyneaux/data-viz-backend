package models

import com.github.tminglei.slickpg._

trait MyPGProfile extends ExPostgresProfile with PgArraySupport {

  override val api = new API with ArrayImplicits {}
  /*override lazy val Implicit = new ImplicitPlus {}
  override val simple = new SimpleQlPlus {}
  trait ImplicitPlus extends  Implicits with ArrayImplicits
  trait SimpleQlPlus extends SimpleQL with ImplicitPlus*/
}

object MyPGProfile extends MyPGProfile