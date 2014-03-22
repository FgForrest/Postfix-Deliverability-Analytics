package com.fg.mail.smtp.index

import org.mapdb.DBMaker
import java.io.File

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/28/13 8:15 PM u_jli Exp $
 */
trait AutoCleanUpPersistence extends DbManager {

  override def buildIndexDb = {
    val dbMaker = DBMaker
                    .newFileDB(new File(o.dbDir + "/" + o.dbName))
                    .asyncWriteEnable()
                    .fullChunkAllocationEnable()
                    .syncOnCommitDisable()
                    .deleteFilesAfterClose()
    if (!o.dbAuth.isEmpty)
      dbMaker.encryptionEnable(o.dbAuth)
    dbMaker.make()
  }

  override lazy val queueDb = DBMaker.newFileDB(new File(o.dbDir + "/queue"))
    .fullChunkAllocationEnable()
    .syncOnCommitDisable()
    .deleteFilesAfterClose()
    .make()

}
