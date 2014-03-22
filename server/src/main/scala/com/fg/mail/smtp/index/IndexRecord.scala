package com.fg.mail.smtp.index

/**
 * LogEntry represents a relevant smtp log entry. All properties should be non-null except for sender and info message
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 5:46 PM u_jli Exp $
 *
 * @param state - enum 0 = soft bounce, 1 = hard bounce, 2 = unknown bounce, 3 = OK
 */
case class IndexRecord(
                    date: Long,
                    queueId: String,
                    msgId: String,
                    rcptEmail: String,
                    senderEmail: String,
                    status: String,
                    info: String,
                    state: Int,
                    stateInfo: String) extends Serializable with Comparable[IndexRecord] {

  def compareTo(that: IndexRecord): Int = {
    if (this.date > that.date)
      1
    else if (this.date < that.date)
      -1
    else if (this == that)
      0
    else
      this.toString.compareTo(that.toString)
  }

}

case class ClientIndexRecord(clientId: String, ir: IndexRecord, fromTailing: Boolean)