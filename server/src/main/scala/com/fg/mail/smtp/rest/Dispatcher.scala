package com.fg.mail.smtp.rest

import com.fg.mail.smtp.rest.RestDSL._
import com.fg.mail.smtp._
import scala.Some
import com.fg.mail.smtp.Client
import com.fg.mail.smtp.IndexQuery
import com.fg.mail.smtp.stats.GetCountStatus

/**
 * A lightweight DSL based router of URIs with query strings
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/26/13 9:28 PM u_jli Exp $
 */
object Dispatcher {

  val groupBy_rcptEmail = "rcptEmail"
  val groupBy_queueId = "queueId"
  val groupBy_msgId = "msgId"

  val from = QueryMatcher(LONG("from"))
  val to = QueryMatcher(LONG("to"))
  val fromTo = QueryMatcher(LONG("from"), LONG("to"))

  val lastOrFirst = QueryMatcher(BOOLEAN("lastOrFirst"))
  val toLastOrFirst = QueryMatcher(LONG("to"), BOOLEAN("lastOrFirst"))
  val fromLastOrFirst = QueryMatcher(LONG("from"), BOOLEAN("lastOrFirst"))
  val groupBy = QueryMatcher(*("groupBy"))
  val fromGroupBy = QueryMatcher(LONG("from"), *("groupBy"))
  val toGroupBy = QueryMatcher(LONG("to"), *("groupBy"))
  val fromToGroupBy = QueryMatcher(LONG("from"), LONG("to"), *("groupBy"))
  val lastOrFirstGroupBy = QueryMatcher(BOOLEAN("lastOrFirst"), *("groupBy"))

  def queryDispatch(rc: ReqCtx) = / {
    case "agent-shutdown" => ShutdownAgent(rc)
    case "agent-restart" => RestartAgent(rc)
    case "agent-reindex" => ReindexAgent(rc)
    case "agent-refresh-bouncelist" => RefreshBounceList(rc)
    case "agent-status" => / {
      case "rcpt-address-counts" => RcptAddressCounts(rc)
      case "rcpt-addresses" => RcptAddresses(rc)
      case "unknown-bounces" => UnknownBounces(rc)
      case "index-age" => IndexAge(rc)
      case "memory-usage" => MemoryUsage(rc)
      case "index-memory-footprint" => IndexMemoryFootprint(rc)
      case "indexed-log-files" => IndexedLogFiles(rc)
      case "server-info" => ServerInfo(rc)
      case $() => GetCountStatus(rc)
    }
    case "agent-read" => / {
      case *(clientId) => / {
        case $() => ? {
          getMatcher(clientId, None, None, None)(rc)
        }
        case "rcptEmail" => / {
          case `@`(email) => / {
            case $() => ? {
              getMatcher(clientId, Some(email), None, None)(rc)
            }
          }
        }
        case "queue" => / {
          case *(queueId) => / {
            case $() => ? {
              getMatcher(clientId, None, Some(queueId), None)(rc)
            }
          }
        }
        case "message" => / {
          case *(msgId) => / {
            case $() => ? {
              getMatcher(clientId, None, None, Some(msgId))(rc)
            }
          }
        }
      }
      case $() => QueryPage(rc)
    }
    case $() => HomePage(rc)
  }

  private def getMatcher(clientId: String, email: Option[String], queue: Option[String], message: Option[String])(rc: ReqCtx): PartialFunction[URIQuery, Request] = {
    case fromToGroupBy(Some(f), Some(t), Some(gb)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(Some(f), Some(t), None, Some(gb))))(rc)
    case fromTo(Some(f), Some(t)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(Some(f), Some(t), None, None)))(rc)
    case fromLastOrFirst(Some(f), Some(l)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(Some(f), None, Some(l), None)))(rc)
    case toLastOrFirst(Some(t), Some(l)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(None, Some(t), Some(l), None)))(rc)
    case fromGroupBy(Some(f), Some(gb)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(Some(f), None, None, Some(gb))))(rc)
    case toGroupBy(Some(t), Some(gb)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(None, Some(t), None, Some(gb))))(rc)
    case lastOrFirstGroupBy(Some(l), Some(gb)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(None, None, Some(l), Some(gb))))(rc)
    case groupBy(Some(gb)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(None, None, None, Some(gb))))(rc)
    case lastOrFirst(Some(l)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(None, None, Some(l), None)))(rc)
    case from(Some(f)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(Some(f), None, None, None)))(rc)
    case to(Some(t)) =>
      Client(IndexFilter(clientId, email, queue, message), Some(IndexQuery(None, Some(t), None, None)))(rc)
    case _ =>
      Client(IndexFilter(clientId, email, queue, message), None)(rc)
  }

  def dispatch(url: String, query: String)(implicit rc: ReqCtx): Option[Request] = {
    queryDispatch(rc)(url) match {
      case Some(f) => f.apply(query)
      case _ => None
    }
  }

}
