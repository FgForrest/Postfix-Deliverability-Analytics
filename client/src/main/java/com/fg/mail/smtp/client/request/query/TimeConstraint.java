package com.fg.mail.smtp.client.request.query;

import javax.annotation.Nullable;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/5/13 8:58 AM u_jli Exp $
 */
public interface TimeConstraint {

    public static final String P_NAME_FROM = "from";
    public static final String P_NAME_TO = "to";

    @Nullable
    Long getFrom();

    @Nullable
    Long getTo();

}
