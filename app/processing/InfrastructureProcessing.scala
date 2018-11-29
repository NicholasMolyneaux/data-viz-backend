package processing

import java.math.MathContext
import java.math.RoundingMode.{CEILING, FLOOR}
import java.nio.file.{DirectoryStream, Files, Path, Paths}

import akka.actor.Actor
import com.google.inject.Inject
import javax.inject.Singleton
import models.{PersonSummaryData, TrackingDataRepository, ZoneData}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, _}
import play.api.{Configuration, Logger}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.io.BufferedSource
import scala.util.{Failure, Success}

@Singleton
class InfrastructureProcessing @Inject()(trackingDataRepo: TrackingDataRepository, config: Configuration)(implicit ec: ExecutionContext) extends Actor {
  // ******************************************************************************************
  //                   CASE CLASSES AND IMPLICIT CONVERSIONS FOR CONTINUOUS SPACE
  // ******************************************************************************************

  /** Wall class reader for interactions with pedestrians
    *
    * @param x1 x coord of first point
    * @param y1 y coord of first point
    * @param x2 x coord of second point
    * @param y2 y coord of second point
    */
  case class Wall_JSON(comment: String, x1: Double, y1: Double, x2: Double, y2: Double, wallType: Int)

  /**
    * Reads the JSON structure into a [[Wall_JSON]] object. No validation on arguments is done.
    */
  implicit val WallReads: Reads[Wall_JSON] = (
    (JsPath \ "comment").read[String] and
      (JsPath \ "x1").read[Double] and
      (JsPath \ "y1").read[Double] and
      (JsPath \ "x2").read[Double] and
      (JsPath \ "y2").read[Double] and
      (JsPath \ "type").read[Int]
    ) (Wall_JSON.apply _)

  case class Vertex_JSON(name: String, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, x4: Double, y4: Double, OD: Boolean)

