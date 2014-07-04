package com.fg.mail.smtp.tail

import akka.actor._
import java.io._
import com.fg.mail.smtp._
import scala.concurrent.{Future, ExecutionContext}
import java.util.concurrent._
import scala._
import com.fg.mail.smtp.parser.BounceListParser
import com.fg.mail.smtp.index._
import scala.util.matching.Regex
import com.fg.mail.smtp.util.{ParsingUtils, Profilable, Commons}
import com.fg.mail.smtp.notification.MailClient
import java.util.{Locale, Date}
import java.text.SimpleDateFormat
import com.fg.mail.smtp.stats._
import com.fg.mail.smtp.RefreshBounceList
import scala.util.Failure
import scala.Some
import com.fg.mail.smtp.ExpiredLine
import com.fg.mail.smtp.RemovedQueueLine
import com.fg.mail.smtp.Options
import com.fg.mail.smtp.ReadBackup
import com.fg.mail.smtp.IndexTailedRecords
import com.fg.mail.smtp.index.ClientIndexRecord
import com.fg.mail.smtp.index.IndexRecord
import scala.util.Success
import com.fg.mail.smtp.DeliveryAttemptLine
import com.fg.mail.smtp.RestartIndexer
import com.fg.mail.smtp.ReadLines
import com.fg.mail.smtp.MessageLine
import com.fg.mail.smtp.stats.CountIndexedCidLine
import com.fg.mail.smtp.index.QueueRecord
import com.fg.mail.smtp.stats.CountIndexedMidLine
import com.fg.mail.smtp.ClientLine
import com.fg.mail.smtp.ShutSystemDown
import com.fg.mail.smtp.IndexBackupRecords
import scala.collection.mutable.ArrayBuffer

/**
 * An akka actor tailing smtp log file. It is sending incoming log lines to Indexer actor for them to be indexed.
 * It is recursively sending a message with new line to itself so that it doesn't block for it to be able to listen to
 * messages from the outside.
 *
 * Tail works similar to unix tail utility. There are 3 essential ingredients :
 *
 * <ul>
 *    <li>TailingInputStream : FileInputStream returns -1 on EOF, this one returns -1 only when the underlying file is rotated<br>
 *      otherwise hitting EOF just makes recursively reading and sleeping
 *    <li>Rotation - infinite enumeration of input streams from the same underlying path to a file that may be rotated
 *    <li>{@link java.io.SequenceInputStream} : Used for continuous reading of input streams. It reads next one when it hits EOF of the previous one.<br>
 *      Providing it with Rotation enumeration where each next element is a new TailingInputStream makes it survive infinite number of file rotations
 * <ul>
 *
 * It also interprets provided lines, files or directory of smtp log to build index. It does so using a few regex patterns that identify relevant log lines.
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 5:25 PM u_jli Exp $
 */
class TailingReader(counter: ActorRef, dbManager: DbManager, val o: Options) extends Actor with ActorLogging with Profilable {

  var executorService: ExecutorService = _
  var prioritizedBounceList: scala.collection.mutable.TreeSet[(String, Long, Long, String, Regex)] = _
  var queue: Queue = _
  var restarted: Boolean = false

  lazy implicit val executionContext = ExecutionContext.fromExecutor(executorService)
  lazy implicit val timeout = o.askTimeout

  val dateFormat = new SimpleDateFormat("yyyy MMM dd HH:mm:ss.SSS", Locale.US)

  override def postRestart(reason: Throwable) {
    restarted = true
  }

