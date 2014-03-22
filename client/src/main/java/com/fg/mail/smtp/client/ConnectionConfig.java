package com.fg.mail.smtp.client;

import org.apache.commons.codec.binary.Base64;

import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/17/13 9:04 AM u_jli Exp $
 */
public class ConnectionConfig {

    public static final String httpUserAgent = "smtp-agent-http-client";

    private String clientVersion = SmtpAgentClient.class.getPackage().getImplementationVersion();
    private final String httpAuth;
    private final String host;
    private final int port;
    private final int connectionTimeout;
    private final int readTimeout;

    private Map<String, String> headers = new LinkedHashMap<String, String>();

    public ConnectionConfig(String host, int port, String httpAuth, int connectionTimeout, int readTimeout) {
        this.host = host;
        this.port = port;
        assert httpAuth != null && httpAuth.contains(":");
        this.httpAuth = "Basic " + Base64.encodeBase64String(httpAuth.getBytes());
        this.clientVersion = clientVersion != null ? clientVersion : "unknown";
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
    }

    public String addHeader(String name, String value) {
        return headers.put(name, value);
    }

    public void setHeaders(Map<String, String> headers) {
        headers.putAll(headers);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getHttpAuth() {
        return httpAuth;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public URLConnection configure(URLConnection conn) {
        conn.setRequestProperty("User-Agent", httpUserAgent);
        conn.setRequestProperty("client-version", clientVersion);
        conn.setRequestProperty("Authorization", httpAuth);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        conn.setConnectTimeout(connectionTimeout);
        conn.setReadTimeout(readTimeout);
        return conn;
    }

    @Override
    public String toString() {
        return "\nConnectionConfig {" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", clientVersion='" + clientVersion + '\'' +
                ", connectionTimeout=" + connectionTimeout +
                ", readTimeout=" + readTimeout +
                '}';
    }
}
