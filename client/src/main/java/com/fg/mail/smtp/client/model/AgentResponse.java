package com.fg.mail.smtp.client.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 7/1/13 12:12 AM u_jli Exp $
 */
public class AgentResponse<T> {
    private T result;
    private ResponseStatus status;

    @JsonCreator
    public AgentResponse(@JsonProperty("result") T result, @JsonProperty("status") ResponseStatus status) {
        this.result = result;
        this.status = status;
    }
    public T getResult() {
        return result;
    }
    public ResponseStatus getStatus() {
        return status;
    }
}
