package com.fg.mail.smtp.rest

import org.scalatest.{Matchers, FunSpec}
import com.fg.mail.smtp.{ShutSystemDown, HomePage, Settings}
import com.fg.mail.smtp.util.ServerInfoService


/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 7/26/13 4:54 PM u_jli Exp $
 */
class ControllerSuite extends FunSpec with Matchers {

  val options = Settings.options()

  val controller = new Controller(new ServerInfoService(options), options)

  describe("Controller should") {

    it("handle HomePage request") {
      controller.dispatch(HomePage(null), null) should not be 'empty
    }

    it("test") {
      ShutSystemDown("wtf") match {
        case ShutSystemDown(msg, ex) =>
          ex match {
            case Some(e) => println("some ex")
            case None => println("none")
        }
      }
    }
  }

}
