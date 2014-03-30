package com.fg.mail.smtp.rest

import com.fg.mail.smtp.{StartHttpServer, Options}
import akka.actor.{ActorRef, ActorLogging, Actor}
import scala.util.control.Exception._
import java.net.{InetSocketAddress, BindException}
import com.sun.net.httpserver.{BasicAuthenticator, HttpServer}
import com.fg.mail.smtp.notification.MailClient
import java.util.concurrent.Executors

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/13/13 5:47 PM u_jli Exp $
 */
class Server(indexer: ActorRef, counter: ActorRef, o: Options) extends Actor with ActorLogging {

  val executorService = Executors.newFixedThreadPool(1)
  var server: HttpServer = _

  override def preStart() {
    catching(classOf[BindException])
      .either(HttpServer.create(new InetSocketAddress(o.httpServerPort), 0)) match {
      case Left(e) =>
        MailClient.fail("Smtp agent server probably started more than once, please stop currently running instance first. Shutting down !", Option(e), o)
        context.system.shutdown()
      case Right(s) =>
        s.setExecutor(executorService)
        val ctx = s.createContext("/", new Handler(indexer, counter, o))
        if (!o.httpServerAuth.isEmpty){
          ctx.setAuthenticator(new SmtpAgentBasicHttpAuthenticator(o))
        }
        server = s
    }
  }

  override def postStop() {
    try {
      log.info("stopping server")
      server.stop(1)
      executorService.shutdown()
      server = null
    } catch {
      case e: Throwable => log.warning("Failed to stop http server", e)
    }
  }

  override def receive = {
    case StartHttpServer =>
      catching(classOf[IllegalStateException])
        .either(server.start()) match {
        case Left(e) => log.info(s"Http server runs on port : ${o.httpServerPort}")
        case _ => log.info(s"Starting http server on port : ${o.httpServerPort}")
      }
  }

  class SmtpAgentBasicHttpAuthenticator(o: Options) extends BasicAuthenticator("smtp-agent") {
    def checkCredentials(user: String, pwd: String): Boolean = if (o.httpServerAuth.isEmpty) true else o.httpServerAuth.exists(_ == user + ":" + pwd)
  }
}

