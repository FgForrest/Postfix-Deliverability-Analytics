package com.fg.mail.smtp


import java.util.Date
import scala.collection.JavaConversions._
import com.fg.mail.smtp.client.{ConnectionConfig, JobCallback, SmtpAgentClient}
import com.fg.mail.smtp.client.request.filter._
import com.fg.mail.smtp.client.request.query.{ByTuple, By, BySingle}
import com.fg.mail.smtp.client.request.factory.{BatchReqFactory, AgentReq, SingleReqFactory}
import com.fg.mail.smtp.client.model.SmtpLogEntry
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.lang
import scala.collection.mutable

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/28/13 2:53 PM u_jli Exp $
 */
class IntegrationSuite extends TestSupport {

  val opt = loadOptions("application-test.conf")

  val client = new SmtpAgentClient(new ConnectionConfig("localhost", 6666, "dummy:1234", 4 * 1000, 6 * 1000))

  describe("Server should handle request for") {

    describe("status about") {

      it("general information") {
        val result = client.getJsonHttpClient.resolveWithoutDeserialization(new AgentReq(new AppendablePath("agent-status"), null))
        result should include ("Agent restarted")
      }

      it("email address count") {
        val counts = client.pullRcptAddressCounts()
        counts should not be 'empty
      }

      it("email addresses") {
        val addresses = client.pullRcptAddresses()
        addresses should not be 'empty
      }

      it("unknown bounces") {
        val unknownBounces = client.pullUnknownBounces()
        unknownBounces should not be 'empty
        unknownBounces.keys should have size 4
        unknownBounces("first-client-id").size should be (0)
        unknownBounces("second-client-id").size should be (0)
        unknownBounces("third-client-id").size should be (0)
        unknownBounces("test-mail-module").size should be (0)
      }

    }

    describe("UI") {

      it("HomePage") {
        client.getJsonHttpClient.resolveWithoutDeserialization(new AgentReq(new AppendablePath(""), null)) should include ("Vypnout")
      }

    }

    val reqFactory = new SingleReqFactory()

    describe("entries by client id should") {

      it("be properly sorted") {
        val entries = client.pull(reqFactory.forClientId("first-client-id").queryLess())
        entries should not be 'empty
        resultShouldBeJavaTreeSetOfLogEntries(entries)
        entries.pollFirst().getDate.after(entries.first().getDate) should be(true)
      }

      it("not contain entries with status sent") {
        val entries = client.pull(reqFactory.forClientId("first-client-id").queryLess())
        entriesShouldNotContainSentOnes(entries)
      }

      describe("be constrainable") {

        it("by date") {
          val entries = client.pull(reqFactory.forClientId("test-mail-module").forTimeConstraining(toDate("2013 Jun 10 15:58:52.123").getTime, null))
          entries should not be 'empty
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entries.pollFirst().getDate.after(entries.first().getDate) should be(true)
          entriesShouldNotContainSentOnes(entries)
        }

        it("by 0L date behaves as if it was not constraint") {
          val entriesConstrained = client.pull(reqFactory.forClientId("test-mail-module").forTimeConstraining(0, null))
          val entries = client.pull(reqFactory.forClientId("test-mail-module").queryLess())
          assert(entriesConstrained.equals(entries))
          entriesShouldNotContainSentOnes(entries)
        }

        it("for last entry only") {
          val entry = client.pull(reqFactory.forClientId("first-client-id").forLastOrFirstConstraining(null, null, true))
          entry should not be null
          entry.getStatus should be ("bounced")
          entry.getDate should be (toDate("2013 Jun 16 14:49:42.123"))
        }

        it("for first entry only") {
          val entry = client.pull(reqFactory.forClientId("first-client-id").forLastOrFirstConstraining(null, null, false))
          entry should not be null
          entry.getStatus should be ("deferred")
          entry.getDate should be (toDate("2013 Jun 10 15:34:42.123"))
        }

        it("for last entry only that does not exist should be null") {
          val entry = client.pull(reqFactory.forClientId("whatever-that-not-match").forLastOrFirstConstraining(null, null, true))
          entry should be (null)
        }

        it("for first entry only that does not exist should be null") {
          val entry = client.pull(reqFactory.forClientId("whatever-that-not-match").forLastOrFirstConstraining(null, null, false))
          entry should be (null)
        }
      }

      describe("be groupable by") {

        it("recipient email address") {
          val entriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forGrouping(null, null, new BySingle(By.Property.rcptEmail)))
          entriesByEmail should not be 'empty
          entriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val entries = entriesByEmail.values().iterator().next()
          assert(!entries.isEmpty)
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entriesByEmail.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("recipient email address constrained for last entry only") {
          val lastEntryByEmail = client.pull(reqFactory.forClientId("first-client-id").forConstrainedGrouping(null, null, true, new BySingle(By.Property.rcptEmail)))
          lastEntryByEmail should not be 'empty
          lastEntryByEmail.isInstanceOf[java.util.HashMap[String, SmtpLogEntry]] should be (true)
          lastEntryByEmail.values() foreach(
            _.getStatus != "sent"
            )
        }

        it("recipient email address constrained by date") {
          val entriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forGrouping(toDate("2013 Jun 10 15:58:52.123").getTime, null, new BySingle(By.Property.rcptEmail)))
          entriesByEmail should not be 'empty
          entriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val entries = entriesByEmail.values().iterator().next()
          assert(!entries.isEmpty)
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entriesByEmail.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("recipient email address constrained by 0L date behaves as if it was not constraint") {
          val entriesByEmailConstrained = client.pull(reqFactory.forClientId("first-client-id").forGrouping(new Date(0L).getTime, null, new BySingle(By.Property.rcptEmail)))
          val entriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forGrouping(null, null, new BySingle(By.Property.rcptEmail)))
          assert(entriesByEmail == entriesByEmailConstrained)
          entriesByEmailConstrained.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("queue id") {
          val entriesByQueueId = client.pull(reqFactory.forClientId("first-client-id").forGrouping(null, null, new BySingle(By.Property.queueId)))
          entriesByQueueId should not be 'empty
          entriesByQueueId.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val entries = entriesByQueueId.values().iterator().next()
          assert(!entries.isEmpty)
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entriesByQueueId.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("message id") {
          val entriesByMsgId = client.pull(reqFactory.forClientId("first-client-id").forGrouping(null, null, new BySingle(By.Property.msgId)))
          entriesByMsgId should not be 'empty
          entriesByMsgId.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val entries = entriesByMsgId.values().iterator().next()
          assert(!entries.isEmpty)
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entriesByMsgId.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("queue id constrained for last entry only") {
          val lastEntryByQueueId = client.pull(reqFactory.forClientId("first-client-id").forConstrainedGrouping(null, null, true, new BySingle(By.Property.queueId)))
          lastEntryByQueueId should not be 'empty
          lastEntryByQueueId.isInstanceOf[java.util.HashMap[String, SmtpLogEntry]] should be (true)
          lastEntryByQueueId.values() foreach(
            _.getStatus != "sent"
            )
        }

        it("message id constrained for last entry only") {
          val lastEntryByMsgId = client.pull(reqFactory.forClientId("first-client-id").forConstrainedGrouping(null, null, true, new BySingle(By.Property.msgId)))
          lastEntryByMsgId should not be 'empty
          lastEntryByMsgId.isInstanceOf[java.util.HashMap[String, SmtpLogEntry]] should be (true)
          lastEntryByMsgId.values() foreach(
            _.getStatus != "sent"
            )
        }

        it("queue id constrained by date") {
          val entriesByQueueId = client.pull(reqFactory.forClientId("first-client-id").forGrouping(toDate("2013 Jun 10 15:58:52.123").getTime, null, new BySingle(By.Property.queueId)))
          entriesByQueueId should not be 'empty
          entriesByQueueId.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val entries = entriesByQueueId.values().iterator().next()
          assert(!entries.isEmpty)
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entriesByQueueId.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("message id constrained by date") {
          val entriesByMsgId = client.pull(reqFactory.forClientId("first-client-id").forGrouping(toDate("2013 Jun 10 15:58:52.123").getTime, null, new BySingle(By.Property.msgId)))
          entriesByMsgId should not be 'empty
          entriesByMsgId.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val entries = entriesByMsgId.values().iterator().next()
          assert(!entries.isEmpty)
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entriesByMsgId.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("queue id constrained by 0L date behaves as if it was not constrained") {
          val entriesByQueueIdConstrained = client.pull(reqFactory.forClientId("first-client-id").forGrouping(new Date(0L).getTime, null, new BySingle(By.Property.queueId)))
          val entriesByQueueId = client.pull(reqFactory.forClientId("first-client-id").forGrouping(null, null, new BySingle(By.Property.queueId)))
          assert(entriesByQueueId == entriesByQueueIdConstrained)
          entriesByQueueIdConstrained.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("message id constrained by 0L date behaves as if it was not constrained") {
          val entriesByMsgIdConstrained = client.pull(reqFactory.forClientId("first-client-id").forGrouping(new Date(0L).getTime, null, new BySingle(By.Property.msgId)))
          val entriesByMsgId = client.pull(reqFactory.forClientId("first-client-id").forGrouping(null, null, new BySingle(By.Property.msgId)))
          assert(entriesByMsgId == entriesByMsgIdConstrained)
          entriesByMsgIdConstrained.values() foreach(
            entriesShouldNotContainSentOnes(_)
            )
        }

        it("both email and queue") {
          val entriesByEmail = client.pull(reqFactory.forClientId("second-client-id").forMultipleGrouping(null, null, new ByTuple(By.Property.queueId, By.Property.rcptEmail)))
          entriesByEmail should not be 'empty
          entriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val emailAddress = entriesByEmail.keySet().iterator().next()
          val entriesByQueueId = entriesByEmail.get(emailAddress)
          assert(!entriesByQueueId.isEmpty)
          val queueId = entriesByQueueId.keySet().iterator().next()
          val queueIdEntries = entriesByQueueId.get(queueId)
          resultShouldBeJavaTreeSetOfLogEntries(queueIdEntries)
        }

        it("both email and message") {
          val entriesByEmail = client.pull(reqFactory.forClientId("second-client-id").forMultipleGrouping(null, null, new ByTuple(By.Property.msgId, By.Property.rcptEmail)))
          entriesByEmail should not be 'empty
          entriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val emailAddress = entriesByEmail.keySet().iterator().next()
          val entriesByMsgId = entriesByEmail.get(emailAddress)
          assert(!entriesByMsgId.isEmpty)
          val msgId = entriesByMsgId.keySet().iterator().next()
          val queueIdEntries = entriesByMsgId.get(msgId)
          resultShouldBeJavaTreeSetOfLogEntries(queueIdEntries)
        }

        it("both email and queue constrained by date") {
          val entriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forMultipleGrouping(toDate("2013 Jun 10 15:58:52.123").getTime, null, new ByTuple(By.Property.queueId, By.Property.rcptEmail)))
          entriesByEmail should not be 'empty
          entriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val emailAddress = entriesByEmail.keySet().iterator().next()
          val entriesByQueueId = entriesByEmail.get(emailAddress)
          assert(!entriesByQueueId.isEmpty)
          val queueId = entriesByQueueId.keySet().iterator().next()
          val queueIdEntries = entriesByQueueId.get(queueId)
          resultShouldBeJavaTreeSetOfLogEntries(queueIdEntries)
        }

        it("both email and message constrained by date") {
          val entriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forMultipleGrouping(toDate("2013 Jun 10 15:58:52.123").getTime, null, new ByTuple(By.Property.msgId, By.Property.rcptEmail)))
          entriesByEmail should not be 'empty
          entriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val emailAddress = entriesByEmail.keySet().iterator().next()
          val entriesByMsgId = entriesByEmail.get(emailAddress)
          assert(!entriesByMsgId.isEmpty)
          val msgId = entriesByMsgId.keySet().iterator().next()
          val queueIdEntries = entriesByMsgId.get(msgId)
          resultShouldBeJavaTreeSetOfLogEntries(queueIdEntries)
        }

        it("both email and queue constrained by 0L date behaves as if it was not constraint") {
          val entriesByEmailConstrained = client.pull(reqFactory.forClientId("first-client-id").forMultipleGrouping(new Date(0L).getTime, null, new ByTuple(By.Property.queueId, By.Property.rcptEmail)))
          val entriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forMultipleGrouping(null, null, new ByTuple(By.Property.queueId, By.Property.rcptEmail)))
          assert(entriesByEmail == entriesByEmailConstrained)
        }

        it("both email and message constrained by 0L date behaves as if it was not constraint") {
          val entriesByEmailConstrained = client.pull(reqFactory.forClientId("first-client-id").forMultipleGrouping(new Date(0L).getTime, null, new ByTuple(By.Property.msgId, By.Property.rcptEmail)))
          val entriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forMultipleGrouping(null, null, new ByTuple(By.Property.msgId, By.Property.rcptEmail)))
          assert(entriesByEmail == entriesByEmailConstrained)
        }

        it("both email and queue constrained for last entry only") {
          val lastEntriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forConstraintMultipleGrouping(null, null, true, new ByTuple(By.Property.queueId, By.Property.rcptEmail)))
          lastEntriesByEmail should not be 'empty
          lastEntriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val emailAddress = lastEntriesByEmail.keySet().iterator().next()
          val lastEntriesByQueueId = lastEntriesByEmail.get(emailAddress)
          assert(!lastEntriesByQueueId.isEmpty)
          val queueId = lastEntriesByQueueId.keySet().iterator().next()
          val queueIdEntry = lastEntriesByQueueId.get(queueId)
          queueIdEntry.isInstanceOf[SmtpLogEntry] should be (true)
        }

        it("both email and message constrained for last entry only") {
          val lastEntriesByEmail = client.pull(reqFactory.forClientId("first-client-id").forConstraintMultipleGrouping(null, null, true, new ByTuple(By.Property.msgId, By.Property.rcptEmail)))
          lastEntriesByEmail should not be 'empty
          lastEntriesByEmail.isInstanceOf[java.util.HashMap[String, _]] should be (true)
          val emailAddress = lastEntriesByEmail.keySet().iterator().next()
          val lastEntriesByMsgId = lastEntriesByEmail.get(emailAddress)
          assert(!lastEntriesByMsgId.isEmpty)
          val msgId = lastEntriesByMsgId.keySet().iterator().next()
          val queueIdEntry = lastEntriesByMsgId.get(msgId)
          queueIdEntry.isInstanceOf[SmtpLogEntry] should be (true)
        }

      }

      describe("and recipient email address") {

        it("properly sorted") {
          val entries = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.rcptEmail, "azv-test@example.com")).queryLess())
          entries should not be 'empty
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entries.pollFirst().getDate.after(entries.first().getDate) should be(true)
        }

