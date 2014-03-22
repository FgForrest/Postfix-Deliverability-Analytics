package com.fg.mail.smtp.client.request.filter;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/5/13 6:04 PM u_jli Exp $
 */
public class AppendablePath implements AgentUrlPath {

    protected StringBuilder path = new StringBuilder();

    public AppendablePath(String path) {
        assert path != null;
        assert !path.startsWith("/");
        this.path.append("/").append(path);
    }

    public AppendablePath appendSegment(String segment) {
        path.append("/").append(segment);
        return this;
    }

    public String print() {
        return path.toString();
    }

}
