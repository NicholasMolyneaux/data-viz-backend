package models

/**
  *
  * @param x1 first x coord
  * @param y1 first y coord
  * @param x2 second x coord
  * @param y2 second y coord
  * @param wtype type of wall (interior or nor)
  * @param id id used in the DB
  */
final case class WallData(x1: Double, y1: Double, x2: Double, y2: Double, wtype: Int, id: Long = 0L)
