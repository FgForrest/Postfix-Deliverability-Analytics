package com.fg.mail.smtp.tail

import com.fg.mail.smtp.Options
import java.io.{InputStream, File}
import org.slf4j.LoggerFactory

/**
 * Creates infinite enumeration of input streams from the same underlying path to a file that may be rotated
 *
 * @param file File handle to the log file
 * @param firstEOF possibility to execute this method when hitting EndOfFile
 * @param fileRotation possibility to execute this method when file is rotated
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/20/13 10:33 AM u_jli Exp $
 */
class Rotation(o: Options, file: File)(firstEOF: () => Unit, fileRotation: () => Unit) extends java.util.Enumeration[InputStream] {
  val log = LoggerFactory.getLogger(getClass.getName)

  var firstIterationOver = false

  /**
   * reOpenTries - how many times to try to re-open the file - reasonable default might be 3
   * reOpenSleep - to sleep between re-open retries - reasonable default might be 1 second
   */
  def hasMoreElements = {
    if (testExists()) {
      true
    } else {
      log.warn(s"Tailing is about to stop, file was not found in ${o.reOpenTries} attempts")
      false
    }
  }

  /**
   * eofWaitNewInput - to be called when the stream walked to the end of the file and need to wait for some more input - reasonable default might be 1 second
   */
  def nextElement = {
    val result = new TailingInputStream(file, o.eofNewInputSleep, firstEOF, firstIterationOver)
    if (firstIterationOver) {
      log.info("Creating TailingInputStream for new file, current file is rotated")
      fileRotation()
    } else {
      log.info("Creating TailingInputStream for current file")
      firstIterationOver = true
    }
    result
  }

  /**
   * Test file existence N times, wait between retries
   *
   * @return true on success
   */
  def testExists(): Boolean = {
    def tryExists(n: Int): Boolean =
      if (file.exists)
        true
      else if (n > o.reOpenTries) {
        false
      } else {
        o.reOpenSleep()
        tryExists(n+1)
      }

    tryExists(1)
  }
}
