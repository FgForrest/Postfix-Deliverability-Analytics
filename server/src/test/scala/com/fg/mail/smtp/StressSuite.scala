package com.fg.mail.smtp

import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.fg.mail.smtp.index.{DbManager, IndexRecord, QueueRecord}
import java.io.File
import akka.actor.{ActorRef, Props, ActorSystem}
import scala.collection.IterableView
import com.fg.mail.smtp.stats.{GetCountStatus, LastIndexingStatus}

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/17/13 5:21 PM u_jli Exp $
 */
class StressSuite extends TestSupport {

  val opt = loadOptions("application-test.conf")
  def dbDir = new File(opt.dbDir)

  override def providePersistence: DbManager = new DbManager(opt)

  override def afterAll() {
    dbDir.listFiles().filter(_.getName.startsWith(opt.dbName)).foreach(_.delete())
    super.afterAll()
  }

  describe("Actor system should") {

    describe("survive restart of supervisor") {

      it("without modifying index, queue or status") {
        val beforeIndex = Await.result(indexer ? GetDisposableRecordsByClientId(rc), timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]].get.map { case (k, v) => (k, v.force) }
        val beforeQueue = Await.result(tailer ? GetQueue(rc), timeout.duration).asInstanceOf[Option[Map[String, QueueRecord]]].get
        val beforeStatus = Await.result(counter ? GetCountStatus(rc), timeout.duration).asInstanceOf[Option[LastIndexingStatus]].get

        indexer ! RestartIndexer("OK")

        indexer = Await.result(supervisor ? GetIndexer, timeout.duration).asInstanceOf[ActorRef]
        counter = Await.result(indexer ? GetCouter, timeout.duration).asInstanceOf[ActorRef]
        tailer  = Await.result(indexer ? GetTailer, timeout.duration).asInstanceOf[ActorRef]
        val afterIndex = Await.result(indexer ? GetDisposableRecordsByClientId(rc), timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]].get.map { case (k, v) => (k, v.force) }
        val afterStatus = Await.result(counter ? GetCountStatus(rc), timeout.duration).asInstanceOf[Option[LastIndexingStatus]].get
        val afterQueue = Await.result(tailer ? GetQueue(rc), timeout.duration).asInstanceOf[Option[Map[String, QueueRecord]]].get

        assert(beforeIndex.equals(afterIndex))
        assert(beforeQueue.equals(afterQueue))
        assert(beforeStatus.indexedLogEntriesFromBackup === afterStatus.indexedLogEntriesFromBackup)
      }

    }

    describe("survive shutdown of supervisor") {

      it("and load into the exact same state") {
        val beforeIndex = Await.result(indexer ? GetDisposableRecordsByClientId(rc), timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]].get.map { case (k, v) => (k, v.force) }
        val beforeQueue = Await.result(tailer ? GetQueue(rc), timeout.duration).asInstanceOf[Option[Map[String, QueueRecord]]].get

        indexer ! ShutdownAgent(rc)
        system.awaitTermination(Duration.create(2, "s"))
        val newSystem = ActorSystem("newName")
        val supervisor = newSystem.actorOf(Props(new  Supervisor(opt, new DbManager(opt))), "supervisor")
        val newIndexer = Await.result(supervisor ? GetIndexer, timeout.duration).asInstanceOf[ActorRef]
        val newTailer = Await.result(newIndexer ? GetTailer, timeout.duration).asInstanceOf[ActorRef]
        val newCounter = Await.result(newIndexer ? GetCouter, timeout.duration).asInstanceOf[ActorRef]

        val afterIndex = Await.result(newIndexer ? GetDisposableRecordsByClientId(rc), timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]].get.map { case (k, v) => (k, v.force) }
        val afterQueue = Await.result(newTailer ? GetQueue(rc), timeout.duration).asInstanceOf[Option[Map[String, QueueRecord]]].get
        val afterStatus = Await.result(newCounter ? GetCountStatus(rc), timeout.duration).asInstanceOf[Option[LastIndexingStatus]].get

        assert(beforeIndex.equals(afterIndex))
        assert(beforeQueue.equals(afterQueue))
        assert(afterStatus.lastLineReceivedAt.longValue() === 0)

        newIndexer ! ShutdownAgent(rc)
        newSystem.awaitTermination(Duration.create(2, "s"))
      }

    }

  }
}
