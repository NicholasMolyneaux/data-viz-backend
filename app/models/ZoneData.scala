package models

import myscala.math.vector.Vector2D

/**
  *
  * @param name name of the zone
  * @param ax bottom left
  * @param ay bottom left
  * @param bx bottom right
  * @param by bottom right
  * @param cx top right
  * @param cy top right
  * @param dx top left
  * @param dy top left
  * @param isOD is an OD zone
  * @param id id for tzhe DB
  */
final case class ZoneData(name: String, ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, dx: Double, dy: Double, isOD: Boolean, id: Int = 0) {


  type Position = Vector2D

  /**
    * computes if a point is inside the zone or not
    * @param pos point to check
    * @return boolean indicating whether the point is inside the zone or not
    */
  def isInside(pos: (Double, Double)): Boolean = {
    val AB: Position = new Position(bx, by) - new Position(ax, ay)
    val BC: Position = new Position(cx, cy) - new Position(bx, by)
    val AP: Position = new Position(pos._1, pos._2) - new Position(ax, ay)
    val BP: Position = new Position(pos._1, pos._2) - new Position(bx, by)
    if (0 <= (AB dot AP) && (AB dot AP) <= (AB dot AB) && 0 <= (BC dot BP) && (BC dot BP) <= (BC dot BC)) true
    else false
  }
}
