package com.fg.mail.smtp.client.request.query;

import com.fg.mail.smtp.client.request.factory.AgentReq;
import com.fg.mail.smtp.client.model.SmtpLogEntry;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.TreeSet;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/9/13 8:08 PM u_jli Exp $
 */
public interface QueryFactory {

    AgentReq<TreeSet<SmtpLogEntry>> forTimeConstraining(@Nullable Long from, @Nullable Long to);

    AgentReq<SmtpLogEntry> forLastOrFirstConstraining(@Nullable Long from, @Nullable Long to, Boolean isLastOrFirst);

    AgentReq<Map<String, TreeSet<SmtpLogEntry>>> forGrouping(@Nullable Long from, @Nullable Long to, BySingle group);

    AgentReq<Map<String, Map<String, TreeSet<SmtpLogEntry>>>> forMultipleGrouping(@Nullable Long from, @Nullable Long to, ByTuple group);

    AgentReq<Map<String, Map<String, SmtpLogEntry>>> forConstraintMultipleGrouping(@Nullable Long from, @Nullable Long to, Boolean isLastOrFirst, ByTuple group);

    AgentReq<Map<String, SmtpLogEntry>> forConstrainedGrouping(@Nullable Long from, @Nullable Long to, Boolean isLastOrFirst, BySingle group);
}
