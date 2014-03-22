package com.fg.mail.smtp.util

import java.security.MessageDigest
import java.io._
import java.util.zip.GZIPInputStream
import scala.io.Source
import java.net.URL
import scala.util.Try
import com.sun.xml.internal.messaging.saaj.util.Base64

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/30/13 5:02 PM u_jli Exp $
 */
object Commons {

  def digest(input: String): String = MessageDigest.getInstance("MD5").digest(input.getBytes).map("%02x".format(_)).mkString

  def digestFirstLine(file: File): String = {
    val source = getSource(file)
    try {
      val it = source.getLines()
      require(it.hasNext, s"md5 digesting of the first line of file $file expect it not to be empty !")
      digest(it.next())
    } finally {
      source.close()
    }
  }

  def getInputStream(resourceLocation: String, auth: Option[String]): InputStream = {
    val u: URL =  if (resourceLocation.startsWith("./") || resourceLocation.startsWith("../") || resourceLocation.startsWith("/"))
                    new File(resourceLocation).toURI.toURL
                  else if (!resourceLocation.startsWith("classpath:"))
                    new URL(resourceLocation)
                  else
                    Try(Thread.currentThread.getContextClassLoader)
                      .getOrElse(getClass.getClassLoader)
                      .getResource(resourceLocation.substring("classpath:".length))

    val connection = u.openConnection()
    auth.foreach( a =>
      connection.setRequestProperty("Authorization", "Basic " + new String(Base64.encode(a.getBytes)))
    )
    connection.getInputStream
  }


  def getSource(file: File): Source = if (file.getName.endsWith(".gz")) Source.fromInputStream(gunzipFileSafely(file)) else Source.fromFile(file)

  def gunzipFileSafely(file: File): InputStream = {

    def throwAndClose(msg: String, e: Throwable, in: InputStream) {
      if (in != null) in.close()
      throw new IllegalStateException(msg, e)
    }

    var in: InputStream = null
    try {
      in = new GZIPInputStream(new FileInputStream(file))
    } catch {
      case fnf: FileNotFoundException =>
        throwAndClose(s"Unable to find file ${file.getAbsolutePath}", fnf, in)
      case ioe: IOException =>
        throwAndClose(s"Unexpected IO error during gunziping file ${file.getAbsolutePath}", ioe, in)
      case e: Throwable =>
        throwAndClose(s"Unexpected error during reading file ${file.getAbsolutePath}", e, in)
    }
    in
  }
}
