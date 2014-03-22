package com.fg.mail.smtp.client.request.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fg.mail.smtp.client.model.AgentResponse;
import com.fg.mail.smtp.client.request.filter.AgentUrlPath;
import com.fg.mail.smtp.client.request.filter.AppendablePath;

import javax.annotation.Nullable;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/5/13 8:15 PM u_jli Exp $
 */
public class AgentReq<T> extends TypeReference<AgentResponse<T>> {

    protected AgentUrlPath path;
    protected IndexQuery query;

    protected TypeReference<AgentResponse<T>> typeRef;

    protected AgentReq(AgentUrlPath path, @Nullable IndexQuery query, TypeReference<AgentResponse<T>> typeRef) {
        this.path = path;
        this.query = query;
        this.typeRef = typeRef;
    }

    public AgentReq(AppendablePath path, TypeReference<AgentResponse<T>> typeRef) {
        this(path, null, typeRef);
    }

    public AgentUrlPath getPath() {
        return path;
    }

    public IndexQuery getQuery() {
        return query;
    }

    public TypeReference<AgentResponse<T>> getTypeRef() {
        return typeRef;
    }
}
