package com.fg.mail.smtp.parser

import scala.xml.Node
import scala.util.matching.Regex
import java.io.InputStream
import scala.xml.XML
import scala.util.control.Exception._
import java.util.regex.{Pattern, PatternSyntaxException}
import scala.collection.mutable.TreeSet
import org.slf4j.LoggerFactory
import com.fg.mail.smtp.util.Commons

/**
 * Parser of regular expressions xml list. It returns either suitable data structure or a xml validation error.
 * There are to types of categories:
 * Soft bounce means that message was deferred and it is to be tried again later on, it is kept in queue
 * Hard bounce means that the reason of not delivering a message was too serious to try to deliver message again, it is removed from queue right away
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 7/18/13 4:54 PM u_jli Exp $
 */
class BounceListParser {
  val log = LoggerFactory.getLogger(getClass)

  def parse(uriAndCredentials: (String, String)): Either[Throwable, TreeSet[(String, Long, Long, String, Regex)]] = {
    log.info("Resolving regex bounce list from " + uriAndCredentials._1)
    catching(classOf[Throwable])
      .either(Commons.getInputStream(uriAndCredentials._1, Option(uriAndCredentials._2).filter(_.trim.nonEmpty)))
        match {
          case Left(e) => Left(e)
          case Right(is: InputStream) => parse(is)
        }
  }

  def parse(is: InputStream): Either[Exception, TreeSet[(String, Long, Long, String, Regex)]] = {
    log.info("parsing regex bounce list")
    /**
     * @param n xml bounces node
     * @return n if xml is valid or reason why it is not valid
     */
    def getValidNodeOnly(n: Node): Either[Exception, Node] = {
      if (!(Set[String]() ++ (n \ "_").map(_.label)).equals(Set[String]("regex")))
        Left(new IllegalArgumentException("bounces element must have just 'regex' children"))
      else {
        (n \ "_")
          .foldLeft[Either[Exception,Node]](Right(n))((acc, group) =>
              (group \ "regex").partition(regex => (regex \ "@category").length == 1 && (regex \ "or").length > 0) match {
                case (valids, invalids) if !invalids.isEmpty =>
                  Left(new IllegalArgumentException("regex elements must have a 'category' attribute and at least one 'or' child element:\n" + invalids.mkString("\n")))
                case (valids, _) =>
                  (for {
                    r <- valids
                    or <- r \ "or"
                      if catching(classOf[PatternSyntaxException]).either(Pattern.compile(or.text)).isLeft || or.text.isEmpty
                  } yield or).toList match {
                  case Nil => acc
                  case invalidRegexes => Left(new IllegalArgumentException("regex elements must have valid regular expressions:\n" + invalidRegexes.mkString("\n")))
                }
              }
          )
      }
    }

    implicit val regexOrdering = Ordering.by[Regex, String](_.toString())
    val ordering = Ordering[(String, Long, Long, String, Regex)].on((t: (String, Long, Long, String, Regex)) => (t._1, t._2, t._3, t._4, t._5)).reverse
    getValidNodeOnly(XML.load(is)) match {
      case Right(n) =>
        val regexElements = n \ "regex"
        Right(
          regexElements.foldLeft(regexElements.size, TreeSet[(String, Long, Long, String, Regex)]()(ordering))(
            (accumulator, regexElm) => {
              accumulator._2.add(
                (
                  (regexElm \ "@type").text,
                  0L,
                  accumulator._1,
                  (regexElm \ "@category").text,
                  new Regex("(" + (regexElm \ "or").map("("+_.text+")").reduceLeft[String]((ac, g) => ac+"|"+g)+")")
                )
              )
              accumulator.copy(_1 = accumulator._1 - 1)
            }
          )._2
        )
      case Left(v) => Left(v)
    }
  }

}