package com.fg.mail.smtp.client.request.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fg.mail.smtp.client.model.AgentResponse;
import com.fg.mail.smtp.client.model.SmtpLogEntry;
import com.fg.mail.smtp.client.request.filter.Eq;
import com.fg.mail.smtp.client.request.query.BySingle;
import com.fg.mail.smtp.client.request.query.ByTuple;
import com.fg.mail.smtp.client.request.query.QueryFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/9/13 7:59 PM u_jli Exp $
 */
public class BatchReqFactory implements RequestFactory {

    private long jobTimeframe;
    private TimeUnit jobTimeframeUnit;

    public BatchReqFactory(long jobTimeframe, TimeUnit jobTimeframeUnit) {
        this.jobTimeframe = jobTimeframe;
        this.jobTimeframeUnit = jobTimeframeUnit;
    }

    public BatchQueryFactory forClientId(String clientId) {
        return new BatchQueryFactory(new IndexFiltering(clientId), jobTimeframe, jobTimeframeUnit);
    }

    public BatchQueryFactory forClientIdAnd(String clientId, Eq equals) {
        return new BatchQueryFactory(new IndexFiltering(clientId, equals), jobTimeframe, jobTimeframeUnit);
    }

    public static class BatchQueryFactory implements QueryFactory {

        private IndexFiltering filtering;
        private long jobTimeframe;
        private TimeUnit jobTimeframeUnit;

        public BatchQueryFactory(IndexFiltering filtering, long jobTimeframe, TimeUnit jobTimeframeUnit) {
            this.filtering = filtering;
            this.jobTimeframe = jobTimeframe;
            this.jobTimeframeUnit = jobTimeframeUnit;
        }

        public BatchAgentReq<TreeSet<SmtpLogEntry>> forTimeConstraining(Long from, @Nullable Long to) {
            assert from != null;
            IndexQuery query = new IndexQuery(from, to, null, null);
            return new BatchAgentReq<TreeSet<SmtpLogEntry>>(filtering, query, jobTimeframe, jobTimeframeUnit, new TypeReference<AgentResponse<TreeSet<SmtpLogEntry>>>() {});
        }

        public BatchAgentReq<SmtpLogEntry> forLastOrFirstConstraining(Long from, @Nullable Long to, Boolean isLastOrFirst) {
            assert from != null;
            assert isLastOrFirst != null;
            IndexQuery query = new IndexQuery(from, to, isLastOrFirst, null);
            return new BatchAgentReq<SmtpLogEntry>(filtering, query, jobTimeframe, jobTimeframeUnit, new TypeReference<AgentResponse<SmtpLogEntry>>() {});
        }

        public BatchAgentReq<Map<String, TreeSet<SmtpLogEntry>>> forGrouping(Long from, @Nullable Long to, BySingle group) {
            assert from != null;
            assert group != null;
            IndexQuery query = new IndexQuery(from, to, null, group);
            return new BatchAgentReq<Map<String, TreeSet<SmtpLogEntry>>>(filtering, query, jobTimeframe, jobTimeframeUnit, new TypeReference<AgentResponse<Map<String, TreeSet<SmtpLogEntry>>>>() {});
        }

        public BatchAgentReq<Map<String, Map<String, TreeSet<SmtpLogEntry>>>> forMultipleGrouping(Long from, @Nullable Long to, ByTuple group) {
            assert from != null;
            assert group != null;
            IndexQuery query = new IndexQuery(from, to, null, group);
            return new BatchAgentReq<Map<String, Map<String, TreeSet<SmtpLogEntry>>>>(filtering, query, jobTimeframe, jobTimeframeUnit, new TypeReference<AgentResponse<Map<String, Map<String, TreeSet<SmtpLogEntry>>>>>() {});
        }

        public BatchAgentReq<Map<String, Map<String, SmtpLogEntry>>> forConstraintMultipleGrouping(Long from, @Nullable Long to, Boolean isLastOrFirst, ByTuple group) {
            assert from != null;
            assert group != null;
            assert isLastOrFirst != null;
            IndexQuery query = new IndexQuery(from, to, isLastOrFirst, group);
            return new BatchAgentReq<Map<String, Map<String, SmtpLogEntry>>>(filtering, query, jobTimeframe, jobTimeframeUnit, new TypeReference<AgentResponse<Map<String, Map<String, SmtpLogEntry>>>>() {});
        }

        public BatchAgentReq<Map<String, SmtpLogEntry>> forConstrainedGrouping(Long from, @Nullable Long to, Boolean isLastOrFirst, BySingle group) {
            assert from != null;
            assert group != null;
            assert isLastOrFirst != null;
            IndexQuery query = new IndexQuery(from, to, isLastOrFirst, group);
            return new BatchAgentReq<Map<String, SmtpLogEntry>>(filtering, query, jobTimeframe, jobTimeframeUnit, new TypeReference<AgentResponse<Map<String, SmtpLogEntry>>>() {});
        }

    }

}
