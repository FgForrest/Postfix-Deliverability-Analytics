package com.fg.mail.smtp.db

import com.fg.mail.smtp.TestSupport
import scala.concurrent.duration.DurationDouble
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import akka.util.Timeout
import org.scalatest.Ignore

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 11/9/13 5:53 PM u_jli Exp $
 */
@Ignore
class MapDbSuite extends TestSupport {

  val df = new SimpleDateFormat("mm:ss.SSS", Locale.US)
  def dbDir = new File(opt.dbDir)
  val opt = loadOptions("application.conf").copy(
    httpServerStart = false
  )

/*
    it("whatever whatever") {

      def s(index: Index) = {
        (0 to 10000000).foreach{ x =>
          val i: Long = x + 100000000000L
          index.addRecord("cezdirectmail", new IndexRecord(i, "3dFjk95XgCz2plD", "<1995936274.3901383825361777.JavaMail.p_prj@gds39k>", "zp.stava@cez.cz", "", "sent", "(250 2.0.0 Mail 899273466 queued for delivery in session 7ea30000021e.),3,OK)", 1, "Lsoft"), true)
          if (i%100000==0) {
            println(df.format(new Date(System.currentTimeMillis())))
            index.commit()
          }
        }
      }

      val index = provideIndex
      val now = System.currentTimeMillis()

      s(index)

      val then = System.currentTimeMillis()
      val thenDiff = then - now
      println(thenDiff)

    }
*/


}
