package com.fg.mail.smtp

import java.text.SimpleDateFormat
import java.util.{Date, Locale}
import com.typesafe.config.ConfigFactory
import java.io.File
import akka.actor.{ActorSelection, Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Matchers, BeforeAndAfterAll, FunSpecLike}
import scala.concurrent.Await
import akka.pattern.ask
import com.fg.mail.smtp.index.{DbManager, IndexRecord, AutoCleanUpPersistence}
import com.fg.mail.smtp.client.model.SmtpLogEntry


/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 5:13 PM u_jli Exp $
 */
abstract class TestSupport(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FunSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("agent"))

  val opt: Options
  var indexer: ActorSelection = _
  var counter: ActorSelection = _
  var tailer: ActorSelection = _

  lazy implicit val timeout = opt.askTimeout
  lazy implicit val rc = new ReqCtx(Map[String, String]("client-version" -> "123", "User-Agent" -> "test"))

  def providePersistence: DbManager = new DbManager(opt) with AutoCleanUpPersistence

  override def beforeAll() {
    system.actorOf(Props(new Supervisor(opt, providePersistence)), "supervisor")
    indexer = system.actorSelection("akka://agent/user/supervisor/indexer")
    counter = system.actorSelection("akka://agent/user/supervisor/counter")
    tailer = system.actorSelection("akka://agent/user/supervisor/indexer/tailer")
    val index = Await.result(indexer ? GetDisposableRecordsByClientId(rc), timeout.duration)
    assert(index != null)
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  def entriesShouldNotContainSentOnes(col: Iterable[SmtpLogEntry]) = col filter (_.getState == 3) should be('empty)
  def recordsShouldNotContainSentOnes(col: Iterable[IndexRecord]) = col filter (_.state == 3) should be('empty)

  val cl = getClass.getClassLoader
  cl.loadClass("org.slf4j.LoggerFactory").getMethod("getLogger",cl.loadClass("java.lang.String")).invoke(null,"ROOT")

  val testLogDir = "src/test/resources/META-INF/logs/"

  def sleepFor(t: Long) { Thread.sleep(t) }
  def sleep(ms: Long) = () => Thread.sleep(ms)

  def toDate(d: String): Date = { new SimpleDateFormat("yyyy MMM dd HH:mm:ss.SSS", Locale.US).parse(d) }
  def fromDate(d: Date): String = { new SimpleDateFormat("yyyy MMM dd HH:mm:ss.SSS", Locale.US).format(d) }

  def loadOptions(fileName: String): Options =
    Settings.buildOptions(
      ConfigFactory.parseFile(Option(getClass.getClassLoader.getResource(fileName)).fold(new File("../conf/" + fileName))(url => new File(url.toURI)))
    )
}
