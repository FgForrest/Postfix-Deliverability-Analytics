package com.fg.mail.smtp.util

import org.slf4j.LoggerFactory
import com.fg.mail.smtp.Options

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 11/11/13 9:43 PM u_jli Exp $
 */
trait Profilable extends CollectionImplicits {

  val logger = LoggerFactory.getLogger(getClass.getName)

  val o: Options

  var overall = Map[String, (Long, Long)]()

  def profile[R](limit: Long, msg: String, argument: String = "profiling subject not provided")(block: => R): R = {
    if (!o.profilingEnabled)
      return block

    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    val tookInMicroSeconds = (t1 - t0) / 1000
    overall = overall.updatedWith(msg, (0, 0))(t => (t._1 + 1, t._2 + tookInMicroSeconds))
    if (tookInMicroSeconds / 1000 > limit) {
      logger.warn(s"[PROFILE : '$msg' took $tookInMicroSeconds micro seconds] > $argument")
      logger.warn(getResultString)
    }
    result
  }

  def getResultString: String = {
    overall.foldLeft("\n") { (acc, t) =>
      val callsCount = t._2._1
      val totalTime = t._2._2
      acc + " --- overall --- " + t._1 + " : " + (totalTime / 1000 / 1000) + " seconds in " + callsCount + " calls\n"
    }
  }

  def logResultString = {
    if (o.profilingEnabled)
      logger.info(getResultString)

  }
}
