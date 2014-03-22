package com.fg.mail.smtp

import akka.actor._

import com.fg.mail.smtp.notification.MailClient
import com.fg.mail.smtp.index.{DbManager, Indexer}
import com.fg.mail.smtp.stats.ProfilingCounter


/**
 * An akka actor that is supervising subordinate actors Indexer and Tailer.
 * It also starts jetty server that is to be subsequently 'joined' in Agent which puts its thread to sleep until server stops.
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 11:31 AM u_jli Exp $
 */
class Supervisor(o: Options, dbManager: DbManager) extends Actor with ActorLogging {

  override def preStart() {
    log.info(" is starting")
    val counter = context.actorOf(Props(new ProfilingCounter), "counter")
    context.actorOf(Props(new Indexer(counter, new DbManager(o), o)).withMailbox("bounded-deque-based"), "indexer")
  }

  override def postStop() {
    log.info(" is stopping")
    dbManager.close()
  }

  override def receive = {

    case ShutSystemDown(why, ex) =>
      MailClient.fail(why, ex, o)
      context.system.shutdown()

  }

}
