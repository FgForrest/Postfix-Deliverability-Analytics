package com.fg.mail.smtp.client.request.query;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/5/13 5:20 PM u_jli Exp $
 */
public interface Grouping {

    public static final String P_NAME = "groupBy";

    By getProperty();
}
