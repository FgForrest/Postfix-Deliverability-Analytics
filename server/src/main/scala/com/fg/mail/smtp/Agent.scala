package com.fg.mail.smtp

import akka.actor.{Props, ActorSystem}
import org.slf4j.LoggerFactory
import com.fg.mail.smtp.index.DbManager

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 1:43 PM u_jli Exp $
 */
object Agent extends App {
  val logger = LoggerFactory.getLogger("Agent")

  run()

  def run() = {
    val system = ActorSystem("agent")
    val o = Settings.options()
    system.actorOf(Props(new Supervisor(o, new DbManager(o))), "supervisor")
    system.awaitTermination()
  }

}
