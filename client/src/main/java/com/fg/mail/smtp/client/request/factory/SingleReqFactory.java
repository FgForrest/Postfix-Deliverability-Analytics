package com.fg.mail.smtp.client.request.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fg.mail.smtp.client.model.AgentResponse;
import com.fg.mail.smtp.client.model.SmtpLogEntry;
import com.fg.mail.smtp.client.request.filter.AppendablePath;
import com.fg.mail.smtp.client.request.filter.Eq;
import com.fg.mail.smtp.client.request.query.BySingle;
import com.fg.mail.smtp.client.request.query.ByTuple;
import com.fg.mail.smtp.client.request.query.QueryFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/7/13 9:21 PM u_jli Exp $
 */
public class SingleReqFactory implements RequestFactory {

    public SingleQueryFactory forClientId(String clientId) {
        return new SingleQueryFactory(new IndexFiltering(clientId));
    }

    public SingleQueryFactory forClientIdAnd(String clientId, Eq equals) {
        return new SingleQueryFactory(new IndexFiltering(clientId, equals));
    }

    public AgentReq<Map<String, Integer>> forRcptAddressCounts() {
        return new AgentReq<Map<String, Integer>>(new AppendablePath("agent-status").appendSegment("rcpt-address-counts"), new TypeReference<AgentResponse<Map<String, Integer>>>() {});
    }

    public AgentReq<String> forAgentShutdown() {
        return new AgentReq<String>(new AppendablePath("agent-shutdown"), new TypeReference<AgentResponse<String>>() {});
    }

    public AgentReq<Map<String, Set<String>>> forRcptAddresses() {
        return new AgentReq<Map<String, Set<String>>>(new AppendablePath("agent-status").appendSegment("rcpt-addresses"), new TypeReference<AgentResponse<Map<String, Set<String>>>>() {});
    }

    public AgentReq<Map<String, Set<SmtpLogEntry>>> forUnknownBounces() {
        return new AgentReq<Map<String, Set<SmtpLogEntry>>>(new AppendablePath("agent-status").appendSegment("unknown-bounces"), new TypeReference<AgentResponse<Map<String, Set<SmtpLogEntry>>>>() {});
    }

    public static class SingleQueryFactory implements QueryFactory {

        private IndexFiltering filtering;

        public SingleQueryFactory(IndexFiltering filtering) {
            this.filtering = filtering;
        }

        public AgentReq<TreeSet<SmtpLogEntry>> forTimeConstraining(@Nullable Long from, @Nullable Long to) {
            IndexQuery query = new IndexQuery(from, to, null, null);
            return new AgentReq<TreeSet<SmtpLogEntry>>(filtering, query, new TypeReference<AgentResponse<TreeSet<SmtpLogEntry>>>() {});
        }

        public AgentReq<SmtpLogEntry> forLastOrFirstConstraining(@Nullable Long from, @Nullable Long to, Boolean isLastOrFirst) {
            assert isLastOrFirst != null;
            IndexQuery query = new IndexQuery(from, to, isLastOrFirst, null);
            return new AgentReq<SmtpLogEntry>(filtering, query, new TypeReference<AgentResponse<SmtpLogEntry>>() {});
        }

        public AgentReq<Map<String, TreeSet<SmtpLogEntry>>> forGrouping(@Nullable Long from, @Nullable Long to, BySingle group) {
            assert group != null;
            IndexQuery query = new IndexQuery(from, to, null, group);
            return new AgentReq<Map<String, TreeSet<SmtpLogEntry>>>(filtering, query, new TypeReference<AgentResponse<Map<String, TreeSet<SmtpLogEntry>>>>() {});
        }

        public AgentReq<Map<String, Map<String, TreeSet<SmtpLogEntry>>>> forMultipleGrouping(@Nullable Long from, @Nullable Long to, ByTuple group) {
            assert group != null;
            IndexQuery query = new IndexQuery(from, to, null, group);
            return new AgentReq<Map<String, Map<String, TreeSet<SmtpLogEntry>>>>(filtering, query, new TypeReference<AgentResponse<Map<String, Map<String, TreeSet<SmtpLogEntry>>>>>() {});
        }

        public AgentReq<Map<String, Map<String, SmtpLogEntry>>> forConstraintMultipleGrouping(@Nullable Long from, @Nullable Long to, Boolean isLastOrFirst, ByTuple group) {
            assert group != null;
            assert isLastOrFirst != null;
            IndexQuery query = new IndexQuery(from, to, isLastOrFirst, group);
            return new AgentReq<Map<String, Map<String, SmtpLogEntry>>>(filtering, query, new TypeReference<AgentResponse<Map<String, Map<String, SmtpLogEntry>>>>() {});
        }

        public AgentReq<Map<String, SmtpLogEntry>> forConstrainedGrouping(@Nullable Long from, @Nullable Long to, Boolean isLastOrFirst, BySingle group) {
            assert group != null;
            assert isLastOrFirst != null;
            IndexQuery query = new IndexQuery(from, to, isLastOrFirst, group);
            return new AgentReq<Map<String, SmtpLogEntry>>(filtering, query, new TypeReference<AgentResponse<Map<String, SmtpLogEntry>>>() {});
        }

        public AgentReq<TreeSet<SmtpLogEntry>> queryLess() {
            return new AgentReq<TreeSet<SmtpLogEntry>>(filtering, null, new TypeReference<AgentResponse<TreeSet<SmtpLogEntry>>>() {});
        }
    }

}

