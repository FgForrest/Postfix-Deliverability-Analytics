package com.fg.mail.smtp

import java.io.{File, BufferedReader}
import com.fg.mail.smtp.index.ClientIndexRecord

/**
 * A bunch of Akka messages
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 8:55 AM u_jli Exp $
 */
trait Request { def ctx: ReqCtx }
case class ReqCtx(underlying: Map[String, String])

case class ShutdownAgent(ctx: ReqCtx) extends Request
case class ReindexAgent(ctx: ReqCtx) extends Request
case class RestartAgent(ctx: ReqCtx) extends Request
case class RefreshBounceList(ctx: ReqCtx) extends Request
case class MemoryUsage(ctx: ReqCtx) extends Request
case class IndexMemoryFootprint(ctx: ReqCtx) extends Request
case class IndexedLogFiles(ctx: ReqCtx) extends Request
case class RcptAddressCounts(ctx: ReqCtx) extends Request
case class RcptAddresses(ctx: ReqCtx) extends Request
case class UnknownBounces(ctx: ReqCtx) extends Request
case class IndexAge(ctx: ReqCtx) extends Request
case class GetDisposableRecordsByClientId(ctx: ReqCtx) extends Request
case class GetQueue(ctx: ReqCtx) extends Request

case class Html(body: String)
object ContentType {
  val json = "application/json;charset=utf-8"
  val html = "text/html;charset=utf-8"
}

case class Client(filter: IndexFilter, query: Option[IndexQuery])(implicit reqCtx: ReqCtx) extends Request {
  def ctx: ReqCtx = reqCtx
}

case class IndexFilter(clientId: String, email: Option[String], queue: Option[String], message: Option[String])
case class IndexQuery(from: Option[Long], to: Option[Long], lastOrFirst: Option[Boolean], groupBy: Option[String])

sealed trait View
case class HomePage(ctx: ReqCtx) extends Request with View
case class QueryPage(ctx: ReqCtx) extends Request with View
case class ServerInfo(ctx: ReqCtx) extends Request with View

sealed trait Message
case class ShutSystemDown(why: String, ex: Option[Throwable] = None) extends Message
case class RestartSystem(why: String, ex: Option[Throwable] = None) extends Message
case object StartHttpServer extends Message

sealed trait Indexing
case class RestartIndexer(why: String, ex: Option[Throwable] = None) extends Message with Indexing
case object ParsingBackupFinished extends Message with Indexing
case class IndexTailedRecords(records: Iterable[ClientIndexRecord]) extends Message with Indexing
case class IndexBackupRecords(records: Iterable[ClientIndexRecord], file: File, digest: Option[String], last: Boolean) extends Message with Indexing
case object LogFileRotated extends Message with Indexing
case object IndexingTailFinished extends Message with Indexing

sealed trait Tailing
case class ReadLines(reader: BufferedReader, batchSize: Int) extends Message with Tailing
case class ReadBackup(digests: collection.mutable.Set[String]) extends Message with Tailing
case object StartTailing extends Message with Tailing

sealed trait Line
case class MessageLine(queueId: String, msgId: String) extends Line
case class ClientLine(queueId: String, clientId: String) extends Line
case class DeliveryAttemptLine(date: Long, queueId: String, recipient: String, status: String, info: String) extends Line
case class RemovedQueueLine(queueId: String) extends Line
case class ExpiredLine(date: Long, queueId: String, sender: String, status: String, info: String) extends Line

class RestartException extends Exception