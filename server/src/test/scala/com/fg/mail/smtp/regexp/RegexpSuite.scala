package com.fg.mail.smtp.regexp

import org.scalatest.{Matchers, FunSuite}
import scala.collection.immutable.HashSet
import scala.io.Source

import com.fg.mail.smtp.Settings
import com.fg.mail.smtp.util.ParsingUtils

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 9:36 PM u_jli Exp $
 */
class RegexpSuite extends FunSuite with Matchers {

  val midLine = "2013 Jun 16 16:05:07.123 gds39d postfix/cleanup[26547]: 3bZLDk1V50z37Dv: message-id=whatever"
  val cidLine = "2013 Jun 16 16:05:07.123 gds39d postfix/cleanup[26547]: 3bZLDk1V50z37Dv: info: header client-id: test-mail-module from localhost[127.0.0.1]; from=<liska@fg.cz> to=<liska@fg.cz> proto=ESMTP helo=<jlinb>"
  val expiredLine = "2013 Jun 15 16:28:52.123 gds39d postfix/qmgr[6273]: 3bYscs22wBz37Dv: from=<no-reply@directmail.fg.cz>, status=expired, returned to sender"
  val recipientLine = "2013 Jun 16 16:05:07.123 gds39d postfix/smtp[20945]: 3bZLDk1V50z37Dv: to=<liska@fg.cz>, relay=hermes.fg.cz[193.86.74.5]:25, delay=0.16, delays=0.01/0/0.05/0.1, dsn=2.0.0, status=sent (250 2.0.0 Ok: queued as 5058FBC3A)"

  val o = Settings.options().copy(tailedLogFileName = "tailed.log", rotatedPatternFn = _.matches("backup-.*"),  indexBatchSize = 1001)

  def testFile = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("META-INF/logs/parser/backup-single-client.log.1"))
  def expectedSizeOf[E](i: Int)(c: Iterable[E]) { new HashSet[E] ++ c should have size i }

  test("message id regex should capture message id") {
    midLine match {
      case ParsingUtils.midRegex(queueId, msgId) =>
        queueId should be ("3bZLDk1V50z37Dv")
        msgId should be ("whatever")
      case _ =>
        throw new IllegalStateException(cidLine.toString + " is not valid!")
    }
  }

  test("client id regex should capture message id and client id") {
    cidLine match {
      case ParsingUtils.cidRegex(queueId, clientId) =>
        queueId should be ("3bZLDk1V50z37Dv")
        clientId should be ("test-mail-module")
      case _ =>
        throw new IllegalStateException(cidLine.toString + " is not valid!")
    }
  }

  test("there should be only 1 client id lines in test file, the previous 3 have client-id information in message-id header") {
    val clientIdsByMessageId = testFile.getLines().toIterable
      .collect {
        case ParsingUtils.cidRegex(queueId, clientId) => (queueId, clientId)
      }.toMap

    clientIdsByMessageId should not be null
    clientIdsByMessageId should have size 1
    expectedSizeOf(1)(clientIdsByMessageId.values)
  }

  test("recipient regex should capture all groups") {
    recipientLine match {
      case ParsingUtils.deliveryAttempt(date, queueId, recipient, status, info) =>
        date should be ("2013 Jun 16 16:05:07.123")
        queueId should be ("3bZLDk1V50z37Dv")
        recipient should be ("liska@fg.cz")
        status should be ("sent")
        info should be ("250 2.0.0 Ok: queued as 5058FBC3A")
      case _ =>
        throw new IllegalStateException(recipientLine.toString + " is not valid!")
    }
  }

  test("there should be 7 (sent || deferred || bounced) and 1 expired lines in test file") {
    val sentDeferredOrBounced = testFile.getLines().toIterable
      .collect {
        case ParsingUtils.deliveryAttempt(date, queueId, recipient, status, info)
          => (date, queueId, recipient, status, info)
    }

    val expired = testFile.getLines().toIterable
      .collect {
        case ParsingUtils.expiredRegex(date, queueId, recipient, status, info)
          => (date, queueId, recipient, status, info)
    }

    sentDeferredOrBounced should not be null
    sentDeferredOrBounced should have size 15
    sentDeferredOrBounced foreach (
      tuple => tuple.productIterator.foreach(
        Option(_) should not be None
      )
    )

    expired should have size 1
  }

  test("expired regex should capture all groups") {
    expiredLine match {
      case ParsingUtils.expiredRegex(date, queueId, sender, status, info) =>
        date should be ("2013 Jun 15 16:28:52.123")
        queueId should be ("3bYscs22wBz37Dv")
        sender should be ("no-reply@directmail.fg.cz")
        status should be ("expired")
        info should be ("returned to sender")
      case _ =>
        throw new IllegalStateException(expiredLine.toString + " is not valid!")
    }
  }

  test("there should be only 1 expired line in test file") {
    val messages = testFile.getLines().toIterable
      .collect {
        case ParsingUtils.expiredRegex(date, queueId, sender, status, info)
          => (date, queueId, sender, status, info)
    }

    messages should not be null
    messages should have size 1
    messages foreach (
      tuple => tuple.productIterator.foreach(
        Option(_) should not be None
      )
    )
  }

}
