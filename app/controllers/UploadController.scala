
package controllers

import java.io.File
import java.nio.file.{Files, Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl._
import akka.util.ByteString
import javax.inject.Inject
import play.api._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.streams._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}

import upload.UploadVSForm

/**
  * This controller handles a file upload.
  */
class UploadController @Inject() (cc:MessagesControllerComponents)
                               (implicit executionContext: ExecutionContext)
  extends MessagesAbstractController(cc) {

  private val logger = Logger(this.getClass)

  val form = Form(
    mapping(
      "location" -> text,
      "description" -> text
    )(UploadVSForm.apply)(UploadVSForm.unapply)
  )

  /**
    * Renders a start page.
    */
  def index = Action { implicit request =>
    Ok(views.html.index(form))
  }

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
    * A generic operation on the temporary file that deletes the temp file after completion.
    */
  private def operateOnTempFile(file: File): Unit = {
    if ( !Files.exists(Paths.get("/home/nicholas/tmp/test"))) {
      Files.createDirectory(Paths.get("/home/nicholas/tmp/test"))
    }

    val destDir: String = "/home/nicholas/tmp/test/"
    logger.info(s"size of uploaded file= ${Files.size(file.toPath)}")
    Files.move(file.toPath, Paths.get(destDir+file.getName))
    logger.info(s"file moved to ${"/home/nicholas/tmp/test/"+file.getName}")

    // TODO: call processing function and then insert results in DB
  }

  /**
    * Uploads a multipart file as a POST request.
    *
    * @return
    */
  def upload = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>

    // collect form input if no error is produced
    val formInput: Option[UploadVSForm] = form.bindFromRequest.fold(
      errForm => {
        BadRequest("error with form")
        None
      },
      spec => Some(spec)
    )
    //Ok(formInput.get.toString)

    request.body.file("myFile").foreach {
      case FilePart(key, filename, contentType, file) /*if (contentType.get =="application/json")*/ =>
        logger.info(s"key = ${key}, filename = ${filename}, contentType = ${contentType}, file = $file")
        operateOnTempFile(file)
    }

    Ok("Finished processing upload form: " + formInput.get.location + ", description: " + formInput.get.description)

  }

}
