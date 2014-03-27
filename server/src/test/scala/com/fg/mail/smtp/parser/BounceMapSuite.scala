package com.fg.mail.smtp.parser

import org.scalatest.{Matchers, FunSpec}
import com.fg.mail.smtp.Settings

import java.net.URL
import com.sun.xml.internal.messaging.saaj.util.Base64
import scala.util.matching.Regex
import com.fg.mail.smtp.util.ParsingUtils

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 7/18/13 4:53 PM u_jli Exp $
 */
class BounceMapSuite extends FunSpec with Matchers {

  val opt = Settings.options()

  val knownHardBounce = "this email was blacklisted"
  val knownSoftBounce = "network connection timed out"
  val unknownBounce = "nejakej vykonstruovanej duvod v cizim jazyce"

  val prioritizedBounceList = new BounceListParser().parse(getClass.getClassLoader.getResourceAsStream("bounce-regex-list.xml")).fold(
    left => throw new IllegalStateException(left),
    right => right
  )

  describe("parser should return regex map") {

    it("valid") {
      prioritizedBounceList should not be 'empty
      prioritizedBounceList.find(t => t._1 == "hard") should not be None
      prioritizedBounceList.find(t => t._1 == "soft") should not be None
      prioritizedBounceList.exists(_ eq null) should be (false)
      prioritizedBounceList.iterator.next()._1 should equal("soft")

      prioritizedBounceList.foldLeft(21L) {
        case(acc, t@(bounceType, prioritizedOrder, defaultOrder, bounceCategory, r)) =>
          assert(defaultOrder === acc)
          acc - 1
      }
    }

    //TODO nasmerovat na github az to pujde
    it("from remote host") {
      val urlConnection = new URL(opt.bounceListUrlAndAuth._1).openConnection()
      urlConnection.setRequestProperty("Authorization", "Basic " + new String(Base64.encode(opt.bounceListUrlAndAuth._2.getBytes)))

      new BounceListParser().parse(urlConnection.getInputStream).fold(
        left => throw new IllegalStateException(left),
        right => right
      )
    }

    it("that will resolve states correctly") {
      ParsingUtils.resolveState(knownSoftBounce, "bounced", false, prioritizedBounceList) should be ((0, "bad domain: connection timeout"))
      ParsingUtils.resolveState(knownHardBounce, "deferred", true, prioritizedBounceList) should be ((1, "spam detection and blacklisting"))
      ParsingUtils.resolveState("not important", "sent", false, prioritizedBounceList) should be ((3, "OK"))
      ParsingUtils.resolveState("not important", "sent", true, prioritizedBounceList) should be ((4, "finally OK"))
    }
  }

  describe("regex should not take too much time") {

    it("matching common error message") {
      prioritizedBounceList.foreach { tuple: (String, Long, Long, String, Regex) =>
        val now = System.currentTimeMillis()
        tuple._5.pattern.matcher("Host or domain name not found. Name service error for name=cedefop.eu.int type=MX: Host not found, try again").matches()
        val after = System.currentTimeMillis() - now
        if (after > 4 ) fail(s"$after ms takes {$tuple._4}")
      }
    }

  }

}

