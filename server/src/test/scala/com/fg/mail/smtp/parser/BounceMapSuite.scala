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

  val knownHardBounce = "Host or domain name not found. Name service error for name=cedefop.eu.int type=MX: Host not found, try again"
  val knownSoftBounce = "network connection timed out"
  val unknownBounce = "nejakej vykonstruovanej duvod v cizim jazyce"

  val bounceMap = new BounceListParser().parse(getClass.getClassLoader.getResourceAsStream("bounce-regex-list.xml")).fold(
    left => throw new IllegalStateException(left),
    right => right
  )

  describe("parser should return regex map") {

    it("valid") {
      bounceMap.keys.exists(bounceType => bounceType != "hard" && bounceType != "soft") should be (false)
      bounceMap.values.exists(_ eq null) should be (false)
      bounceMap("soft") should not be 'empty
      bounceMap("hard") should not be 'empty
      bounceMap.iterator.next()._1 should equal("soft")
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
      ParsingUtils.resolveState(knownSoftBounce, "bounced", false, bounceMap) should be ((0, "bad domain: connection timeout"))
      ParsingUtils.resolveState(knownHardBounce, "deferred", true, bounceMap) should be ((1, "bad domain: host/domain not found"))
      ParsingUtils.resolveState("not important", "sent", false, bounceMap) should be ((3, "OK"))
      ParsingUtils.resolveState("not important", "sent", true, bounceMap) should be ((4, "finally OK"))
    }
  }

  describe("regex should not take too much time") {

    it("matching common error message") {
      bounceMap.values.flatten.foreach { tuple: (String, Regex) =>
        val now = System.currentTimeMillis()
        tuple._2.pattern.matcher("Host or domain name not found. Name service error for name=cedefop.eu.int type=MX: Host not found, try again").matches()
        val after = System.currentTimeMillis() - now
        if (after > 4 ) fail(s"$after ms takes {$tuple._1}")
      }
    }

  }

}

