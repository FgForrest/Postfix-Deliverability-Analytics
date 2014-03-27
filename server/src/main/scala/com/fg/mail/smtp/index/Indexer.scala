package com.fg.mail.smtp.index

import akka.actor._
import com.fg.mail.smtp._
import com.fg.mail.smtp.tail.TailingReader
import com.fg.mail.smtp.notification.MailClient
import akka.pattern.ask
import com.fg.mail.smtp.rest.{Controller, Server}
import com.fg.mail.smtp.util.{ServerInfoService, Profilable}
import java.io.File
import akka.dispatch.{BoundedDequeBasedMessageQueueSemantics, RequiresMessageQueue}
import com.fg.mail.smtp.stats.{LastIndexingStatus, GetCountStatus}
import com.fg.mail.smtp.rest.Dispatcher._
import com.fg.mail.smtp.stats.CountClientRequest
import com.fg.mail.smtp.RcptAddresses
import com.fg.mail.smtp.RestartAgent
import com.fg.mail.smtp.ShutdownAgent
import com.fg.mail.smtp.UnknownBounces
import com.fg.mail.smtp.RefreshBounceList
import com.fg.mail.smtp.IndexQuery
import scala.Some
import com.fg.mail.smtp.IndexAge
import com.fg.mail.smtp.Options
import com.fg.mail.smtp.ReadBackup
import com.fg.mail.smtp.IndexTailedRecords
import com.fg.mail.smtp.Client
import com.fg.mail.smtp.Html
import com.fg.mail.smtp.MemoryUsage
import com.fg.mail.smtp.IndexedLogFiles
import com.fg.mail.smtp.RestartIndexer
import com.fg.mail.smtp.GetDisposableRecordsByClientId
import com.fg.mail.smtp.IndexMemoryFootprint
import com.fg.mail.smtp.IndexFilter
import com.fg.mail.smtp.ShutSystemDown
import com.fg.mail.smtp.IndexBackupRecords
import com.fg.mail.smtp.RcptAddressCounts
import com.fg.mail.smtp.ReindexAgent
import scala.collection.immutable.HashSet

/**
 * An Akka actor responsible for indexing back up log files and listening to Tailer actor for upcoming log entries to be indexed
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 5:29 PM u_jli Exp $
 */
class Indexer(counter: ActorRef, dbManager: DbManager, val o: Options) extends Actor with UnrestrictedStash with RequiresMessageQueue[BoundedDequeBasedMessageQueueSemantics] with ActorLogging with Profilable {

  var timer: Long = System.currentTimeMillis()
  val multipleGroupingRegex = """([a-zA-z]{2,10})-and-([a-zA-z]{2,10})""".r
  val serverInfoService: ServerInfoService = new ServerInfoService(o)
  val controller: Controller = new Controller(serverInfoService, o)
  var tailer: ActorRef = _
  var index: Index = _
  var digestor: Digestor = _

  import context.dispatcher
  lazy implicit val timeout = o.askTimeout

  override def preStart() {
    log.info(" is starting")
    index = Index(dbManager.indexDb, "records")
    digestor = Digestor(dbManager.indexDb, "digests", o)
    tailer = context.actorOf(Props(new TailingReader(counter, dbManager, o)), "tailer")
    tailer ! ReadBackup(digestor.getDigests)
  }

  override def postStop() {
    log.info(" is stopping")
    dbManager.commit()
  }

  def receive = indexing orElse stashing

  def indexing: Receive = {
    case m: Indexing => m match {

      case IndexTailedRecords(records) =>
        indexRecords(records, index)

      case IndexBackupRecords(records, file, digest, isLast) =>
        indexRecords(records, index)
        digest match {
          case Some(md5) =>
            val lasted = System.currentTimeMillis() - timer
            timer = System.currentTimeMillis()
            log.info(s"Index of file ${file.getName} successfully persisted in $lasted ms")
            digestor.store(md5, file.getName)
            if (isLast) tailer ! StartTailing
          case _ =>
        }
        dbManager.commit()

      case ParsingBackupFinished =>
        timer = System.currentTimeMillis()
        logResultString
        dbManager.logResultString
        if (index.isEmpty)
          log.warning("No backup files were parsed !")

      case IndexingTailFinished =>
        log.info("Indexing of current log file finished, tailing initialized")
        if (o.httpServerStart) {
          log.info("Creating http server...")
          context.actorOf(Props(new Server(self, counter, o)), "server")
          context.child("server").get ! StartHttpServer
        }
        context.become(combining)
        unstashAll()
        log.info("Committing db transaction")
        dbManager.commit()

      case LogFileRotated =>
        log.info(s"Log file successfully rotated")
        digestor.store(new File(o.logDir + o.rotatedFileName))
        dbManager.commit()

      case RestartIndexer(why, ex) =>
        ex match {
          case Some(e) => counter ? GetCountStatus onSuccess {
            case s: LastIndexingStatus => MailClient.fail(why, Option(e), o, s.toString)
          }
          case None => counter ? GetCountStatus onSuccess {
            case s: LastIndexingStatus => MailClient.fail(why, None, o, s.toString)
          }
        }
        throw new RestartException

    }
  }

