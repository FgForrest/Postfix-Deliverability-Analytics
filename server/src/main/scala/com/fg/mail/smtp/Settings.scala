package com.fg.mail.smtp

import scala.concurrent.duration.DurationDouble
import org.slf4j.LoggerFactory
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import java.io.File
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
 * All customizable agent properties. Remaining properties that could be customizable (date format, log entry regular expressions) are static
 * because their change would require junit tests modification.
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 6:30 PM u_jli Exp $
 */
object Settings {
  val logger = LoggerFactory.getLogger(getClass.getName)

  def options() = {
    val fsResource = new File("../conf/application.conf")
    buildOptions(
      ConfigFactory.parseFile(
        if (fsResource.exists()) {
          logger.info("Loading configuration from filesystem : " + fsResource.getAbsolutePath)
          fsResource
        } else {
          logger.info("Loading configuration from classpath...")
          new File(getClass.getClassLoader.getResource("application.conf").toURI) // for testing environment
        }
      ).resolve()
    )
  }

  def buildOptions(c: Config) = {
    def sleep(ms: Long) = () => Thread.sleep(ms)
    Try(
      new Options(
        c.getBoolean("app.profiling.enabled"),
        c.getString("app.http-server.host"),
        c.getStringList("app.notification.recipients").asScala,
        Timeout(c.getInt("app.timing.request-timeout").second),
        c.getDouble("app.logs.max-file-size-to-index"),
        c.getInt("app.http-server.port"),
        (c.getString("app.bounce-regex-list.url"), c.getString("app.bounce-regex-list.auth")),
        c.getStringList("app.http-server.auth").asScala,
        c.getBoolean("app.http-server.start"),
        if (c.getString("app.logs.dir").endsWith("/")) c.getString("app.logs.dir") else c.getString("app.logs.dir") + "/",
        c.getString("app.logs.tailed-log-file"),
        c.getString("app.logs.rotated-file"),
        c.getString("app.logs.rotated-file-pattern"),
        _.matches(c.getString("app.logs.rotated-file-pattern")),
        c.getInt("app.logs.index-batch-size"),
        c.getString("app.db.dir"),
        c.getString("app.db.name"),
        c.getString("app.db.auth"),
        c.getInt("app.timing.re-open-tries"),
        sleep(c.getInt("app.timing.re-open-sleep")),
        sleep(c.getInt("app.timing.eof-wait-for-new-input-sleep"))
      )
    ) match {
      case Success(o) =>
        logger.info("Settings successfully parsed...")
        o
      case Failure(ex) =>
        logger.info(s"There is an error in settings", ex)
        throw ex
    }
  }
}

/**
 * @param profilingEnabled profiling has some overhead so you can turn it off
 * @param hostName http server is running on
 * @param notifRcpts email addresses that are to be sent notifications about warnings, errors, unknown bunces etc.
 * @param askTimeout how much time an actor thread is given for answering a request (eq. requesting indexer) before it fails
 * @param maxFileSizeToIndex maximum size of log files to index in Mega Bytes this property must be explicitly specified
 *                           because files must be processed by reverse alphabetical order (from the oldest to the newest)
 *                           and a log file might contain 10% or 90% of relevant log entries because postfix does not have to be dedicated necessarily.
 *                           Please note that this property doesn't see whether a file is zipped or not
 * @param httpServerPort port number of http server
 * @param bounceListUrlAndAuth remote or local location of bounce regex list (use file:// protocol if local, http:// if remote)
 * @param httpServerAuth basic base64 authentication credentials of remote server that serves bounce regex list file
 * @param httpServerStart it is possible to not start http server
 * @param logDir absolute path of directory that contains postfix log files
 * @param indexBatchSize how many relevant lines (client-id, message-id, sentOrDeferred, expired) is in a batch to be indexed
 * @param dbDir absolute path of directory that contains database files
 * @param dbName database name
 * @param dbAuth encryption key
 * @param reOpenTries number of attempts to re-open a log file after it is moved during log rotation
 * @param reOpenSleep how many miliseconds to wait between re-open tries
 * @param eofNewInputSleep how many miliseconds to wait after reaching log file EOF before reading next line
 * @param rotatedPatternFn backup file name matching constraint so that only backup files in a directory are read (regex for matching backup log files that has been rotated)
 * @param tailedLogFileName name of the log file that is being written to by postfix and tailed by agent
 */
case class Options(
            profilingEnabled: Boolean,
            hostName: String,
            notifRcpts: mutable.Buffer[String],
            askTimeout: Timeout,
            maxFileSizeToIndex: Double,
            httpServerPort: Int,
            bounceListUrlAndAuth: (String, String),
            httpServerAuth: mutable.Buffer[String],
            httpServerStart: Boolean,
            logDir: String,
            tailedLogFileName: String,
            rotatedFileName: String,
            rotatedPattern: String,
            rotatedPatternFn: String => Boolean,
            indexBatchSize: Int,
            dbDir: String,
            dbName: String,
            dbAuth: String,
            reOpenTries: Int,
            reOpenSleep: () => Unit,
            eofNewInputSleep: () => Unit
         )