  /**
    * Reads a JSON structure into a [[Vertex_JSON]] object. No validation on the arguments is done.
    */
  implicit val Vertex_JSON_Reads: Reads[Vertex_JSON] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "x1").read[Double] and
      (JsPath \ "y1").read[Double] and
      (JsPath \ "x2").read[Double] and
      (JsPath \ "y2").read[Double] and
      (JsPath \ "x3").read[Double] and
      (JsPath \ "y3").read[Double] and
      (JsPath \ "x4").read[Double] and
      (JsPath \ "y4").read[Double] and
      (JsPath \ "OD").read[Boolean]
    ) (Vertex_JSON.apply _)


  case class ContinuousSpaceParser(location: String,
                                   subLocation: String,
                                   walls: Vector[Wall_JSON])

  implicit val InfraSFParserReads: Reads[ContinuousSpaceParser] = (
    (JsPath \ "location").read[String] and
      (JsPath \ "sublocation").read[String] and
      (JsPath \ "walls").read[Vector[Wall_JSON]]
    ) (ContinuousSpaceParser.apply _)

  case class FlowGates_JSON(o: String, d: String, start_pos_x: Double, start_pos_y: Double, end_pos_x: Double, end_pos_y: Double, area: String, funcForm: Option[String], funcParam: Option[Vector[Double]])

  /**
    * Reads the JSON structure into a [[FlowGates_JSON]] object. No validation on arguments is done.
    */
  implicit val FlowGates_JSONReads: Reads[FlowGates_JSON] = (
    (JsPath \ "o").read[String] and
      (JsPath \ "d").read[String] and
      (JsPath \ "start_pos_x").read[Double] and
      (JsPath \ "start_pos_y").read[Double] and
      (JsPath \ "end_pos_x").read[Double] and
      (JsPath \ "end_pos_y").read[Double] and
      (JsPath \ "controlled_area").read[String] and
      (JsPath \ "functional_form").readNullable[String] and
      (JsPath \ "functional_parameters").readNullable[Vector[Double]]
    ) (FlowGates_JSON.apply _)

  case class GraphParser(location: String,
                         subLocation: String,
                         zones: Vector[Vertex_JSON],
                         gates: Vector[FlowGates_JSON])

  implicit val GraphParserReads: Reads[GraphParser] = (
    (JsPath \ "location").read[String] and
      (JsPath \ "sublocation").read[String] and
      (JsPath \ "nodes").read[Vector[Vertex_JSON]] and
      (JsPath \ "flow_gates").read[Vector[FlowGates_JSON]]
    ) (GraphParser.apply _)

  class ReadContinuousSpace(file: String) {

    val continuousSpace: Vector[(Double, Double, Double, Double, Int)] = {
      val source: BufferedSource = scala.io.Source.fromFile(file)
      val input: JsValue = Json.parse(try source.mkString finally source.close)

      input.validate[ContinuousSpaceParser] match {
        case s: JsSuccess[ContinuousSpaceParser] => {
          source.close()
          s.get.walls.map(w => (w.x1, w.y1, w.x2, w.y2, w.wallType))
        }
        case e: JsError => {
          source.close()
          throw new Error("Error while parsing SF infrastructure file: " + file + ", JSON error: " + JsError.toJson(e).toString())
        }
      }
    }
  }

  def ReadGraph(file: String): (Iterable[(String, Double, Double, Double, Double, Double, Double, Double, Double, Boolean)], Iterable[(Double, Double, Double, Double)]) = {

    val source: BufferedSource = scala.io.Source.fromFile(file)
    val input: JsValue = Json.parse(try source.mkString finally source.close)
    Logger.warn("Parsed graph file.")

    input.validate[GraphParser] match {
      case s: JsSuccess[GraphParser] => {
        //Logger.warn("Parsing succesful")
        source.close()
        (
          s.get.zones.map(z => (z.name, z.x1, z.y1, z.x2, z.y2, z.x3, z.y3, z.x4, z.y4, z.OD)),
          s.get.gates.map(g => (g.start_pos_x, g.start_pos_y, g.end_pos_x, g.end_pos_y))
        )
      }
      case e: JsError => {
        //*Logger.warn("Parsing unsuccesful")
        source.close()
        throw new Error("Error while parsing SF infrastructure file: , JSON error: " + JsError.toJson(e).toString())
      }
    }
  }

  val uploadDir: String = config.get[String]("data.upload.path")
  val processedDir: String = config.get[String]("data.processed.path")


  def receive = {
    case "process-infra" => updateInfraContents
    case _ => Logger.error("scheduler key not recognized")
  }


  type Place = String

  def updateInfraContents(): Unit = {
    Logger.warn("processing infra")

    val fileStreamToProcess: DirectoryStream[Path] = Files.newDirectoryStream(Paths.get(uploadDir + "infra/"), "*.json")
    val fileStreamProcessing: DirectoryStream[Path] = Files.newDirectoryStream(Paths.get(uploadDir + "infra/"), "*.processing")

    val filesToProcess: Vector[Path] = fileStreamToProcess.toVector
    val filesProcessing: Vector[Path] = fileStreamProcessing.toVector
    fileStreamToProcess.close()
    fileStreamProcessing.close()

    if (filesToProcess.nonEmpty && filesProcessing.isEmpty) {
      Logger.warn("inserting infra: " + filesToProcess.mkString(", "))


      val filesByPlace: Map[Place, Iterable[(Path, Array[String])]] = filesToProcess.map(fn => (fn, fn.getFileName.toString.split("__", 3))).groupBy(_._2(0))
      filesByPlace.foreach(place => {

        //trackingDataRepo.schemaNameOld = place._1

        val wallFile: (Path, Array[String]) = place._2.find(d => d._2(1) == "walls").get
        val graphFile: (Path, Array[String]) = place._2.find(d => d._2(1) == "graph").get
        Files.move(wallFile._1, wallFile._1.resolveSibling(wallFile._1.getFileName.toString + ".processing"))
        Files.move(graphFile._1, graphFile._1.resolveSibling(graphFile._1.getFileName.toString + ".processing"))

        val wallFileTmp: Path = Paths.get(wallFile._1.getFileName.toString + ".processing")
        val graphFileTmp: Path = Paths.get(graphFile._1.getFileName.toString + ".processing")


        //Logger.warn("inserting infra from file:  " + "/home/nicholas/data/uploaded/infra/" + place._2.find(_ (1) == "walls").get.mkString("__"))
        trackingDataRepo.createWallsTable(place._1, new ReadContinuousSpace(uploadDir + "infra/" + wallFileTmp.getFileName.toString).continuousSpace)
        Logger.warn("Finished processing walls file. Starting processing graph.")
        val graphData = ReadGraph(uploadDir + "infra/" + graphFileTmp.getFileName.toString)
        Logger.warn("Finished parsing graph file again.")
        trackingDataRepo.createODZonesTable(place._1, graphData._1)
        trackingDataRepo.createGatesTable(place._1, graphData._2)

        Files.move(Paths.get(wallFile._1.getParent + "/" + wallFileTmp), Paths.get(processedDir + "infra/" + wallFile._1.getFileName))
        Files.move(Paths.get(graphFile._1.getParent + "/" + graphFileTmp), Paths.get(processedDir + "infra/" + graphFile._1.getFileName))
      })
    }
  }


}