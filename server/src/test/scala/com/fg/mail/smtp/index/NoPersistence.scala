package com.fg.mail.smtp.index

import org.mapdb.DBMaker

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/28/13 8:17 PM u_jli Exp $
 */
trait NoPersistence extends DbManager {

  override lazy val indexDb = DBMaker.newMemoryDB().make()
  override lazy val queueDb = DBMaker.newMemoryDB().make()

}