        it("constrained by date") {
          val entries = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.rcptEmail, "azv-test@example.com")).forTimeConstraining(toDate("2013 Jun 10 15:58:52.123").getTime, null))
          entries should not be 'empty
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entries.pollFirst().getDate.after(entries.first().getDate) should be(true)
        }

        it("constrained by 0L date behaves as if it was not constraint") {
          val entriesConstrained = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.rcptEmail, "azv-test@example.com")).forTimeConstraining(new Date(0L).getTime, null))
          val entries = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.rcptEmail, "azv-test@example.com")).queryLess())
          assert(entries == entriesConstrained)
        }

        it("constrained for last entry only") {
          val entry = client.pull(reqFactory.forClientIdAnd("first-client-id", new Eq(UrlPathPart.rcptEmail, "azv-test@example.com")).forLastOrFirstConstraining(null, null, true))
          entry should not be null
        }

      }

      describe("and queue id") {

        it("properly sorted") {
          val entries = client.pull(reqFactory.forClientIdAnd("first-client-id", new Eq(UrlPathPart.queueId, "3bYsCs20wBz37Dv")).queryLess())
          entries should not be 'empty
          resultShouldBeJavaTreeSetOfLogEntries(entries)
        }

        it("constrained by date") {
          val entries = client.pull(reqFactory.forClientIdAnd("first-client-id", new Eq(UrlPathPart.queueId, "3bYsCs20wBz37Dv")).forTimeConstraining(toDate("2013 Jun 10 15:58:52.123").getTime, null))
          entries should not be 'empty
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entries.pollFirst().getDate.after(entries.first().getDate) should be(true)
        }

        it("constrained by 0L date behaves as if it was not constraint") {
          val entriesConstrained = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.queueId, "3bYscs22wBz37Dv")).forTimeConstraining(new Date(0).getTime, null))
          val entries = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.queueId, "3bYscs22wBz37Dv")).queryLess())
          assert(entries == entriesConstrained)
        }

        it("constrained for last entry only") {
          val entry = client.pull(reqFactory.forClientIdAnd("first-client-id", new Eq(UrlPathPart.queueId, "3bYsCs20wBz37Dv")).forLastOrFirstConstraining(null, null, true))
          entry should not be null
        }

      }

      describe("and message id") {

        it("properly sorted") {
          val entries = client.pull(reqFactory.forClientIdAnd("first-client-id", new Eq(UrlPathPart.msgId, "msgId-3bYsCs20wBz37Dv")).queryLess())
          entries should not be 'empty
          resultShouldBeJavaTreeSetOfLogEntries(entries)
        }

        it("constrained by date") {
          val entries = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.msgId, "98494.321.654")).forTimeConstraining(toDate("2013 Jun 10 15:58:52.123").getTime, null))
          entries should not be 'empty
          resultShouldBeJavaTreeSetOfLogEntries(entries)
          entries.pollFirst().getDate.after(entries.first().getDate) should be(true)
        }

        it("constrained by 0L date behaves as if it was not constraint") {
          val entriesConstrained = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.queueId, "98494.321.654")).forTimeConstraining(new Date(0).getTime, null))
          val entries = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.queueId, "98494.321.654")).queryLess())
          assert(entries == entriesConstrained)
        }

        it("constrained for last entry only") {
          val entry = client.pull(reqFactory.forClientIdAnd("test-mail-module", new Eq(UrlPathPart.msgId, "98494.321.654")).forLastOrFirstConstraining(null, null, true))
          entry should not be null
        }
      }
    }
  }

  describe("Client and Server should support batch request that") {

    it("should split load to x jobs") {
      val format = new SimpleDateFormat("yyyy MMM dd HH:mm:ss.SSS")
      val from = toDate("2013 Jun 10 10:00:00.000")
      val to = toDate("2013 Jun 17 10:00:00.000")

      val batchReq = new BatchReqFactory(60 * 60 * 24, TimeUnit.SECONDS).forClientId("test-mail-module").forGrouping(from.getTime, to.getTime, new BySingle(By.Property.msgId))

      val result = mutable.Map[(String, String), java.util.Map[String, java.util.TreeSet[SmtpLogEntry]]]()
      val callback = new JobCallback[java.util.Map[String, java.util.TreeSet[SmtpLogEntry]]] {
        protected def execute(job: java.util.Map[String, java.util.TreeSet[SmtpLogEntry]], from: lang.Long, to: lang.Long) {
          result += (format.format(from), format.format(to)) -> job
        }
      }
      client.pull(batchReq, callback)

      result.keys should have size 7
      result(("2013 Jun 10 10:00:00.000", "2013 Jun 11 10:00:00.000")) should have size 1
      result(("2013 Jun 11 10:00:00.000", "2013 Jun 12 10:00:00.000")) should have size 0
      result(("2013 Jun 12 10:00:00.000", "2013 Jun 13 10:00:00.000")) should have size 0
      result(("2013 Jun 13 10:00:00.000", "2013 Jun 14 10:00:00.000")) should have size 0
      result(("2013 Jun 14 10:00:00.000", "2013 Jun 15 10:00:00.000")) should have size 0
      result(("2013 Jun 15 10:00:00.000", "2013 Jun 16 10:00:00.000")) should have size 1
      result(("2013 Jun 16 10:00:00.000", "2013 Jun 17 10:00:00.000")) should have size 1
    }

  }

  def resultShouldBeJavaTreeSetOfLogEntries(entries: Any) = entries match {
    case e: java.util.TreeSet[SmtpLogEntry] =>
    case _ => assert(false)
  }
}
