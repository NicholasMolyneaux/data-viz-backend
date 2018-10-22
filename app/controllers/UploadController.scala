
package controllers

import java.io.File
import java.nio.file.{Files, Path, Paths}

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}


/**
  * This controller handles a file upload.
  */
@Singleton
class HomeController @Inject() (cc:MessagesControllerComponents)
                               (implicit executionContext: ExecutionContext)
  extends MessagesAbstractController(cc) {

  private val logger = Logger(this.getClass)

  val form = Form(
    mapping(
      "name" -> text,
      "location" -> text,
      "description" -> text
    )(UploadVSForms.apply)(UploadVSForms.unapply)
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
    Files.createDirectory(Paths.get("/home/nicholas/tmp/test"))

    val destDir: String = "/home/nicholas/tmp/"
    logger.info(s"size of uploaded file= ${Files.size(file.toPath)}")
    Files.mv(file.toPath, Files.getPath(destDir+file.getName))
    logger.info(s"file moved to ${"/home/nicholas/tmp/"+file.getName}")

    // TODO: call processing function and then insert results in DB
  }

  /**
    * Uploads a multipart file as a POST request.
    *
    * @return
    */
  def upload = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>
    val fileOption = request.body.file("name").map {
      case FilePart(key, filename, contentType, file) if (contentType.get =="application/json") =>
        logger.info(s"key = ${key}, filename = ${filename}, contentType = ${contentType}, file = $file")
        operateOnTempFile(file)
    }

    Ok(s"file size = ${fileOption.getOrElse("no file")}")
  }

}
