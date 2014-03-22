package com.fg.mail.smtp.parser

import scala.collection.immutable.TreeSet
import java.io.File
import com.fg.mail.smtp._

import akka.pattern.ask

import scala.io.Source
import com.fg.mail.smtp.util.{ParsingUtils, Commons}
import scala.concurrent.Await
import com.fg.mail.smtp.index.IndexRecord
import scala.collection.IterableView

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 11:03 PM u_jli Exp $
 */
class LogParserSuite extends TestSupport {

  def arbiterLogDir = new File(getClass.getClassLoader.getResource("META-INF/logs/arbiter").toURI)

  val opt = loadOptions("application-test.conf").copy(httpServerStart = false)

  describe("parser should") {

    it("group messages over multiple log files from directory") {

      Await.result(indexer ? GetDisposableRecordsByClientId(rc), timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]] match {
        case Some(i) =>
          assert(i.keys.size === 4)
          val test= i("test-mail-module").toList
          val first = i("first-client-id").toList
          val second = i("second-client-id").toList
          val third = i("third-client-id").toList
          assert(test.size === 16)
          assert(first.size === 9)
          assert(second.size === 9)
          assert(third.size === 9)
          val records = test ++ first ++ second ++ third
          assert(records.size === 9 * 3 + 16)
          val rcptLessRecords = records.foldLeft(0) { case (acc: Int, r) => if (Option(r.rcptEmail).isEmpty) acc+1 else acc }
          val rcptFullRecords = records.foldLeft(0) { case (acc: Int, r) => if (Option(r.rcptEmail).isDefined) acc+1 else acc }
          assert(rcptLessRecords === 0)
          assert(rcptFullRecords === 9 * 3 + 16)

          noMsgHasEmptyValue(records)
        case _ =>
          fail("GetIndex should always return stuff")
      }
    }

    it("split backup files into groups for indexing and storing") {
      //65 bytes => index 2 and backup 2 files
      val arbiter = ParsingUtils.splitFiles(arbiterLogDir, Settings.options().copy(maxFileSizeToIndex = 0.000065D))
      assert(arbiter.remainingSize < 0)
      assert(arbiter.toIndex.size === 2)
      assert(arbiter.toIgnore.size === 2)

      val indexed = arbiter.toIndex
      val backup = arbiter.toIgnore

      val expectedIndexed = TreeSet[String]("mail.log.2", "mail.log.1")
      val expectedBackup = TreeSet[String]("mail.log.4", "mail.log.3")

      assert(indexed.map(f => f.getName).subsetOf(expectedIndexed))
      assert(backup.map(f => f.getName).subsetOf(expectedBackup))

      assert(indexed.head.getName === "mail.log.2")
      assert(backup.head.getName === "mail.log.4")
    }

    it("digest files always with the same m5d") {

      def digest(f: File) = Commons.digestFirstLine(f)

      val indigestibles = new File(getClass.getClassLoader.getResource("META-INF/logs/real").toURI).listFiles().map(f => (digest(f), f)).filterNot(tuple => tuple._1 == digest(tuple._2)).map(_._2)

      assert(indigestibles.size === 0)

    }
  }

  def noMsgHasEmptyValue(index: Iterable[IndexRecord])  {
    val emptyRecords = index.filterNot {
          case IndexRecord(date: Long, queueId: String, msgId: String, rcptEmail: String, senderEmail: String, status: String, info: String, state: Int, stateInfo: String) => true
          case _ => false
        }
    assert(emptyRecords.isEmpty)
  }
}