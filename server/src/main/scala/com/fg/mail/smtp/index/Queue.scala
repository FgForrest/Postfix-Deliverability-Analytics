package com.fg.mail.smtp.index

import org.mapdb.DB
import scala.collection.JavaConverters._


/**
 * It holds a lookup map[queueId, clientId] that is necessary due to the fact that a log entry doesn't contain information about clientId.
 * Postfix keeps a queue of messages. Message is removed from this queue if it is successfully sent, bounced or expired
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/28/13 8:24 PM u_jli Exp $
 */
class Queue(val records: java.util.Map[String, QueueRecord]) {

  def insert(qid: String, msgIdClientId: QueueRecord): Option[QueueRecord] = Option(records.put(qid, msgIdClientId))

  def lookup(qid: String): Option[QueueRecord] = Option(records.get(qid))

  def invalidate(qid: String): Option[QueueRecord] = Option(records.remove(qid))

  def getQueue = records.asScala.toMap
}

case class QueueRecord(msgId: String, cid: String, rcpt: String, hasBeenDeferred: Boolean) extends Serializable

object Queue {

  def apply(db: DB): Queue = {
    new Queue(db.createHashMap("queue").makeOrGet())
  }

}
