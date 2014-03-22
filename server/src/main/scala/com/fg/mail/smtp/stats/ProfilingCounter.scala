package com.fg.mail.smtp.stats

import akka.actor.{ActorLogging, Actor}
import com.fg.mail.smtp.{Request, ReqCtx, Client}

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 12/7/13 7:29 PM u_jli Exp $
 */
class ProfilingCounter extends Actor with ActorLogging {

  /* index operations are recorded to status for having some overview */
  var status: LastIndexingStatus = _

  override def preStart() {
    log.info(" is starting")
    status = new LastIndexingStatus
  }

  override def postStop() {
    log.info(" is stopping")
  }

  override def receive = {

    case CountIndexedDeliveryAttemptLine(count) =>
      status.logEntry(count)

    case CountIndexedCidLine(count) =>
      status.cidLineIndexed(count)

    case CountIndexedMidLine(count) =>
      status.midLineIndexed(count)

    case CountClientRequest(c) =>
      status.clientRequest(c)

    case GetCountStatus(rc) =>
      sender ! Some(status)
  }

}

case class CountIndexedCidLine(count: Long)
case class CountRemovedLine(count: Long)
case class CountClientRequest(c: Client)
case class CountIndexedMidLine(count: Long)
case class CountIndexedDeliveryAttemptLine(count: Long)
case class GetCountStatus(ctx: ReqCtx) extends Request