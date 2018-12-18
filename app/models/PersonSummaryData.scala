package models

/**
  *
  * @param id id of the pedestrian taken from the tracking data
  * @param origin origin zone
  * @param destination destination zone
  * @param entryTime entrance time into the system
  * @param exitTime exit time from the system
  * @param tt total travel time for this pedestrian
  */
final case class PersonSummaryData(id: String, origin: String, destination: String, entryTime: Double, exitTime: Double, tt: Double)
