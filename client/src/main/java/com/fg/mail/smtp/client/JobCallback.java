package com.fg.mail.smtp.client;

import javax.annotation.Nullable;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/3/13 8:51 PM u_jli Exp $
 */
public interface JobCallback<T> {

    void execute(T job, @Nullable Long from, @Nullable Long to);

}
