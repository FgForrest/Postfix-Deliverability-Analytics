package com.fg.mail.smtp.index

import org.mapdb._
import java.io.File
import com.fg.mail.smtp.Options
import org.slf4j.LoggerFactory
import com.fg.mail.smtp.util.Profilable

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/28/13 7:35 PM u_jli Exp $
 */
class DbManager(val o: Options) extends Profilable {
  val log = LoggerFactory.getLogger(getClass.getName)

  lazy val indexDb: DB = buildIndexDb

  lazy val queueDb = DBMaker.newFileDB(new File(o.dbDir + "/queue"))
            .fullChunkAllocationEnable()
            .syncOnCommitDisable()
            .closeOnJvmShutdown()
            .make()

  def buildIndexDb = {
    val dbMaker = DBMaker
                      .newFileDB(new File(o.dbDir + "/" + o.dbName))
                      .fullChunkAllocationEnable()
                      .syncOnCommitDisable()
                      .closeOnJvmShutdown()
                      .freeSpaceReclaimQ(3)
    if (!o.dbAuth.isEmpty)
      dbMaker.encryptionEnable(o.dbAuth)
    dbMaker.make()
  }

  def commit() = profile(700, "Committing transaction") {
      indexDb.commit()
      queueDb.commit()
    }

  def close() {
    log.info("Closing database")
    indexDb.close()
    queueDb.close()
    log.info("Database closed")
  }

  def getPhysicalSize: Long = profile(900, "Measuring db size") {
      0 //TODO
  }

}
