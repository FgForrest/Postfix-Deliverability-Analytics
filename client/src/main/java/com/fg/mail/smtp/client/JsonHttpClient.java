package com.fg.mail.smtp.client;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fg.mail.smtp.client.model.AgentResponse;
import com.fg.mail.smtp.client.model.ResponseStatus;
import com.fg.mail.smtp.client.request.factory.AgentReq;
import com.fg.mail.smtp.client.request.factory.BatchAgentReq;
import com.fg.mail.smtp.client.request.factory.IndexQuery;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/2/13 4:25 PM u_jli Exp $
 */
public class JsonHttpClient {
    private static final Log log = LogFactory.getLog(JsonHttpClient.class);

    private ObjectMapper mapper = new ObjectMapper();

    private ConnectionConfig conCfg;

    public JsonHttpClient(ConnectionConfig conCfg) {
        this.conCfg = conCfg;
    }

    protected <T> void processBatch(BatchAgentReq<T> request, JobCallback<T> callback) throws ClientNotAvailableException {
        for (AgentReq<T> req : request.getRequests()) {
            IndexQuery query = req.getQuery();
            callback.execute(resolveResult(req), query.getFrom(), query.getTo());
        }
    }

    protected <T> T resolveResult(AgentReq<T> request) throws ClientNotAvailableException {
        AgentResponse<T> response = resolve(request);
        ResponseStatus status = response.getStatus();
        if (status.getSucceeded()) {
            log.info("Request was successfully served. Result is ready for further processing");
            return response.getResult();
        } else {
            throw new ClientNotAvailableException(status.getMessage());
        }
    }

    public <T> AgentResponse<T> resolve(AgentReq<T> request) throws ClientNotAvailableException {
        AgentUrl url = new AgentUrl(conCfg.getHost(), conCfg.getPort(), request);
        long start = System.currentTimeMillis();
        URLConnection urlConn = openConnection(url.getURL());
        AgentResponse<T> result = deserialize(getInputStream(urlConn), request);
        log.info("Response retrieved and deserialized in " + (System.currentTimeMillis() - start) + " ms");
        return result;
    }

    protected <T> AgentResponse<T> deserialize(InputStream inputStream, AgentReq<T> request) throws ClientNotAvailableException {
        try {
            return mapper.readValue(new InputStreamReader(inputStream), request.getTypeRef());
        } catch (JsonMappingException e) {
            throw new IllegalStateException("Unexpected JSON mapping error occurred during deserialization", e);
        } catch (JsonParseException e) {
            throw new IllegalStateException("Unexpected JSON parsing error occurred during deserialization", e);
        } catch (IOException e) {
            throw new ClientNotAvailableException("Connection to remote server failed, unable to read the stream", e);
        }
    }

    private URLConnection openConnection(URL url) throws ClientNotAvailableException {
        log.info("Connecting to : " + url.toString() + " with connection timeout : " + conCfg.getConnectionTimeout()/1000 + " and read timeout : " + conCfg.getReadTimeout()/1000 + " seconds");
        try {
            return conCfg.configure(url.openConnection());
        } catch (IOException e) {
            throw new ClientNotAvailableException("Connection to remote server failed - unable to open connection - probably a networking problem !", e);
        }
    }

    private InputStream getInputStream(URLConnection urlConn) throws ClientNotAvailableException {
        try {
            return urlConn.getInputStream();
        } catch (IOException e) {
            if (urlConn instanceof HttpURLConnection) {
                try {
                    int responseCode = ((HttpURLConnection) urlConn).getResponseCode();
                    InputStream errorStream = ((HttpURLConnection) urlConn).getErrorStream();
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    try {
                        // read the response body
                        int ret;
                        while ((ret = errorStream.read(buf)) > 0) os.write(buf, 0, ret);
                    } finally {
                        // close the error stream
                        errorStream.close();
                    }
                    throw new ClientNotAvailableException("Error http response " + responseCode + ": " + new String(os.toByteArray()), e);
                } catch (IOException omg) {
                    throw new ClientNotAvailableException("Connection to remote server failed, unable to retrieve error code, connection was probably refused", e);
                }
            } else {
                throw new ClientNotAvailableException("Connection to remote server failed, unable to read the stream", e);
            }
        }
    }

    /**
     * helper method for testing purposes
     * @param request
     */
    public String resolveWithoutDeserialization(AgentReq request) {
        try {
            URLConnection urlConn = conCfg.configure(new AgentUrl(conCfg.getHost(), conCfg.getPort(), request).getURL().openConnection());
            BufferedReader reader = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
            String line = "";
            StringBuilder sb = new StringBuilder();
            do {
                sb.append(line);
                line = reader.readLine();
            } while (line != null);
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Connection to remote server failed !", e);
        }
    }

}
