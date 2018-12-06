package processing

//import java.io.{BufferedWriter, File, FileWriter}
import java.math.MathContext
import java.math.RoundingMode.{CEILING, FLOOR}

import akka.actor.Actor
import com.google.inject.Inject
import play.api.{Configuration, Logger}
import javax.inject.Singleton
import java.nio.file.{DirectoryStream, Files, Path, Paths}

import models._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads
import play.api.libs.json._

import scala.collection.JavaConversions._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.BufferedSource
import scala.util.{Failure, Success}

@Singleton
class TrajectoryProcessing @Inject()(trackingDataRepo: TrackingDataRepository, config: Configuration)(implicit ec: ExecutionContext) extends Actor {

  val uploadDir: String = config.get[String]("data.upload.path")
  val processedDir: String = config.get[String]("data.processed.path")
  val PGSQLImportDir: String = config.get[String]("data.pgsqlimport")


  def receive = {
    case "process-traj" => updateTrajContents()
    case _ => Logger.error("scheduler key not recognized")
  }

  def updateTrajContents(): Unit = {
    Logger.warn("Checking for trajectory files to process.")

    val fileStreamToProcess: DirectoryStream[Path] = Files.newDirectoryStream(Paths.get(uploadDir + "traj/"), "*.csv")
    val fileStreamProcessing: DirectoryStream[Path] = Files.newDirectoryStream(Paths.get(uploadDir + "traj/"), "*.processing")
    val filesToProcess: Vector[Path] = fileStreamToProcess.toVector
    val filesProcessing: Vector[Path] = fileStreamProcessing.toVector
    fileStreamToProcess.close()
    fileStreamProcessing.close()

    // only process one file at a time.
    if (filesToProcess.nonEmpty && filesProcessing.isEmpty) {
      Logger.warn("Started processing file: " + filesToProcess.head.getFileName.toString)
      processTrackingData(filesToProcess.head)
    } else {
      Logger.warn("No processing to start.")
    }
  }

