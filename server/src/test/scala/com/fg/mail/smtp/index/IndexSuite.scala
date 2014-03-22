package com.fg.mail.smtp.index

import com.fg.mail.smtp._
import scala.concurrent.Await
import akka.pattern.ask
import com.fg.mail.smtp.rest.Dispatcher
import scala.Some
import com.fg.mail.smtp.Client
import com.fg.mail.smtp.IndexQuery
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.IterableView
import com.fg.mail.smtp.stats.{GetCountStatus, LastIndexingStatus}
import com.fg.mail.smtp.util.IndexFootprint

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 1:43 PM u_jli Exp $
 */
class IndexSuite extends TestSupport {

  val opt = loadOptions("application-test.conf").copy(
    rotatedPattern = """mail\.log\.\d{1}.*""",
    rotatedPatternFn = _.matches("""mail\.log\.\d{1}.*"""),
    logDir = testLogDir + "real/",
    tailedLogFileName = "mail.log",
    httpServerStart = false
  )

  describe("Indexer") {

    it("should create the same index after restart") {
      Await.result(indexer ? GetDisposableRecordsByClientId(rc), timeout.duration).asInstanceOf[Option[Map[String, Iterable[IndexRecord]]]] match {
        case Some(index) =>
          assert(index.keys.size === 3)
          assert(index("first-client-id").size === 13512)
          assert(index("second-client-id").size === 97)
          assert(index("third-client-id").size === 73)
        case _ =>
          fail("GetIndex should always return stuff")
      }

      indexer ! ReindexAgent(rc)

      Await.result(indexer ? GetDisposableRecordsByClientId(rc), timeout.duration).asInstanceOf[Option[Map[String, Iterable[IndexRecord]]]] match {
        case Some(map) =>
          map.keys should have size 3
          map("first-client-id").size should be (13512)
          map("second-client-id").size should be (97)
          map("third-client-id").size should be (73)
        case _ =>
          fail("GetIndex should always return stuff")
      }
    }

    describe("should provide status about") {

      it("general information") {
        Await.result(counter ? GetCountStatus(rc), timeout.duration).asInstanceOf[Option[LastIndexingStatus]] match {
          case Some(status) => println(status)
          case _ =>
            fail("StatusAgent should always return stuff")
        }
      }

      it("email address count") {
        Await.result(indexer ? RcptAddressCounts(rc), timeout.duration).asInstanceOf[Option[Map[String, Int]]] match {
          case Some(counts) =>
            counts.keys should have size 3
            counts("first-client-id") should be (7346)
            counts("second-client-id") should be (67)
            counts("third-client-id") should be (68)
          case _ =>
            fail("RcptAddressCounts should always return stuff")
        }
      }

      it("email addresses") {
        Await.result(indexer ? RcptAddresses(rc), timeout.duration).asInstanceOf[Option[Map[String, Set[String]]]] match {
          case Some(counts) =>
            counts.keys should have size 3
            counts("first-client-id").size should be (7346)
            counts("second-client-id").size should be (67)
            counts("third-client-id").size should be (68)
          case _ =>
            fail("RcptAddresses should always return stuff")
        }
      }

      it("unknown bounces") {
        Await.result(indexer ? UnknownBounces(rc), timeout.duration).asInstanceOf[Option[Map[String, Set[IndexRecord]]]] match {
          case Some(unknownBounces) =>
            unknownBounces.keys should have size 3
            unknownBounces("first-client-id").size should be (0)
            unknownBounces("second-client-id").size should be (0)
            unknownBounces("third-client-id").size should be (0)
          case _ =>
            fail("UnknownBounces should always return stuff")
        }
      }

      it("index footprint") {
        Await.result(indexer ? IndexMemoryFootprint(rc), timeout.duration).asInstanceOf[Option[IndexFootprint]] match {
          case Some(IndexFootprint(_,0,_,0)) =>
          case x =>
            fail(s"IndexFootprint $x is not correct")
        }
      }

      it("indexed log files") {
        Await.result(indexer ? IndexedLogFiles(rc), timeout.duration).asInstanceOf[Option[Array[String]]] match {
          case Some(logFiles) => assert(logFiles.deep === Array[String]("mail.log.2.gz").deep)
          case _ => fail("IndexFootprint should always return stuff")
        }
      }

      it("index age") {
        Await.result(indexer ? IndexAge(rc), timeout.duration).asInstanceOf[Option[Long]] match {
          case Some(timestamp) =>
            val theOldest = new SimpleDateFormat("yyyy MMM dd HH:mm:ss.SSS").format(new Date(timestamp))
            theOldest should be ("2013 Jun 10 06:50:26.123")
          case _ => fail("StatusAgent should always return stuff")
        }
      }
    }

    describe("should provide log entries by client id") {

      it("properly sorted") {
        var result = Await.result(indexer ? Client(IndexFilter("first-client-id", None, None, None), None), timeout.duration).asInstanceOf[Option[Iterable[IndexRecord]]].get
        recordsShouldNotContainSentOnes(result)
        result.size should be (6762)
        result.head.date < result.slice(20, 21).head.date should be(true)
        result = Await.result(indexer ? Client(IndexFilter("second-client-id", None, None, None), None), timeout.duration).asInstanceOf[Option[Iterable[IndexRecord]]].get
        result.size should be (26)
        result.head.date < result.slice(20, 21).head.date should be(true)
        result = Await.result(indexer ? Client(IndexFilter("third-client-id", None, None, None), None), timeout.duration).asInstanceOf[Option[Iterable[IndexRecord]]].get
        result.size should be (5)
        result.head.date < result.slice(2, 3).head.date should be(true)
      }

      it("with date constraint") {
        val from = toDate("2013 Jun 10 15:11:53.123")
        val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(Some(from.getTime), None, None, None)))
        val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Iterable[IndexRecord]]].get
        recordsShouldNotContainSentOnes(result)
        result.size should be (4152)
      }

      it("with last constraint") {
        val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, Some(true), None)))
        val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[IndexRecord]].get
        result should not be null
        result.date should be (toDate("2013 Jun 11 09:23:17.123").getTime)
      }

      it("with first constraint") {
        val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, Some(false), None)))
        val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[IndexRecord]].get
        result should not be null
        result.date should be (toDate("2013 Jun 10 08:00:00.123").getTime)
      }

      describe("grouped by") {

        describe("recipient email address") {

          it("properly sorted") {
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, None, Some(Dispatcher.groupBy_rcptEmail))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]].get
            result.keys.size should be (1273)
            val logEntries = result.head._2
            logEntries.isInstanceOf[IterableView[IndexRecord, Iterable[IndexRecord]]] should be (true)
            recordsShouldNotContainSentOnes(logEntries)

            val emailWithMultipleMessages = result.find(_._2.size > 8).head._2
            assert(emailWithMultipleMessages.head.date < emailWithMultipleMessages.tail.head.date, "IndexRecords under rcptEmail should be sorted by date")
          }

          it("with last constraint") {
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, Some(true), Some(Dispatcher.groupBy_rcptEmail))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, IndexRecord]]].get
            result should not be null
            result.keys.size should be (1273)
            result("w3r4h3sh@3x4fpi3.e0f").date should be (toDate("2013 Jun 11 09:08:38.123").getTime)
          }

          it("with first constraint") {
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, Some(false), Some(Dispatcher.groupBy_rcptEmail))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, IndexRecord]]].get
            result should not be null
            result.keys.size should be (1273)
            result("hr4ekyh3sh@3x4fpi3.e0f").date should be (toDate("2013 Jun 10 08:00:00.123").getTime)
          }

          it("with date constraint") {
            val since = toDate("2013 Jun 11 06:47:53.123")
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(Some(since.getTime), None, None, Some(Dispatcher.groupBy_rcptEmail))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, Set[IndexRecord]]]].get
            result should not be null
            result("w3r4h3sh@3x4fpi3.e0f") should have size 3
          }
        }

        describe("message id") {

          it("properly sorted") {
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, None, Some(Dispatcher.groupBy_msgId))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]].get

            result.keys.size should be (2214)
            val logEntries = result.head._2
            logEntries.isInstanceOf[IterableView[IndexRecord, Iterable[IndexRecord]]] should be (true)
            recordsShouldNotContainSentOnes(logEntries)
          }

          it("with last constraint") {
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, Some(true), Some(Dispatcher.groupBy_msgId))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, IndexRecord]]].get
            result should not be null
            result.keys.size should be (2214)
            result("msgId-44F6DC2C124XXXX").date should be (toDate("2013 Jun 11 09:08:38.123").getTime)
          }

          it("with first constraint") {
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(None, None, Some(false), Some(Dispatcher.groupBy_msgId))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, IndexRecord]]].get
            result should not be null
            result.keys.size should be (2214)
            result("msgId-4135B3FC069XXXX").date should be (toDate("2013 Jun 10 08:00:00.123").getTime)
          }

          it("with date constraint") {
            val since = toDate("2013 Jun 11 06:47:53.123")
            val client = Client(IndexFilter("first-client-id", None, None, None), Some(IndexQuery(Some(since.getTime), None, None, Some(Dispatcher.groupBy_msgId))))
            val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, Set[IndexRecord]]]].get
            result should not be null
            result("msgId-44F6DC2C124XXXX") should have size 3
          }
        }
      }

      describe("and email address") {

        it("properly sorted") {
          val result = Await.result(indexer ? Client(IndexFilter("first-client-id", Some("w3r4h3sh@3x4fpi3.e0f"), None, None), None), timeout.duration).asInstanceOf[Option[IterableView[IndexRecord, Iterable[IndexRecord]]]].get
          result should have size 19
          assert(result.head.date < result.tail.head.date)
        }

        it("with date constraint") {
          val since = toDate("2013 Jun 11 07:00:53.123")
          val mail = Client(IndexFilter("first-client-id", Some("w3r4h3sh@3x4fpi3.e0f"), None, None), Some(IndexQuery(Some(since.getTime), None, None, None)))
          val result = Await.result(indexer ? mail, timeout.duration).asInstanceOf[Option[IterableView[IndexRecord, Iterable[IndexRecord]]]].get
          result should have size 2
        }

        it("with last constraint") {
          val mail = Client(IndexFilter("first-client-id", Some("w3r4h3sh@3x4fpi3.e0f"), None, None), Some(IndexQuery(None, None, Some(true), None)))
          val result = Await.result(indexer ? mail, timeout.duration).asInstanceOf[Option[IndexRecord]].get
          result should not be null
          result.date should be (toDate("2013 Jun 11 09:08:38.123").getTime)
        }

        it("with first constraint") {
          val mail = Client(IndexFilter("first-client-id", Some("hr4ekyh3sh@3x4fpi3.e0f"), None, None), Some(IndexQuery(None, None, Some(false), None)))
          val result = Await.result(indexer ? mail, timeout.duration).asInstanceOf[Option[IndexRecord]].get
          result should not be null
          result.date should be (toDate("2013 Jun 10 08:00:00.123").getTime)
        }
      }

      describe("and message id") {

        it("properly sorted") {
          val result = Await.result(indexer ? Client(IndexFilter("first-client-id", None, Some("B90D53FC06DXXXX"), None), None), timeout.duration).asInstanceOf[Option[IterableView[IndexRecord, Iterable[IndexRecord]]]].get
          result should have size 9
          recordsShouldNotContainSentOnes(result)
          assert(result.head.date < result.tail.head.date)
        }

        it("with date constraint") {
          val from = toDate("2013 Jun 10 16:47:17.123")
          val msg = Client(IndexFilter("first-client-id", None, Some("B90D53FC06DXXXX"), None), Some(IndexQuery(Some(from.getTime), None, None, None)))
          val result = Await.result(indexer ? msg, timeout.duration).asInstanceOf[Option[IterableView[IndexRecord, Iterable[IndexRecord]]]].get
          result should have size 5
          recordsShouldNotContainSentOnes(result)
        }

        it("with last constraint") {
          val msg = Client(IndexFilter("first-client-id", None, Some("B90D53FC06DXXXX"), None), Some(IndexQuery(None, None, Some(true), None)))
          val result = Await.result(indexer ? msg, timeout.duration).asInstanceOf[Option[IndexRecord]].get
          result should not be null
          result.status should be ("sent")
          assert(result.state == 4)
        }

        it("with first constraint") {
          val msg = Client(IndexFilter("first-client-id", None, Some("4135B3FC069XXXX"), None), Some(IndexQuery(None, None, Some(false), None)))
          val result = Await.result(indexer ? msg, timeout.duration).asInstanceOf[Option[IndexRecord]].get
          result should not be null
          result.status should be ("bounced")
        }
      }
    }
  }

}
