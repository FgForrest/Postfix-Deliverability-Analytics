package com.fg.mail.smtp.client.request.filter;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/6/13 4:35 PM u_jli Exp $
 */
public enum UrlPathPart {

    clientId("agent-read"), rcptEmail("rcptEmail"), queueId("queue"), msgId("message");

    private String name;

    UrlPathPart(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