  def processTrackingData(file: Path): Unit = {

    val tmpFileName: String = file.getFileName.toString + ".processing"
    val (infraName, trajName) = (file.getFileName.toString.split("__", 3).head, file.getFileName.toString.split("__", 3).tail.head)
    Files.move(file, file.resolveSibling(tmpFileName))

    createSummaryTable(insertRawData()).foreach(v => {
      groupDataByID().foreach(vv => {
        interpolateToTimes().foreach(vvv => {
          groupByTime().onComplete {
            case Success(s) => {
              Logger.warn("Grouping trajectories by times... done !")
              trackingDataRepo.updateTrajectoryProcessingState(infraName, trajName, tf = true)
            }
            case Failure(f) => throw f
          }
        })
      })
      })


    def interpolateToTimes(): Future[Unit] = {

      Logger.warn("Get trajectories by ID...")
      trackingDataRepo.getTrajectoriesByID(infraName, trajName, None).map(data => {
        Logger.warn("Get trajectories by ID... done !")
        Logger.warn("Interpolating trajectories to regular times...")
        //Future {
        val interval = BigDecimal(0.1)
        val minimumValue = BigDecimal(data.flatMap(_.time).min).setScale(1, scala.math.BigDecimal.RoundingMode.FLOOR)
        val maximumValue = BigDecimal(data.flatMap(_.time).max).setScale(1, scala.math.BigDecimal.RoundingMode.CEILING)

        trackingDataRepo.setTrajectoryTimeBounds(infraName, trajName, minimumValue.toDouble, maximumValue.toDouble)

        val times = minimumValue to maximumValue by interval

        //Logger.warn(times.mkString(", "))
        data.foreach(ped => {
          //println(ped.id)
          //println(ped.time)
          val interpolatedTimes: Iterable[(String, Double, Double, Double)] = for (t: BigDecimal <- times if ped.time.min <= t && t <= ped.time.max) yield {
            //Logger.warn(t + ", " + ped.id)
            val diff: Vector[BigDecimal] = ped.time.map(v => BigDecimal(v) - t).toVector
            //Logger.warn(diff.mkString(", "))

            val idxSup: Int = diff.indexOf(diff.filter(_ >= 0).min)
            val idxInf: Int = diff.indexOf(diff.filter(_ <= 0).max)

            //println("t " + t + ", " + ped.time.min + ", " + ped.time.max)
            //println("idxinf " + ped.time(idxInf))
            //println("idexsup " + ped.time(idxSup))

            if (idxInf != idxSup) {
              //Logger.warn(t.toString() + ", " + BigDecimal(ped.time(idxInf)).toString + ", " + BigDecimal(ped.time(idxSup)).toString)
              val tRatio = (t - BigDecimal(ped.time(idxInf))) / (BigDecimal(ped.time(idxSup)) - BigDecimal(ped.time(idxInf)))
              //Logger.warn("Inserting: " + ped.id + ", " + t.toDouble + ", " + (ped.x(idxInf) + tRatio * (ped.x(idxSup) - ped.x(idxInf))).toDouble + ", " + (ped.y(idxInf) + tRatio * (ped.y(idxSup) - ped.y(idxInf))).toDouble)
              (ped.id, t.toDouble, (ped.x(idxInf) + tRatio * (ped.x(idxSup) - ped.x(idxInf))).toDouble, (ped.y(idxInf) + tRatio * (ped.y(idxSup) - ped.y(idxInf))).toDouble)
            } else {
              //Logger.warn("Inserting: " + ped.id + ", " + t.toDouble + ", " + ped.x(idxInf)+ ", " + ped.y(idxInf) )
              (ped.id, t.toDouble, ped.x(idxInf), ped.y(idxInf))
            }
          }
          trackingDataRepo.insertRowIntoTempTrajTimeTable(infraName, trajName, interpolatedTimes)
        })
        Logger.warn("Interpolating trajectories to regular times... done !")
      })
    }

    def groupByTime(): Future[Unit] = {
      Logger.warn("Grouping trajectories by times...")
      trackingDataRepo.groupTrajectoriesByTime(infraName, trajName)
    }


    //}
    //case Failure(f) => f
    //}
    //}
    //case Failure(f) => f
    //}


    def insertRawData(): scala.collection.mutable.Map[String, (Double, Double, Double, Double, Double, Double)] = {

      Logger.warn("File for processing moved to: " + tmpFileName)

      Logger.warn("Creating tables for " + trajName + " data set inside " + infraName + " infrastructure.")
      trackingDataRepo.createTrajTables(infraName, trajName) //.onComplete {

      //case Success(s) => {

      //Future {
      Logger.warn("Starting processing lines of : " + uploadDir + "traj/" + tmpFileName)
      val pedMap: scala.collection.mutable.Map[String, (Double, Double, Double, Double, Double, Double)] = scala.collection.mutable.Map()
      val bufferedSource: scala.io.BufferedSource = scala.io.Source.fromFile(uploadDir + "traj/" + tmpFileName)

      val tmpFile = Paths.get(PGSQLImportDir + "tmp.csv")

      if (Files.exists(tmpFile)) { Files.delete(tmpFile) }
      val tmpCSVFile = Files.newBufferedWriter(tmpFile)

      for (l <- bufferedSource.getLines) {
        val cols = l.split(",")
        val t: Double = cols(3).toDouble * 3600.0 + cols(4).toDouble * 60.0 + cols(5).toDouble + cols(6).toDouble / 1000.0
        val currentPedSum = pedMap.getOrElseUpdate(cols(10), (t, cols(8).toDouble / 1000.0, cols(9).toDouble / 1000.0, t, cols(8).toDouble / 1000.0, cols(9).toDouble / 1000.0))
        if (t < currentPedSum._1) {
          pedMap.update(cols(10), (t, cols(8).toDouble / 1000.0, cols(9).toDouble / 1000.0, currentPedSum._4, currentPedSum._5, currentPedSum._6))
        }
        if (t > currentPedSum._4) {
          pedMap.update(cols(10), (currentPedSum._1, currentPedSum._2, currentPedSum._3, t, cols(8).toDouble / 1000.0, cols(9).toDouble / 1000.0))
        }
        tmpCSVFile.write(cols(10) + "," + t.toString + "," +  cols(8).toDouble / 1000.0 + "," + cols(9).toDouble / 1000.0 + "\n")
        //trackingDataRepo.insertRowIntoTrajTable(infraName, trajName, TrajRowData(cols(10), t, cols(8).toDouble / 1000.0, cols(9).toDouble / 1000.0))
      }
      bufferedSource.close()
      tmpCSVFile.close()
      Logger.warn("Finished processing raw trajectory data.")

      Files.move(Paths.get(file.getParent + "/" + tmpFileName), Paths.get(processedDir + "traj/" + file.getFileName))

      Files.setOwner(tmpFile, tmpFile.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName("postgres"))

      import scala.concurrent.duration._

      Await.ready(trackingDataRepo.insertTrajTableFile(infraName, trajName, PGSQLImportDir + "tmp.csv"), Duration.Inf)

      pedMap
    }

    def groupDataByID(): Future[Unit] = {
      trackingDataRepo.groupTrajectoriesByID(infraName, trajName)
    }

    def createSummaryTable(pedMap: scala.collection.mutable.Map[String, (Double, Double, Double, Double, Double, Double)]): Future[Unit] = {

      Logger.warn("Creating summary table...")
      Logger.warn(pedMap.keys.mkString(", "))
      trackingDataRepo.getZones(infraName).map(zones => {//onComplete {
        //case Success(zones) => {
        Logger.warn(zones.mkString(", "))
          pedMap.foreach(ped => {
            val OZone: Option[ZoneData] = zones.find(z => z.isInside((ped._2._2, ped._2._3)))
            val DZone: Option[ZoneData] = zones.find(z => z.isInside((ped._2._5, ped._2._6))) //.get.name
            Logger.warn(ped._1 + ", " + ped._2.toString() + ", " + OZone.isDefined + ", " + DZone.isDefined)
            if (OZone.isDefined && DZone.isDefined) {
              val tt: Double = ped._2._4 - ped._2._1
              trackingDataRepo.insertRowIntoPedSummaryTable(infraName, trajName, PersonSummaryData(ped._1, OZone.get.name, DZone.get.name, ped._2._1, ped._2._4, tt))
            }
          })
        })
        //case Failure(f) => throw new Exception("Error getting zones for infra while computing summary. infra=" + infraName + "\n" + f)
      }
    //}
  }


}
