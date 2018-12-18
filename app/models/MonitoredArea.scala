package models

/**
  *
  * @param name name of the zone where the density is computed
  * @param x1 bottom left
  * @param y1 bottom left
  * @param x2 bottom right
  * @param y2 bottom right
  * @param x3 top right
  * @param y3 top right
  * @param x4 top left
  * @param y4 top left
  * @param id used in the DB
  */
case class MonitoredArea(name: String, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, x4: Double, y4: Double, id: Long = 0)
