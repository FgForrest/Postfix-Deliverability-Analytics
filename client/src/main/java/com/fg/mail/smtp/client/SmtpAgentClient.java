package com.fg.mail.smtp.client;

import com.fg.mail.smtp.client.model.SmtpLogEntry;
import com.fg.mail.smtp.client.request.factory.AgentReq;
import com.fg.mail.smtp.client.request.factory.BatchAgentReq;
import com.fg.mail.smtp.client.request.factory.SingleReqFactory;

import java.util.Map;
import java.util.Set;

/**
 * Http client for accessing restful interface of Smtp Agent
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/30/13 11:15 AM u_jli Exp $
 */
public class SmtpAgentClient {

    private JsonHttpClient jsonHttpClient;
    private SingleReqFactory reqFactory = new SingleReqFactory();

    public SmtpAgentClient(ConnectionConfig conCfg) {
        this.jsonHttpClient = new JsonHttpClient(conCfg);
    }

    public JsonHttpClient getJsonHttpClient() {
        return jsonHttpClient;
    }

    /**
     * @return map of recipient email address counts by client id
     */
    public String shutAgentDown() throws ClientNotAvailableException {
        return jsonHttpClient.resolveResult(reqFactory.forAgentShutdown());
    }

    /**
     * @return map of recipient email address counts by client id
     */
    public Map<String, Integer> pullRcptAddressCounts() throws ClientNotAvailableException {
        return jsonHttpClient.resolveResult(reqFactory.forRcptAddressCounts());
    }

    /**
     * @return map of recipient email addresses by client id
     */
    public Map<String, Set<String>> pullRcptAddresses() throws ClientNotAvailableException {
        return jsonHttpClient.resolveResult(reqFactory.forRcptAddresses());
    }

    /**
     * @return map of unknown bounces by client id
     */
    public Map<String, Set<SmtpLogEntry>> pullUnknownBounces() throws ClientNotAvailableException {
        return jsonHttpClient.resolveResult(reqFactory.forUnknownBounces());
    }

    /**
     * @param request with filter (narrow down the result set by mandatory clientId and optional rcptEmail, queueId or msgId) and query (result can be grouped and constrained by time and last entry)
     * @return all log entries for client id sorted by date and possibly grouped by a property or two and constrained by time or chronologically last log entry
     */
    public <T> T pull(AgentReq<T> request) throws ClientNotAvailableException {
        return jsonHttpClient.resolveResult(request);
    }

    /**
     * @param request with filter (narrow down the result set by mandatory clientId and optional rcptEmail, queueId or msgId) and query (result can be grouped and constrained by time and last entry)
     * @param callback to be supplied with 2 - x jobs from a batch based on provided timeframe
     */
    public <T> void pull(BatchAgentReq<T> request, JobCallback<T> callback) throws ClientNotAvailableException {
        jsonHttpClient.processBatch(request, callback);
    }

}
