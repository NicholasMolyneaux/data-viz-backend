
package controllers

import java.io.File
import java.nio.file.{Files, Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl._
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import models.TrackingDataRepository
import play.api._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.streams._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.api.libs.concurrent.Futures
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}
import upload.{UploadInfraForm, UploadVSForm}


/**
  * This controller handles a file upload.
  */
@Singleton
class UploadController @Inject()(cc: MessagesControllerComponents, trackingDataRepo: TrackingDataRepository, config: Configuration)
                                (implicit executionContext: ExecutionContext)

  extends MessagesAbstractController(cc) {

  private val logger = Logger(this.getClass)

  val form = Form(
    mapping(
      "infra" -> text,
      "description" -> text,
      "name" -> text,
      "key" -> text
    )(UploadVSForm.apply)(UploadVSForm.unapply)
  )

  /**
    * Renders a start page.
    */
  /*def index = Action { implicit request =>
    Ok(views.html.index(form))
  }*/

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  /**
    * Uses a custom FilePartHandler to return a type of "File" rather than
    * using Play's TemporaryFile class.  Deletion must happen explicitly on
    * completion, rather than TemporaryFile (which uses finalization to
    * delete temporary files).
    *
    * @return
    */
  private def handleFilePartAsFile: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType) =>
      val path: Path = Files.createTempFile("multipartBody", "tempFile")
      val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(path)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) =>
          logger.info(s"count = $count, status = $status")
          FilePart(partName, filename, contentType, path.toFile)
      }
  }


  /**
    * Uploads a multipart file as a POST request.
    *
    * @return
    */
  def uploadTrajectory = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>

    // collect form input if no error is produced
    val formInput: Option[UploadVSForm] = form.bindFromRequest.fold(
      errForm => {
        BadRequest("error with form")
        None
      },
      spec => Some(spec)
    )

    if (formInput.get.key == config.get[String]("data.uploadkey.traj")) {
      trackingDataRepo.insertIntoTrajectoriesList(formInput.get.infra, formInput.get.name, formInput.get.description)

      request.body.file("traj").get match {
        case FilePart(key, filename, contentType, file) => {
          if (contentType.get == "text/csv") {
            logger.info(s"key = ${key}, filename = ${filename}, contentType = ${contentType}, file = $file")
            val destDir: String = config.get[String]("data.upload.path") + "traj/"
            logger.info(s"size of uploaded file= ${Files.size(file.toPath)}")
            Files.move(file.toPath, Paths.get(destDir + formInput.get.infra + "__" + formInput.get.name + "__" + file.getName + ".csv"))
            logger.info(s"file moved to ${destDir + formInput.get.infra + "__" + formInput.get.name + "__" + file.getName} + .csv")
            Ok("File uploaded successfully !")
          } else {
            BadRequest("Wrong file type !")
          }
        }
        case _ => {
          logger.warn("File not uploaded !")
          BadRequest("File not uploaded !")
        }
      }
    } else {
      BadRequest("Key is wrong !")
    }
  }


  val formInfra = Form(
    mapping(
      "location" -> text,
      "description" -> text,
      "key" -> text
    )(UploadInfraForm.apply)(UploadInfraForm.unapply)
  )

  def uploadInfrastructure = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>

    // collect form input if no error is produced
    val formInput: Option[UploadInfraForm] = formInfra.bindFromRequest.fold(
      errForm => {
        BadRequest("error with form")
        None
      },
      spec => Some(spec)
    )

    if (formInput.get.key == config.get[String]("data.uploadkey.infra")) {
      trackingDataRepo.createInfraIfNotExisting(formInput.get.location, formInput.get.description)

      request.body.file("walls").get match {
        case FilePart(key, filename, contentType, file) => {
          if (contentType.get == "application/json") {
            logger.info(s"key = ${key}, filename = ${filename}, contentType = ${contentType}, file = $file")
            val destDir: String = config.get[String]("data.upload.path") + "infra/"
            logger.info(s"size of uploaded file= ${Files.size(file.toPath)}")
            Files.move(file.toPath, Paths.get(destDir + formInput.get.location + "__" + "walls__" + file.getName + ".json"))
            logger.info(s"file moved to ${destDir + formInput.get.location + "__" + "walls__" + file.getName}")
            Ok("File uploaded successfully !")
          } else {
            BadRequest("Wrong file type !")
          }
        }
        case _ => {
          logger.warn("File not uploaded !")
          BadRequest("File not uploaded !")
        }
      }

      request.body.file("graph").get match {
        case FilePart(key, filename, contentType, file) => {
          if (contentType.get == "application/json") {
            logger.info(s"key = ${key}, filename = ${filename}, contentType = ${contentType}, file = $file")
            val destDir: String = config.get[String]("data.upload.path") + "infra/"
            logger.info(s"size of uploaded file= ${Files.size(file.toPath)}")
            Files.move(file.toPath, Paths.get(destDir + formInput.get.location + "__" + "graph__" + file.getName + ".json"))
            logger.info(s"file moved to ${destDir + formInput.get.location + "__" + "graph__" + file.getName}")
            Ok("File uploaded successfully !")
          } else {
            BadRequest("Wrong file type !")
          }
        }
        case _ => {
          logger.warn("File not uploaded !")
          BadRequest("File not uploaded !")
        }
      }
    } else {
      BadRequest("Key is wrong !")
    }
  }
}
