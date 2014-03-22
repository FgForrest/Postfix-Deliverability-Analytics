package com.fg.mail.smtp.util

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 12/5/13 9:16 PM u_jli Exp $
 */
trait CollectionImplicits {
  implicit class ListExtensions[K](val list: List[K]) {
    def copyWithout(item: K) = {
      val (left, right) = list span (_ != item)
      left ::: right.drop(1)
    }
  }

  implicit class MapExtensions[K, V](val map: Map[K, V]) {
    def updatedWith(key: K, default: V)(f: V => V) = {
      map.updated(key, f(map.getOrElse(key, default)))
    }
  }
}
