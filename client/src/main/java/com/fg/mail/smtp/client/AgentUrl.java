package com.fg.mail.smtp.client;

import com.fg.mail.smtp.client.request.factory.AgentReq;
import com.fg.mail.smtp.client.request.factory.IndexQuery;
import com.fg.mail.smtp.client.request.filter.AgentUrlPath;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/9/13 10:00 AM u_jli Exp $
 */
public class AgentUrl {

    private String host;
    private int port;
    private AgentReq request;

    public AgentUrl(String host, int port, AgentReq request) {
        assert host != null;
        assert !host.endsWith("/");
        this.host = host;
        this.port = port;
        this.request = request;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public AgentReq getRequest() {
        return request;
    }

    public URL getURL() {
        String url = print();
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Url : " + url + " is not valid, please check your configuration !", e);
        }
    }

    public String print() {
        StringBuilder base = new StringBuilder("http://").append(host).append(":").append(port);
        AgentUrlPath path = request.getPath();
        if (path != null) {
            base.append(path.print());
            IndexQuery query = request.getQuery();
            if (query != null) {
                base.append(query.print());
            }
        }
        return base.toString();
    }
}
