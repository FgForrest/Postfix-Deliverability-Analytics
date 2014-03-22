package com.fg.mail.smtp.client;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 7/3/13 10:59 AM u_jli Exp $
 */
public class ClientNotAvailableException extends Exception {

    public ClientNotAvailableException(String message) {
        super(message);
    }

    public ClientNotAvailableException(String message, Exception e) {
        super(message, e);
    }

}
