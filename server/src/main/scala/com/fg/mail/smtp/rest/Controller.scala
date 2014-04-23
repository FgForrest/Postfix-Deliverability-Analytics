package com.fg.mail.smtp.rest

import com.fg.mail.smtp._
import scala.xml.Xhtml
import scala.collection.immutable.ListMap
import com.fg.mail.smtp.HomePage
import com.fg.mail.smtp.QueryPage
import com.fg.mail.smtp.Options
import com.fg.mail.smtp.index.Index
import com.fg.mail.smtp.util.ServerInfoService

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 7/26/13 3:35 PM u_jli Exp $
 */
class Controller(serverInfoService: ServerInfoService, o: Options) {

  lazy val url = s"http://${o.hostName}:${o.httpServerPort}/"

  lazy val actionHrefs = ListMap(
      "Shutdown"                                                                                      -> "agent-shutdown",
      "Restart (restart http server, reload configuration, refresh bounce list, reindex logs)"        -> "agent-restart",
      "Reindex (refresh bounce list, reindex logs)"                                                   -> "agent-reindex",
      "Refresh bounce list"                                                                           -> "agent-refresh-bouncelist"
  ).mapValues(url + _)

  lazy val readHrefs = ListMap(
      "Index status (since last start only)"                                                          -> "agent-status",
      "Total count of recipient email addresses"                                                      -> "agent-status/rcpt-address-counts",
      "Recipient email addresses"                                                                     -> "agent-status/rcpt-addresses",
      "Unclassified bounce messages"                                                                  -> "agent-status/unknown-bounces",
      "Memory info (RAM usage)"                                                                       -> "agent-status/memory-usage",
      "Index and queue size"                                                                          -> "agent-status/index-memory-footprint",
      "Indexed log files"                                                                             -> "agent-status/indexed-log-files",
      "Environment info (memory, threads, GC, variables)"                                             -> "agent-status/server-info",
      "Querying"                                                                                      -> "agent-read"
  ).mapValues(url + _)

  def dispatch(req: View, index: Index): String = req match {
    case HomePage(_) =>
      Xhtml.toXhtml(
        <html>
          <body>
            <ul> {for (href <- actionHrefs) yield <li><a href={href._2}>{href._1}</a></li>} </ul>
            <ul> {for (href <- readHrefs) yield <li><a href={href._2}>{href._1}</a></li>} </ul>
          </body>
        </html>
      )
    case QueryPage(_) =>
      Xhtml.toXhtml(
        <html>
          <body>
            {for (clientId <- index.getClientIds) yield
            <form action={"agent-read/" + clientId} method="get">
              <input type="submit" value={"Query " + clientId + "client id"}/><div>order by :</div>
              <input type="radio" name="groupBy" value="rcptEmail"/>recipient email address
              <input type="radio" name="groupBy" value="msgId"/>message id
              <input type="radio" name="groupBy" value="queueId"/>queue id
            </form>
            }
          </body>
        </html>
      )

    case ServerInfo(_) =>
      Xhtml.toXhtml(
        <html>
          <body>
            <pre>{serverInfoService.getEnvironment.toString}
            </pre>
          </body>
        </html>
      )
    }

}
