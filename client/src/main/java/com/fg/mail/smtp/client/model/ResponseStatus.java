package com.fg.mail.smtp.client.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 7/1/13 12:12 AM u_jli Exp $
 */
public class ResponseStatus {
    private Boolean succeeded;
    private String message;
    private Long timeStamp;
    private String version;
    private Long responseTime;

    @JsonCreator
    public ResponseStatus(@JsonProperty("succeeded") Boolean succeeded,
                          @JsonProperty("message") String message,
                          @JsonProperty("timeStamp") Long timeStamp,
                          @JsonProperty("version") String version,
                          @JsonProperty("responseTime") Long responseTime
    ) {
        this.succeeded = succeeded;
        this.message = message;
        this.timeStamp = timeStamp;
    }

    public Boolean getSucceeded() {
        return succeeded;
    }

    public String getMessage() {
        return message;
    }

    public Long getTimeStamp() {
        return timeStamp;
    }

    public String getVersion() {
        return version;
    }

    public Long getResponseTime() {
        return responseTime;
    }
}