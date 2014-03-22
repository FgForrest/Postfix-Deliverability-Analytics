package com.fg.mail.smtp.rest

import akka.actor.ActorRef
import akka.pattern.ask
import com.fg.mail.smtp._
import scala.concurrent._
import scala.util.control.Exception._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import java.io.{OutputStream, PrintWriter, StringWriter, ByteArrayOutputStream}
import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty}
import java.util.concurrent.TimeoutException
import scala.annotation.meta.{param, getter}
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import scala.Some
import com.fg.mail.smtp.notification.MailClient
import com.fg.mail.smtp.util.Profilable
import org.slf4j.LoggerFactory
import com.fg.mail.smtp.stats.GetCountStatus

/**
 * Handler of incoming Client's requests. It is responsible for serialization of data structures.
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 1:36 PM u_jli Exp $
 */
class Handler(indexer: ActorRef, counter: ActorRef, val o: Options) extends HttpHandler with Profilable {
  implicit val timeout = o.askTimeout

  val log = LoggerFactory.getLogger(getClass.getName)

  val agentVersion = Option(getClass.getPackage.getImplementationVersion).getOrElse("unknown")
  val writer = new ObjectMapper().registerModule(DefaultScalaModule).writer()
  val prettyWriter = new ObjectMapper().registerModule(DefaultScalaModule).writer(new DefaultPrettyPrinter())

  override def handle(exchange: HttpExchange) {
    def woQueryString(qs: String) = if (qs eq null) "without query string" else "with query string : " + qs

    val responseHeaders = exchange.getResponseHeaders
    val requestHeaders = exchange.getRequestHeaders
    implicit val clientVersion = Option(requestHeaders.getFirst("client-version")).getOrElse("unknown")
    val userAgent = Option(requestHeaders.getFirst("User-Agent")).getOrElse("unknown")
    val rc = ReqCtx(Map[String, String]("client-version" -> clientVersion, "User-Agent" -> userAgent))
    val out = new ByteArrayOutputStream()
    val isAgentClient = userAgent == "smtp-agent-http-client"
    val startAt = System.currentTimeMillis()
    exchange.getRequestMethod match {
      case "GET" =>
        val requestedUri = exchange.getRequestURI
        val queryString = requestedUri.getRawQuery
        val uri = requestedUri.getPath
        log.info(s"processing request URI : $uri ${woQueryString(queryString)}")
        profile(1500, "Request processing", uri + " with query string " + queryString) {
          Dispatcher.dispatch(uri, queryString)(rc) match {
            case Some(req: Request) =>
              log.info(s"processing query msg : $req")
              catching(classOf[Throwable])
                .either(Await.result(selectActor(req) ? req, timeout.duration).asInstanceOf[Option[Any]]) match {
                case Left(e) => e match {
                  case ex: TimeoutException =>
                    writeValue(out, null, false, Left(e), isAgentClient, startAt)
                    exchange.sendResponseHeaders(408, 0)
                    indexer ! RestartIndexer(s"handling request URI : $uri ${woQueryString(queryString)} failed on response timeout !", Option(ex))
                  case _ =>
                    writeValue(out, null, false, Left(e), isAgentClient, startAt)
                    exchange.sendResponseHeaders(500, 0)
                    MailClient.fail(s"handling request URI : $uri ${woQueryString(queryString)} failed !", Option(e), o)
                }
                case Right(Some(result)) =>
                  log.info(s"handling request URI : $uri ${woQueryString(queryString)} was successful")
                  result match {
                    case Html(html) =>
                      out.write(html.getBytes)
                      responseHeaders.set("Content-Type", ContentType.html)
                    case _ =>
                      writeValue(out, result, true, Right("OK"), isAgentClient, startAt)
                      responseHeaders.set("Content-Type", ContentType.json)
                  }
                  exchange.sendResponseHeaders(200, 0)
                case Right(None) =>
                  log.info(s"handling request URI : $uri ${woQueryString(queryString)} with empty result")
                  responseHeaders.set("Content-Type", ContentType.json)
                  writeValue(out, null, true, Right("OK"), isAgentClient, startAt)
                  exchange.sendResponseHeaders(200, 0)
              }
            case None =>
              log.error(s"request URI : $uri ${woQueryString(queryString)} doesn't exist !")
              responseHeaders.set("Content-Type", ContentType.json)
              writeValue(out, null, false, Right(s"Invalid request uri $uri or query string: $queryString"), isAgentClient, startAt)
              exchange.sendResponseHeaders(501, 0)
          }
        }
      case _ =>
        log.error(s"Agent requested by other http method than GET from ${exchange.getRemoteAddress}")
        writeValue(out, null, false, Right(s"Invalid http request method"), isAgentClient, startAt)
    }
    responseHeaders.set("Content-Length", out.size().toString)
    val body = exchange.getResponseBody
    out.writeTo(body)
    body.close()
    out.close()
    log.info("Response handled")
  }

  private def selectActor(req: Request): ActorRef = req match {
    case GetCountStatus(rc) => counter
    case _ => indexer
  }

  private def writeValue(out: OutputStream, result: Any, succeeded: Boolean, message: Either[Throwable, String], isAgentClient: Boolean, startedAt: Long)(implicit clientVersion: String) {
    def chooseWriter = if (isAgentClient) writer else prettyWriter
    val responseTime = System.currentTimeMillis() - startedAt
    def stackTrace(e: Throwable) = {
      val sw = new StringWriter()
      e.printStackTrace(new PrintWriter(sw))
      sw.toString
    }

    def buildMsg(message: Either[Throwable, String]) = message match {
      case Left(e) => s"${e.getClass.getSimpleName} : ${e.getMessage}\n ${stackTrace(e)}"
      case Right(m) => m
    }

    def getStatus(success: Boolean, msg: String, timeStamp: Long, version: String, responseTime: Long) = {
      if (clientVersion == "unknown")
        new ResponseStatus(success, msg, timeStamp)
      else
        new NewResponseStatus(success, msg, timeStamp, version, responseTime)
    }

    profile(2000, "Json serialization") {
      catching(classOf[Throwable])
      .either(chooseWriter.writeValue(out, new AgentResponse(result, getStatus(succeeded, buildMsg(message), System.currentTimeMillis(), agentVersion, responseTime)))) match {
        case Left(e) =>
          log.error(s"Json serialization failed, result :\n $result", e)
          writer.writeValue(out, new AgentResponse(null, getStatus(false, "Json serialization error", System.currentTimeMillis(), agentVersion, responseTime)))
        case Right(r) =>
          r
      }
    }
  }
}

class AgentResponse[T] @JsonCreator()(
                                       @(JsonProperty @getter @param)("result") val result: T,
                                       @(JsonProperty @getter @param)("status") val status: ResponseStatus
                                     )
class ResponseStatus @JsonCreator()(
                                     @(JsonProperty @getter @param)("succeeded") val succeeded: Boolean,
                                     @(JsonProperty @getter @param)("message") val message: String,
                                     @(JsonProperty @getter @param)("timeStamp") val timeStamp: Long
                                     )

//TODO az tady nebude zadny unknown v posledni dobe http://agent.massmail.fg.cz:1523/agent-status/ tak odstranit
class NewResponseStatus @JsonCreator() (
                                         val suc: Boolean,
                                         val msg: String,
                                         val ts: Long,
                                         @(JsonProperty @getter @param)("version") val version: String,
                                         @(JsonProperty @getter @param)("responseTime") val responseTime: Long) extends ResponseStatus(suc, msg, ts)