  def requesting: Receive = {
    case r: Request => r match {

      case c@Client(filter, q) =>
        log.info(s"Client request for filter $filter and query $q processing !")
        counter ! CountClientRequest(c)
        sender ! getEntries(index, filter, q)

      case GetDisposableRecordsByClientId(_) =>
        log.info("GetIndex request processing !")
        sender ! Option(index.getAsMapWithDisposableValues(Interval(None,None)))

      case hr: View =>
        sender ! Some(Html(controller.dispatch(hr, index)))

      case ReindexAgent(_) | RestartAgent(_) =>
        sender ! Some("Agent is restarting and reindexing, it might take a while !")
        self ! RestartIndexer("Reindexing agent based on http request !")

      case RefreshBounceList(rc) =>
        sender ! Some("Agent is parsing and reloading bounce list, it might take a while !")
        tailer ! RefreshBounceList(rc)

      case ShutdownAgent(_) =>
        sender ! Some("OK")
        Thread.sleep(300)
        context.parent ! ShutSystemDown("Shutting agent down based on http request !")

      /* reading operations */

      case MemoryUsage(_) =>
        log.info("MemoryUsage request processing !")
        sender ! Option(serverInfoService.getMemoryUsage)

      case IndexMemoryFootprint(_) =>
        log.info("IndexMemoryFootprint request processing !")
        sender ! Option(serverInfoService.getIndexFootprint(dbManager, index))

      case IndexedLogFiles(_) =>
        log.info("IndexedLogFiles request processing !")
        sender ! Option(digestor.getDigestedFiles.map(_.getName))

      case RcptAddressCounts(_) =>
        log.info("RcptAddressCounts request processing !")
        sender ! getRecipientAddressCounts(index)

      case IndexAge(_) =>
        log.info("IndexAge request processing !")
        sender ! getIndexAge(index)

      case UnknownBounces(_) =>
        log.info("UnknownBounces request processing !")
        sender ! getUnknownBounces(index)

      case RcptAddresses(_) =>
        log.info("RcptAddresses request processing !")
        sender ! getRecipientAddresses(index)

    }
  }

  def combining: Receive = indexing orElse requesting

  def stashing: Receive = {
    case _ =>
      stash()
  }

  private def getRecipientAddressCounts(index: Index) =
    Option(
      profile(1000, "Getting rcpt address counts") {
        index.getAsMapWithDisposableValues(Interval(None,None)).mapValues(records => records.foldLeft(HashSet[String]()) ((acc, r) => if (r.rcptEmail ne null) acc + r.rcptEmail else acc).size)
      }
    )

  private def getRecipientAddresses(index: Index) =
    Option(
      profile(1000, "Getting rcpt addresses") {
        index.getAsMapWithDisposableValues(Interval(None,None)).mapValues(col => HashSet[String]() ++ col.map(_.rcptEmail))
      }
    )

  private def getIndexAge(index: Index): Option[Long] =
    Option(if(index.records.iterator().hasNext) index.records.iterator().next().b else System.currentTimeMillis())

  private def getUnknownBounces(index: Index) = profile(600, "Getting unknown bounces") {
    Option(index.getAsMapWithDisposableValues(Interval(None,None)).mapValues(col => col.filter(_.state == 2)))
  }

  private def getEntries(i: Index, f: IndexFilter, q: Option[IndexQuery]) = profile(1000, "Getting entries") {
    f match {
      case IndexFilter(clientId, None, None, None) =>
        filterIndex(i, clientId, q, (r: IndexRecord) => r.state != 3)
      case IndexFilter(clientId, Some(email), None, None) =>
        filterIndex(i, clientId, q, (r: IndexRecord) => email == r.rcptEmail && r.state != 3)
      case IndexFilter(clientId, None, Some(queueId), None) =>
        filterIndex(i, clientId, q, (r: IndexRecord) => queueId == r.queueId && r.state != 3)
      case IndexFilter(clientId, None, None, Some(msgId)) =>
        filterIndex(i, clientId, q, (r: IndexRecord) => msgId == r.msgId && r.state != 3)
    }
  }

  private def indexRecords(r: Iterable[ClientIndexRecord], index: Index) = profile(500, s"Indexing records") {
    r.foreach(cir => index.addRecord(cir))
  }

  private def filterIndex(index: Index, clientId: String, q: Option[IndexQuery], filter: (IndexRecord) => Boolean): Option[Any] = {
    log.info(s"building result for query string : $q")
    q match {
      case Some(query) =>
        query match {
          case IndexQuery(f, t, None, None) =>
            log.info(s"building result for query string : $q")
            Some(index.getRecordsFor(clientId, Interval(f,t)).filter(filter))

          case IndexQuery(f, t, None, Some(gb)) =>
            gb match {
              case multipleGroupingRegex(outer, inner) =>
                Some(index.getRecordsFor(clientId, Interval(f,t)).filter(filter).groupBy(property(inner)).mapValues(_.groupBy(property(outer))))
              case _ =>
                Some(index.getRecordsFor(clientId, Interval(f,t)).filter(filter).groupBy(property(gb)))
            }

          case IndexQuery(f, t, Some(l), None) =>
            Some(index.getRecordsFor(clientId, Interval(f, t), l).find(filter).getOrElse(null))

          case IndexQuery(f, t, Some(l), Some(gb)) =>
            gb match {
              case multipleGroupingRegex(outer, inner) =>
                Some(index.getRecordsFor(clientId, Interval(f,t), l).filter(filter).groupBy(property(inner)).mapValues(all => all.groupBy(property(outer)).mapValues(_.headOption.getOrElse(null))))
              case _ =>
                Some(index.getRecordsFor(clientId, Interval(f,t), l).filter(filter).groupBy(property(gb)).mapValues(_.headOption.getOrElse(null)))
            }

          case _ =>
            log.warning(s"Invalid request query : $q, returning None")
            None
        }
      case None =>
        log.warning(s"building data structure for request without query")
        Some(index.getRecordsFor(clientId, Interval(None,None)).filter(filter))
    }
  }

  private def property(p: String): IndexRecord => String = {
    p match {
      case `groupBy_rcptEmail` => (e: IndexRecord) => e.rcptEmail
      case `groupBy_queueId` => (e: IndexRecord) => e.queueId
      case `groupBy_msgId` => (e: IndexRecord) => e.msgId
    }
  }

}
