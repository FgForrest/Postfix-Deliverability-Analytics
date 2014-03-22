package com.fg.mail.smtp.rest

import com.fg.mail.smtp._
import scala.Some
import com.fg.mail.smtp.rest.RestDSL._
import org.scalatest.{Matchers, FunSpec}
import com.fg.mail.smtp.stats.GetCountStatus


/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 2:34 PM u_jli Exp $
 */
class DispatcherSuite extends FunSpec with Matchers {

  implicit val rc = new ReqCtx(Map[String, String]("client-version" -> "123", "User-Agent" -> "test"))

  describe("dispatching should") {

    def dispatchUrl(rc: ReqCtx) = / {
      case "agent-shutdown" => ShutdownAgent(rc)
      case "agent-reindex" => ReindexAgent(rc)
      case "agent-restart" => RestartAgent(rc)
      case "agent-status" => / {
        case "rcpt-address-counts" => RcptAddressCounts(rc)
        case "rcpt-addresses" => RcptAddresses(rc)
        case $() => GetCountStatus(rc)
      }
      case "agent-read" => / {
        case *(clientId) => / {
          case $() => Client(IndexFilter(clientId, None, None, None), None)(rc)
          case "rcptEmail" => / {
            case `@`(email) => / {
              case $() => Client(IndexFilter(clientId, Some(email), None, None), None)(rc)
            }
          }
          case "queue" => / {
            case *(queueId)  => / {
              case $() => Client(IndexFilter(clientId, None, Some(queueId), None), None)(rc)
            }
          }
        }
      }
      case $() => HomePage(rc)
    }
    val dispatch = dispatchUrl(rc)

    it("dispatch parts of query-less URL") {
      dispatch("/").get(null) should be(Some(HomePage(rc)))
      dispatch("/agent-shutdown").get(null) should be(Some(ShutdownAgent(rc)))
      dispatch("/agent-reindex").get(null) should be(Some(ReindexAgent(rc)))
      dispatch("/agent-status").get(null) should be(Some(GetCountStatus(rc)))
      dispatch("/agent-status/rcpt-address-counts").get(null) should be(Some(RcptAddressCounts(rc)))
      dispatch("/agent-status/rcpt-addresses").get(null) should be(Some(RcptAddresses(rc)))

      dispatch("/agent-read/runczech").get(null) should be(Some(Client(IndexFilter("runczech", None, None, None), None)))
      dispatch("/agent-read/runczech/").get(null) should be(Some(Client(IndexFilter("runczech", None, None, None), None)))

      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz").get(null) should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), None)))
      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz/").get(null) should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), None)))

      dispatch("/agent-read/runczech/queue/abc123").get(null) should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), None)))
      dispatch("/agent-read/runczech/queue/abc123/").get(null) should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), None)))

    }

    it("handle URLs that do not match without throwing exceptions") {

      dispatch("/je") should be(None)
      dispatch("/mi") should be(None)
      dispatch("/hroznej/hic") should be(None)

    }

    it("not match URLs that are too long or too short") {

      dispatch("/agent") should be(None)
      dispatch("/agent-read/runczech/moc/dlouhy") should be(None)

    }
  }

  describe("query extraction should") {

    val stringId = QueryMatcher(*("id"))
    val longId = QueryMatcher(LONG("id"))
    val longIdBoolLastOrFirst = QueryMatcher(LONG("id"), BOOLEAN("lastOrFirst"))

    case class From(time: Option[Long])(implicit rc: ReqCtx) extends Request {
      def ctx: ReqCtx = rc
    }
    case class Last(b: String)(implicit rc: ReqCtx) extends Request{
      def ctx: ReqCtx = rc
    }
    case class Multi(from: Option[Long], lastOrFirst: Boolean)(implicit rc: ReqCtx) extends Request{
      def ctx: ReqCtx = rc
    }

    val queryExtraction = / {
      case "single" => ? {
        case stringId(Some(id)) => Client(IndexFilter(id, None, None, None), None)
      }
      case "from"   => ? {
        case longId(id) => From(id)
      }
      case "multi"    => ? {
        case longIdBoolLastOrFirst(id, Some(lastOrFirst)) => Multi(id, lastOrFirst)
      }
    }

    it("extract a single required query parameter") {

      queryExtraction("/single").get("id=Yes") should be(Some(Client(IndexFilter("Yes", None, None, None), None)))
      queryExtraction("/single").get("foo=3434&id=2323") should be(Some(Client(IndexFilter("2323", None, None, None), None)))
      queryExtraction("/single").get("") should be(None)
      queryExtraction("/single").get(null) should be(None)

    }

    it("extract an optional query parameter") {

      queryExtraction("/from").get("") should be(Some(From(None)))
      queryExtraction("/from").get(null) should be(Some(From(None)))
      queryExtraction("/from").get("id=sdfsdf") should be(Some(From(None)))
      queryExtraction("/from").get("id=121") should be(Some(From(Some(121l))))
      queryExtraction("/from").get("id=absct&id=121") should be(Some(From(Some(121l))))

    }

    it("extract many query parameters") {

      queryExtraction("/multi").get("") should be(None)
      queryExtraction("/multi").get(null) should be(None)
      queryExtraction("/multi").get("lastOrFirst=true") should be(Some(Multi(None, true)))
      queryExtraction("/multi").get("id=121") should be(None)
      queryExtraction("/multi").get("id=121&lastOrFirst=false") should be(Some(Multi(Some(121L), false)))

    }
  }

  describe("Dispatcher should") {
    it("handle all possible combinations agent api allows") {
      import com.fg.mail.smtp.rest.Dispatcher._

      dispatch("/agent-read/runczech", "") should be(Some(Client(IndexFilter("runczech", None, None, None), None)))
      dispatch("/agent-read/runczech", null) should be(Some(Client(IndexFilter("runczech", None, None, None), None)))
      dispatch("/agent-read/runczech/", null) should be(Some(Client(IndexFilter("runczech", None, None, None), None)))
      dispatch("/agent-read/runczech/", "") should be(Some(Client(IndexFilter("runczech", None, None, None), None)))

      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz", null) should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), None)))
      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz/", null) should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), None)))
      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz", null) should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), None)))
      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz/", null) should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), None)))

      dispatch("/agent-read/runczech", "to=121&lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", None, None, None), Some(IndexQuery(None, Some(121), Some(true), None)))))
      dispatch("/agent-read/runczech", "lastOrFirst=true&to=121") should be(Some(Client(IndexFilter("runczech", None, None, None),Some(IndexQuery(None, Some(121), Some(true), None)))))

      dispatch("/agent-read/runczech", "from=121&lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", None, None, None), Some(IndexQuery(Some(121), None, Some(true), None)))))
      dispatch("/agent-read/runczech", "lastOrFirst=true&from=121") should be(Some(Client(IndexFilter("runczech", None, None, None),Some(IndexQuery(Some(121), None, Some(true), None)))))

      dispatch("/agent-read/runczech", "from=121") should be(Some(Client(IndexFilter("runczech", None, None, None),Some(IndexQuery(Some(121), None, None, None)))))
      dispatch("/agent-read/runczech", "lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", None, None, None),Some(IndexQuery(None, None, Some(true), None)))))
      dispatch("/agent-read/runczech", "lastOrFirst=true&groupBy=queueId") should be(Some(Client(IndexFilter("runczech", None, None, None), Some(IndexQuery(None, None, Some(true), Some("queueId"))))))
      dispatch("/agent-read/runczech", "from=121&groupBy=rcptEmail") should be(Some(Client(IndexFilter("runczech", None, None, None),Some(IndexQuery(Some(121), None, None, Some("rcptEmail"))))))
      dispatch("/agent-read/runczech", "groupBy=rcptEmail") should be(Some(Client(IndexFilter("runczech", None, None, None),Some(IndexQuery(None, None, None, Some("rcptEmail"))))))
      dispatch("/agent-read/runczech", "lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", None, None, None),Some(IndexQuery(None, None, Some(true), None)))))

      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz", "lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), Some(IndexQuery(None, None, Some(true), None)))))
      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz", "lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), Some(IndexQuery(None, None, Some(true), None)))))
      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz", "from=121") should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), Some(IndexQuery(Some(121), None, None, None)))))
      dispatch("/agent-read/runczech/rcptEmail/liska@fg.cz", "lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", Some("liska@fg.cz"), None, None), Some(IndexQuery(None, None, Some(true), None)))))

      dispatch("/agent-read/runczech/queue/abc123", null) should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), None)))
      dispatch("/agent-read/runczech/queue/abc123/", null) should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), None)))
      dispatch("/agent-read/runczech/queue/abc123", null) should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), None)))
      dispatch("/agent-read/runczech/queue/abc123/", null) should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), None)))

      dispatch("/agent-read/runczech/queue/abc123", "to=121&lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), Some(IndexQuery(None, Some(121), Some(true), None)))))
      dispatch("/agent-read/runczech/queue/abc123", "lastOrFirst=true&to=121") should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), Some(IndexQuery(None, Some(121), Some(true), None)))))
      dispatch("/agent-read/runczech/queue/abc123", "from=121") should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), Some(IndexQuery(Some(121), None, None, None)))))
      dispatch("/agent-read/runczech/queue/abc123", "lastOrFirst=true") should be(Some(Client(IndexFilter("runczech", None, Some("abc123"), None), Some(IndexQuery(None, None, Some(true), None)))))
    }

  }

}