  override def preStart() {
    log.info(" is starting")
    queue = Queue(dbManager.queueDb)
    executorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue[Runnable], Executors.defaultThreadFactory, new RejectedExecutionHandler{
      def rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
        log.warning("Tailer executor is forced to shut down, queued tasks are rejected !")
      }
    })
    refreshBounceList()
  }

  override def postStop() {
    log.info(" is stopping")
    executorService.shutdownNow()
  }

  override def receive = {

    case msg: Tailing => msg match {

      case ReadLines(reader, batchSize) =>
        Future {
          Option(List.fill(batchSize){reader.readLine})
        } onComplete {
          case Success(Some(lines)) =>
            val records = parseLines(lines, true, queue)
            if (!records.isEmpty) context.parent ! IndexTailedRecords(records)
            self ! ReadLines(reader, batchSize)
          case Success(None) =>
            reader.close()
            log.warning("Closing tail file input stream, cause tailing file disappeared or system is shutting down", None)
          case Failure(e) =>
            reader.close()
            context.parent ! RestartIndexer("Closing tail file input stream and restarting indexer due to exception", Option(e))
        }

      case ReadBackup(digests: collection.mutable.Set[String]) =>
        try {
          val logDir = new File(o.logDir)
          assert(logDir.exists(), s"Directory ${logDir.getAbsolutePath} doesn't exist !")
          val start = System.currentTimeMillis()
          val indexingBackup = parseAndIndexRotatedLogFiles(queue, logDir, digests, context.parent ! IndexBackupRecords(_, _, _, _))
          log.info(s"Backup logs parsed in ${System.currentTimeMillis() - start} ms")
          context.parent ! ParsingBackupFinished

          log.info(
            "Prioritized bounce list - how many times and in what order bounce messages were classified  :\n" +
            Commons.buildTable(
              List("type", "hit count", "default order (1 is the last)", "category"),
              prioritizedBounceList.toList.map( t => (t._1, t._2, t._3, t._4).productIterator.toList)
            )
          )

          logResultString

          if (!indexingBackup) self ! StartTailing
        } catch {
          case e: Throwable => context.actorSelection("../../../supervisor") ! ShutSystemDown("Fatal error during reading backup logs", Option(e))
        }

      case StartTailing =>
        val tailedLogFile = new File(o.logDir + o.tailedLogFileName)
        if (tailedLogFile.createNewFile()) {
          logger.warn(s"File to be tailed ${tailedLogFile.getName} doesn't exist, it was created...")
        }
        val length = tailedLogFile.length()
        val tailingBatchSize = if (length < (1024 * 1024)) 1 else length / (1024 * 1024)
        log.info("Preparing for tailing file " + tailedLogFile.getName + " with batch size " + tailingBatchSize + " MB")
        val reader = new BufferedReader(
                        new InputStreamReader(
                          new SequenceInputStream(
                            new Rotation( o, tailedLogFile )( () => context.parent ! IndexingTailFinished, () => context.parent ! LogFileRotated )
                          )
                        )
                      )
        self ! ReadLines(reader, tailingBatchSize.toInt)

    }

    case GetQueue(_) =>
      log.info("GetQueue request processing !")
      sender ! Option(queue.getQueue)

    case RefreshBounceList(_) =>
      log.info("Refreshing bounce list based on http request !")
      refreshBounceList()
      log.info("Bounce list successfully refreshed !")
  }

  private def parseLines(c: Iterable[String], fromTailing: Boolean, queue: Queue): Iterable[ClientIndexRecord] = {
    c.flatMap (
      recognizeLine
    ).flatMap (
      parseLine(_, fromTailing, queue)
    )
  }


  /**
   * When building index from log files in a directory, we want to first filter only relevant files,
   * collect the newest ones that fit in max-file-size-to-index limit and parse them in reverse alphabetical order
   *
   * @param d directory
   * @return Index build from log files in a directory
   */
  private def parseAndIndexRotatedLogFiles(queue: Queue, d: File, digests: collection.mutable.Set[String], batchCallback: (Iterable[ClientIndexRecord], File, Option[String], Boolean) => Any ): Boolean = {
    assert(d.isDirectory)

    def indexFileIfNeeded(f: File, isLast: Boolean): Boolean = {
      val md5 = Commons.digestFirstLine(f)
      if (!digests.contains(md5)) {
        indexLogFile(queue, f, md5, isLast, false)
        true
      } else {
        false
      }
    }

    def indexLogFile(queue: Queue, file: File, digest: String, isLast: Boolean, fromTailing: Boolean) {
      val source = Commons.getSource(file)
      try {
        val remaining = source.getLines().foldLeft(new ArrayBuffer[ClientIndexRecord](o.indexBatchSize))( (acc, line) => {
          recognizeLine(line).fold(acc) {
            parseLine(_, fromTailing, queue).fold(acc) {
              record =>
                if (acc.size < o.indexBatchSize) {
                  acc :+ record
                } else {
                  batchCallback(acc :+ record, file, None, isLast)
                  new ArrayBuffer(o.indexBatchSize)
                }
            }
          }
        })
        batchCallback(remaining, file, Some(digest), isLast)
      } finally {
        source.close()
        counter ! CountIndexedMidLine(midCount)
        counter ! CountIndexedCidLine(cidCount)
        counter ! CountRemovedLine(removedCount)
      }
    }

    val arbiter = ParsingUtils.splitFiles(d, o)
    arbiter.toIgnore.foreach { f =>
      log.info(s"file ${f.getName} is not going to be indexed because it is beyond limit")
    }
    if (arbiter.toIndex.isEmpty) {
      log.info("There are no backup files to index...")
      false
    } else {
      val indexedFiles = arbiter.toIndexInit.filter(indexFileIfNeeded(_, false))
      if (indexFileIfNeeded(arbiter.toIndex.last, true))
        true
      else
        !indexedFiles.isEmpty
    }
  }

  var cidCount: Long = 0
  var midCount: Long = 0
  var deliveryAttemptCount: Long = 0
  var removedCount: Long = 0

  val clientIdHashCode = "cid".hashCode.toString
  val compoundMsgIdClientId = s"<($clientIdHashCode\\..+)@(.+)>".r

  /**
   * In the sequence of smtp log entries it first registers a client id log entry and saves it to the Index's queueId/clientId lookup table.
   * Then either recipient log entry or expired log entry are indexed. Due to the recipient address absence in expired log entry,
   * it is necessary to look it up before creating a LogEntry.
   * @return index
   */
  private def parseLine(line: Line, fromTailing: Boolean, queue: Queue): Option[ClientIndexRecord] = {
    line match {
      case MessageLine(queueId: String, msgId: String) =>
        // if message-id header contains clientId information, use it, otherwise we have to retrieve it from ClientLine that follows
        val qr = msgId match {
                          case compoundMsgIdClientId(midFirstPart: String, clientId: String) => QueueRecord(midFirstPart, clientId, null, false)
                          case _ => QueueRecord(msgId, null, null, false)
                        }
        // create queue record
        queue.insert(queueId, qr) match {
                                    case None =>
                                      midCount += 1
                                      None
                                    case Some(QueueRecord(mid, cid, _, _)) =>
                                      log.warning(s"duplicated message log entry : $mid for queue id : $queueId and client id : $cid")
                                      None
                                  }
      case ClientLine(queueId: String, clientId: String) =>
        queue.lookup(queueId) match {
          case Some(t @ QueueRecord(mid: String, cid: String, _, _)) =>
            // now we're adding clientId information to message-id header, so I take it from there
            None
          case Some(t @ QueueRecord(mid: String, null, _, _)) =>
            // add client id to a queue record - this means, that clientId was not resolved from message-id header
            queue.insert(queueId, t.copy(cid = clientId))
            cidCount += 1
            None
          case None =>
            MailClient.info(s"missing queueId : $queueId for client id : $clientId in lookup table", o)
            None
        }
      case DeliveryAttemptLine(date, queueId, recipient, status, info) =>
        queue.lookup(queueId)
        match {
          case Some(t @ QueueRecord(msgId: String, clientId: String, rcpt, hasBeenDeferred)) =>
            if (status == "deferred" && ((rcpt eq null) || (!hasBeenDeferred))) {
              // from deferred attempts we have to set up QueueRecord with :
              //    recipient - for ExpiredLines, that do not contain this information
              //    hasBeenDeferred - for flagging possible 'sent' deliveries that were previously deferred - performance enhancement
              queue.insert(queueId, t.copy(rcpt = recipient, hasBeenDeferred = true))
            }
            val (state, errorMessage) = profile(100, "categorizing error", info) {
              ParsingUtils.resolveState(info, status, hasBeenDeferred, prioritizedBounceList)
            }
            deliveryAttemptCount += 1
            Some(ClientIndexRecord(clientId, IndexRecord(date, queueId, msgId, recipient, "unknown", status, info, state, errorMessage), fromTailing))

          case Some(QueueRecord(msgId: String, null, _, _)) =>
            //NOTE this queueId does not come from agent client because it would have client-id in request header, remove it from lookup table
            queue.invalidate(queueId)
            None
          case None => None
        }
      case ExpiredLine(date, queueId, sender, status, info) =>
        queue.lookup(queueId)
        match {
          case Some(QueueRecord(msgId: String, clientId: String, rcpt: String, true)) =>
            val (state, errorMessage) = ParsingUtils.resolveState(info, status, true, prioritizedBounceList)
            deliveryAttemptCount += 1
            Some(ClientIndexRecord(clientId, IndexRecord(date, queueId, msgId, rcpt, sender, status, info, state, errorMessage), fromTailing))

          case Some(QueueRecord(msgId: String, clientId: String, null, _)) =>
            log.error(s"expired line $line should have a queue record with rcpt email resolved from previously indexed deferred entry")
            None

          case Some(QueueRecord(msgId: String, clientId: String, _, false)) =>
            log.error(s"expired line $line should have a queue record with hasBeenDeferred set to 'true'")
            None

          case Some(QueueRecord(msgId: String, null, _, _)) =>
            //NOTE this queueId does not come from agent client that would have client-id in request header, remove it from lookup table
            queue.invalidate(queueId)
            None
          case None => None
        }
      case RemovedQueueLine(queueId: String) =>
        //NOTE postfix keeps a queue of messages. Message is removed from this queue if it is successfully sent, bounced or expired
        queue.invalidate(queueId)
        removedCount += 1
        None
      case _ =>
        throw new IllegalStateException("Attempt to index a Line of unknown type : " + line)
    }
  }

  private def recognizeLine(line: String): Option[Line] = {
    def toDate(d: String): Date = { dateFormat.parse(d) }
    line match {
      case ParsingUtils.deliveryAttempt(date, queueId, recipient, status, info) => Some(DeliveryAttemptLine(toDate(date).getTime, queueId, recipient, status, info))
      case ParsingUtils.midRegex(queueId: String, msgId: String) => Some(MessageLine(queueId, msgId))
      case ParsingUtils.cidRegex(queueId: String, clientId: String) => Some(ClientLine(queueId, clientId))
      case ParsingUtils.expiredRegex(date, queueId, sender, status, info) => Some(ExpiredLine(toDate(date).getTime, queueId, sender, status, info))
      case ParsingUtils.removedQueueRegex(queueId: String) => Some(RemovedQueueLine(queueId))
      case _ => None
    }
  }

  private def refreshBounceList() =
    prioritizedBounceList = new BounceListParser().parse(o.bounceListUrlAndAuth).fold (
      error => {
        val errorMsg = "Unable to download or parse bounce list from remote server due to : " + error
        MailClient.info(errorMsg, o)
        prioritizedBounceList
      },
      newBounceMap => newBounceMap
    )

}