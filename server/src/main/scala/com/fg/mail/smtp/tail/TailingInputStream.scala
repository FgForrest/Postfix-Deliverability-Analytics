package com.fg.mail.smtp.tail

import java.io.{FileInputStream, InputStream, File}
import org.slf4j.LoggerFactory
import scala.annotation.tailrec

/**
 * InputStream that handles log rotation. It will not return -1 on EOF. Instead it waits and continues reading.
 * When file is being rotated it behaves just if it found EOF and it returns -1. It is used together with SequenceInputStream
 * that is supplied new TailingInputStream (with the new file of the same name) when the old returns -1 (file rotated)
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/20/13 10:34 AM u_jli Exp $
 */
class TailingInputStream(val file: File, val eofWaitForNewInput: () => Unit, val firstEOF: () => Unit, var firstEofReached: Boolean = false) extends InputStream {
  val log = LoggerFactory.getLogger(getClass.getName)

  require(file != null)
  assume(file.exists, "Attempt to tail a file that doesn't exists, somebody probably moved/removed postfix log file")

  private val inputStream = new FileInputStream(file)

  def read: Int = handle(inputStream.read)

  override def read(b: Array[Byte]): Int = read(b, 0, b.length)

  override def read(b: Array[Byte], off: Int, len: Int): Int = handle(inputStream.read(b, off, len))

  override def close() {
    log.warn("Closing input stream of tailed file")
    inputStream.close()
  }

  /* NOTE !!! rotating a file with just one line and immediate writing to the new one makes this method return false !!! */
  protected def rotatedOrClosed_? = inputStream.getChannel.position > file.length || !inputStream.getChannel.isOpen

  @tailrec
  private def handle(read: => Int): Int = read match {
    case -1 if rotatedOrClosed_? => 
      log.info(s"File ${file.getName} was rotated or closed")
      -1
    case i if i > -1 => i
    case -1 => // when reading a big file I'm getting -1 return value several times, which leads to premature firstEOF !
      try {
        eofWaitForNewInput()
      } catch {
        case ie: InterruptedException =>
          log.warn("Tailing interrupted while sleeping and waiting for new input !")
          close()
      }
      if (!firstEofReached) {
        firstEOF()
        firstEofReached = true
      }
      handle(read)
  }

}
