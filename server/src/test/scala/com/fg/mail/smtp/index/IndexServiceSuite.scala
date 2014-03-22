package com.fg.mail.smtp.index

import com.fg.mail.smtp.{IndexQuery, IndexFilter, Client, TestSupport}
import scala.collection.IterableView
import scala.concurrent.Await
import akka.pattern.ask
import com.fg.mail.smtp.rest.Dispatcher

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 12/5/13 1:55 PM u_jli Exp $
 */
class IndexServiceSuite extends TestSupport {

  val opt = loadOptions("application-test.conf").copy(httpServerStart = false)

  describe("test") {

    it("properly sorted") {
      val client = Client(IndexFilter("test-mail-module", None, None, None), Some(IndexQuery(None, None, None, Some(Dispatcher.groupBy_msgId))))
      val result = Await.result(indexer ? client, timeout.duration).asInstanceOf[Option[Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]]]].get

      result.keys.size should be (3)
      val logEntries: Iterable[IndexRecord] = result.last._2
      assert(logEntries.size === 7)
    }

  }

}
