package com.fg.mail.smtp.notification

import javax.mail._
import java.util.Properties
import javax.mail.internet.InternetAddress
import com.sun.mail.smtp.SMTPMessage
import javax.mail.Message.RecipientType.TO
import org.slf4j.LoggerFactory
import scala.util.control.Exception._
import java.io.{PrintWriter, StringWriter}
import com.fg.mail.smtp.Options

/**
 * Serves only for notification emails when something goes wrong
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 2:36 PM u_jli Exp $
 */
object MailClient {
  val log = LoggerFactory.getLogger("MailClient")

  val smtpHost = "localhost"
  val smtpPort = "25"

  val props = new Properties()
  props.put("mail.smtp.host", smtpHost)
  props.put("mail.smtp.port", smtpPort)

  def send(subject: String, text: String, recipients: List[String]) {
    log.warn(s"Mailing notification to $recipients\n subject: $subject\n text: $text")

    this.synchronized {
      recipients.foreach( rcpt => {
          val message = new SMTPMessage(Session.getInstance(props))
          message.setFrom(new InternetAddress("liska@fg.cz"))
          message.setRecipients(TO, rcpt)
          message.setSubject(subject)
          message.setText(text)
          catching(classOf[Throwable]).either(Transport.send(message)) match {
              case Left(e) => log.warn(s"Unable to connect to mail server : $smtpHost:$smtpPort, notification not sent", e)
              case _ => log.info("Notification sent")
            }
        }
      )
    }
  }

  def fail(problem: String, e: Option[Throwable], o: Options, status: String = "") {
    val stackTrace = {
      e match {
        case Some(ex) =>
          val sw = new StringWriter()
          ex.printStackTrace(new PrintWriter(sw))
          sw.toString
        case _ => ""
      }
    }
    val msg = s"host: ${o.hostName} - $problem ${e.fold(" : ")(_.getMessage)}'"
    log.warn(s"$msg \n $stackTrace")
    if (System.getProperty("agentProductionMode") ne null)
      send(msg, s"$status \n $stackTrace", o.notifRcpts.toList)
  }

  def info(problem: String, o: Options, status: String = "") {
    log.warn(problem)
    if (System.getProperty("agentProductionMode") ne null)
      send(s"host: ${o.hostName} - $problem", status, o.notifRcpts.toList)
  }

}
