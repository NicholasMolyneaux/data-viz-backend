package models

/**
  *
  * @param id pedestrian id
  * @param time time stamp
  * @param x x position
  * @param y y position
  */
final case class TrajRowData(id: String, time: Double, x: Double, y: Double)
