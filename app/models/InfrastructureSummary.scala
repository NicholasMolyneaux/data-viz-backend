package models

/**
  *
  * @param name name of the infrastructure
  * @param description brief descripition
  * @param xmin smallest x value
  * @param xmax largest x value
  * @param ymin smallest y value
  * @param ymax largest y value
  * @param id id (used on the DB)
  */
final case class InfrastructureSummary(name: String, description: String, xmin: Double = 0, xmax: Double = 0, ymin: Double = 0, ymax: Double = 0, id: Int = 0)
