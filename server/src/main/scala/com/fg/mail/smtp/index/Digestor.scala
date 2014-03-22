package com.fg.mail.smtp.index

import java.util.NavigableSet
import org.mapdb.{DB, BTreeKeySerializer}
import scala.collection.JavaConverters._
import com.fg.mail.smtp.Options
import java.io.File
import com.fg.mail.smtp.util.{Profilable, Commons}
import org.slf4j.LoggerFactory

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/29/13 5:53 PM u_jli Exp $
 */
class Digestor(digests: NavigableSet[String], val o: Options) extends Profilable {

  val log = LoggerFactory.getLogger(getClass)

  def getDigests: collection.mutable.Set[String] = digests.asScala

  def getDigestedFiles: Array[File] = {
    profile(200, "Getting digested files") {
      new File(o.logDir).listFiles()
        .filterNot(_.isDirectory)
        .filterNot(_.length() < 10)
        .filter( (x: File) => o.rotatedPatternFn(x.getName) )
        .filter( f => digests.contains(Commons.digestFirstLine(f)))
        .sorted( Ordering.by((_: File).getName) )
    }
  }

  def store(file: File) {
    if (file.length() > 10) {
      store(Commons.digestFirstLine(file), file.getName)
    } else {
      log.warn(s"Digest of file ${file.getName} won't be stored because file is empty !")
    }
  }

  def store(md5: String, fileName: String) {
    if (digests.add(md5))
      log.info(s"md5 digest for file $fileName successfully persisted")
    else
      log.warn(s"md5 digest for file $fileName already existed !")
  }
}

object Digestor {

  def apply(db: DB, name: String, o: Options): Digestor = {
    new Digestor(db.createTreeSet(name).serializer(BTreeKeySerializer.STRING).makeOrGet(), o)
  }

}
