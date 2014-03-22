package com.fg.mail.smtp.rest

import com.fg.mail.smtp.Request

/**
 * DSL backend for {@link com.fg.mail.smtp.rest.Dispatcher}
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 1:56 PM u_jli Exp $
 */
object RestDSL {

  type KeyValueMatcher[T] = (String, PartialFunction[Seq[String], T])

  object $ {
    def unapply(pathSegment: String): Boolean = {
      pathSegment.eq(null) || pathSegment.length == 0
    }
  }

  object * {
    def apply(key: String): KeyValueMatcher[String] = ( key, { case Seq(head, _*) => head } )
    def unapply(pathSegment: String): Option[String] = {
      if (!(pathSegment eq null) && pathSegment.length > 0)
        Some(pathSegment)
      else
        None
    }
  }

  object `@` {
    def apply(key: String): KeyValueMatcher[String] = ( key, { case Seq(head, _*) => head } )
    def unapply(emailSegment: String): Option[String] = {
      emailSegment match {
        case (es: String) if es.contains("@") => Some(es)
        case _ => None
      }
    }
  }

  object BOOLEAN {
    def apply(key: String): KeyValueMatcher[Boolean] = (
      key,
      new PartialFunction[Seq[String], Boolean] {
        def isDefinedAt(values: Seq[String]): Boolean = {
          values.mkString match {
            case path: String if path.equalsIgnoreCase("true") || path.equalsIgnoreCase("false") => true
            case _ => false
          }
        }

        def apply(values: Seq[String]): Boolean = {
          values.mkString match {
            case BOOLEAN(b) => b
          }
        }
      }
      )
    def unapply(pathSegment: String): Option[Boolean] = {
      pathSegment match {
        case path: String if path.equalsIgnoreCase("true") => Some(true)
        case path: String if path.equalsIgnoreCase("false") => Some(false)
        case _ => None
      }
    }
  }

  object LONG {
    def apply(key: String): KeyValueMatcher[Long] = (
      key,
      new PartialFunction[Seq[String], Long] {
        def isDefinedAt(values: Seq[String]): Boolean = {
          values.exists(_ match { case LONG(_) => true; case _ => false })
        }

        def apply(values: Seq[String]): Long = {
          values.collectFirst({ case LONG(l) => l }).get
        }
      }
      )
    def unapply(string: String): Option[Long] = {
      if (string eq null)
        None
      else
        try {
          Some(java.lang.Long.parseLong(string, 10))
        }
        catch {
          case e: NumberFormatException => None
          case e: Throwable             => throw e
        }
    }
  }

  type Dispatch = (String) => Option[(String) => Option[Request]]

  implicit def RequestToDispatch(h: Request): Dispatch = {
    (pathSegment: String) =>
    {
      if (pathSegment eq null)
        Some((query: String) => Some(h))
      else
        None
    }
  }

  case class /(matcher: PartialFunction[String, Dispatch]) extends Dispatch {

    /**
     * @param trailingPath A valid URI path (or the yet unmatched part of the path of an URI).
     * 		The trailingPath is either null or is a string that starts with a "/".
     * 		The semantics of null is that the complete path was matched; i.e., there
     * 		is no remaining part.
     */
    def apply(trailingPath: String): Option[String => Option[Request]] = {
      if (trailingPath == null)
        return {
          if (matcher.isDefinedAt(null))
            matcher(null)(null)
          else
          // the provided path was completely matched, however it is too short
          // w.r.t. a RESTful application; hence, this points to a design problem
            None
        }

      if (trailingPath.charAt(0) != '/')
        throw new IllegalArgumentException("The provided path: \""+trailingPath+"\" is invalid; it must start with a /.")

      val path = trailingPath.substring(1) // we truncate the trailing "/"
      val separatorIndex = path.indexOf('/')
      val head = if (separatorIndex == -1) path else path.substring(0, separatorIndex)
      val tail = if (separatorIndex == -1 || separatorIndex == 0 && path.length == 1) null else path.substring(separatorIndex)
      if (matcher.isDefinedAt(head))
        matcher(head)(tail)
      else
        None
    }
  }

  type URIQuery = Map[String, Seq[String]]

  case class ?(matcher: PartialFunction[URIQuery, Request]) extends Dispatch {
    def apply(path: String): Option[String => Option[Request]] = {
      if (path ne null)
        None
      else {
        Some((query: String) => {
          val splitUpQuery = decodeRawURLQueryString(query)
          if (matcher.isDefinedAt(splitUpQuery))
            Some(matcher(splitUpQuery))
          else
            None
        })
      }
    }
  }

  trait QueryMatcher {
    protected def apply[T](kvMatcher: KeyValueMatcher[T], uriQuery: URIQuery): Option[T] = {
      val (key, valueMatcher) = kvMatcher
      uriQuery.get(key) match {
        case Some(values) => {
          if (valueMatcher.isDefinedAt(values)) {
            Some(valueMatcher(values))
          } else {
            None
          }
        }
        case None => None
      }
    }
  }

  object QueryMatcher {
    def apply[T1](kvm1: KeyValueMatcher[T1]) =
      new QueryMatcher1(kvm1)

    def apply[T1, T2](kvm1: KeyValueMatcher[T1], kvm2: KeyValueMatcher[T2]) =
      new QueryMatcher2(kvm1, kvm2)

    def apply[T1, T2, T3](kvm1: KeyValueMatcher[T1], kvm2: KeyValueMatcher[T2], kvm3: KeyValueMatcher[T3]) =
      new QueryMatcher3(kvm1, kvm2, kvm3)

  }

  class QueryMatcher1[T1](val kvMatcher1: KeyValueMatcher[T1]) extends QueryMatcher {
    def unapply(uriQuery: URIQuery): Some[Option[T1]] = {
      Some(apply(kvMatcher1, uriQuery))
    }
  }

  class QueryMatcher2[T1, T2](val kvm1: KeyValueMatcher[T1], val kvm2: KeyValueMatcher[T2]) extends QueryMatcher {
    def unapply(uriQuery: URIQuery): Some[(Option[T1], Option[T2])] = {
      Some((apply(kvm1, uriQuery), apply(kvm2, uriQuery)))
    }
  }

  class QueryMatcher3[T1, T2, T3](val kvm1: KeyValueMatcher[T1], val kvm2: KeyValueMatcher[T2], val kvm3: KeyValueMatcher[T3]) extends QueryMatcher {
    def unapply(uriQuery: URIQuery): Some[(Option[T1], Option[T2], Option[T3])] = {
      Some((apply(kvm1, uriQuery), apply(kvm2, uriQuery), apply(kvm3, uriQuery)))
    }
  }

  def decodeRawURLQueryString(query: String, encoding: String = "UTF-8"): Map[String, List[String]] = {
    import java.net.URLDecoder.decode

    var param_values = Map[String, List[String]]().withDefaultValue(List[String]())
    if ((query eq null) || query.length == 0)
      param_values
    else {
      for (param_value <- query.split('&')) {
        val index = param_value.indexOf('=')
        if (index == -1) {
          val param = decode(param_value, encoding)
          param_values = param_values.updated(param, param_values(param))
        } else {
          val param = decode(param_value.substring(0, index), encoding)
          val value = decode(param_value.substring(index + 1), encoding)
          param_values = param_values.updated(param, param_values(param) :+ value)
        }
      }
      param_values
    }
  }


}
