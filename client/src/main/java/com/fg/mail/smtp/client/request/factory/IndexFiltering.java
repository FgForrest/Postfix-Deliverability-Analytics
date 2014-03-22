package com.fg.mail.smtp.client.request.factory;

import com.fg.mail.smtp.client.request.filter.AgentUrlPath;
import com.fg.mail.smtp.client.request.filter.Eq;
import com.fg.mail.smtp.client.request.filter.UrlPathPart;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/6/13 1:37 PM u_jli Exp $
 */
public class IndexFiltering implements AgentUrlPath {

    private Collection<Eq> filtering = new LinkedHashSet<Eq>();

    protected IndexFiltering(String clientId) {
        this.filtering.add(new Eq(UrlPathPart.clientId, clientId));
    }

    protected IndexFiltering(String clientId, Eq filtering) {
        this(clientId);
        this.filtering.add(filtering);
    }

    public String print() {
        return buildPath(filtering.iterator(), new StringBuilder("/"));
    }

    private String buildPath(Iterator<Eq> itr, StringBuilder result) {
        if (itr.hasNext()) {
            Eq eq = itr.next();
            result.append(eq.getPropertyName()).append("/").append(eq.getValue());
            if (itr.hasNext()) {
                result.append("/");
            }
            return buildPath(itr, result);
        } else {
            return result.toString();
        }
    }

    public String toString() {
        return print();
    }
}