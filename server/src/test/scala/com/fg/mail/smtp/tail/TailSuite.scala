package com.fg.mail.smtp.tail

import java.io._
import org.scalatest._
import scala.concurrent.Await
import com.fg.mail.smtp._
import akka.pattern.ask

import com.fg.mail.smtp.stats.{GetCountStatus, LastIndexingStatus}

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 2:34 PM u_jli Exp $
 */
class TailSuite extends TestSupport with BeforeAndAfter {

  val opt = loadOptions("application-test.conf").copy(
                  httpServerStart = false,
                  eofNewInputSleep = sleep(25)
                )

  val existingFile = new File(testLogDir + "parser/existingFile")
  val tailedFile = new File(testLogDir + "parser/tailed.log")
  val absentFile = new File(testLogDir + "parser/absentFile")
  val rotatedFile = new File(testLogDir + "parser/rotatedFile")

  val msgIdLine = "2013 Jun 11 06:38:52.123 gds39d postfix/cleanup[7565]: B0236C2C111XXXX: message-id=msgId-B0236C2C111XXXX\n"

  before {
    existingFile.createNewFile
    absentFile.delete
  }
  after {
    absentFile.delete
    rotatedFile.delete
    existingFile.delete
  }

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    val out = new FileWriter(tailedFile, false)
    out.append("s\ns\ns\ns\n")
    out.flush()
    out.close()
    super.afterAll()
  }

  describe("tail should") {

    it("find existing file") {
      assert(existingFile.exists())
      assert(new Rotation(opt.copy(reOpenTries = 1), existingFile)(() => Unit, () => Unit).testExists())
      assert(new Rotation(opt.copy(reOpenTries = 0), existingFile)(() => Unit, () => Unit).testExists())
    }

    it("fail on absent file") {
      assert(!absentFile.exists())
      assert(!new Rotation(opt.copy(reOpenTries = 1), absentFile)(() => Unit, () => Unit).testExists())
      assert(!new Rotation(opt.copy(reOpenTries = 0), absentFile)(() => Unit, () => Unit).testExists())
    }

    it("find existing file after x attempts") {
      def testCreate(f: File, n: Int) = try {
        var count = 0
        val result = new Rotation(opt.copy(reOpenTries = n, reOpenSleep = () => { count += 1; if (count >= n) f.createNewFile }), f)(() => Unit, () => Unit).testExists()
        (result, count)
      } finally {
        f.delete
      }

      assert(existingFile.exists())
      assert(!absentFile.exists())
      assert(testCreate(absentFile, 0) === (false, 0))
      assert(testCreate(absentFile, 1) === (true, 1))
      assert(testCreate(existingFile, 10) === (true, 0))
    }
  }

  describe("tailing") {

    it("inputStream should read lines as they are being added and survive file rotation") {

      def testLine(s: String, expectedCount: Long) {
        system.log.info("writing a line")
        val out = new FileWriter(tailedFile, true)
        out.append(s)
        out.flush()
        out.close()
      }

      testLine(msgIdLine, 1)
      testLine(msgIdLine, 2)
      testLine(msgIdLine + msgIdLine + msgIdLine, 5)

      tailedFile.renameTo(rotatedFile) should be (true)

      testLine(msgIdLine, 6)
    }
  }

}
