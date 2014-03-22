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

  val url = s"http://${o.hostName}:${o.httpServerPort}/"

  val actionHrefs = ListMap(
      "Vypnout"                                                                                       -> "agent-shutdown",
      "Restartovat (restart http serveru, reload konfigurace, refresh bounce listu, reindexace logů)" -> "agent-restart",
      "Reindexovat (refresh bounce listu, reindexace logů)"                                           -> "agent-reindex",
      "Refresh bounce list (refresh seznamu regexů pro kategorizaci bounců)"                          -> "agent-refresh-bouncelist"
  ).mapValues(url + _)

  val readHrefs = ListMap(
      "Status indexování (pouze od posledního startu)"                                                -> "agent-status",
      "Celkový počet emailových adres příjemců"                                                       -> "agent-status/rcpt-address-counts",
      "Emailové adresy příjemců"                                                                      -> "agent-status/rcpt-addresses",
      "Nekategorizované bounce záznamy"                                                               -> "agent-status/unknown-bounces",
      "Spotřeba paměti jvm (zobrazí podrobné informace o využití RAM)"                                -> "agent-status/memory-usage",
      "Velikost indexu a fronty"                                                                      -> "agent-status/index-memory-footprint",
      "Naindexované log soubory"                                                                      -> "agent-status/indexed-log-files",
      "Informace o prostředí (paměť, vlákna, GC, proměnné)"                                           -> "agent-status/server-info",
      "Podrobnější dotazování"                                                                        -> "agent-read"
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
              <input type="submit" value={"Query " + clientId + "client id"}/><div>setřídit podle :</div>
              <input type="radio" name="groupBy" value="rcptEmail"/>emailové adresy příjemce
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
