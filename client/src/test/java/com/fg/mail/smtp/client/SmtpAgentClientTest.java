package com.fg.mail.smtp.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * NOTE: All junit and integration client tests resides in server test suite ! These are just for playing around and bug fixing
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/2/13 11:50 AM u_jli Exp $
 */
public class SmtpAgentClientTest {
    private static final Log log = LogFactory.getLog("SmtpAgentClientTest");

    private ConnectionConfig cfg = new ConnectionConfig("host", 1523, "http-auth", 2*1000, 4*1000);
    private SmtpAgentClient client = new SmtpAgentClient(cfg);

}
