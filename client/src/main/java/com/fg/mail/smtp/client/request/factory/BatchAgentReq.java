package com.fg.mail.smtp.client.request.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fg.mail.smtp.client.ClientNotAvailableException;
import com.fg.mail.smtp.client.model.AgentResponse;
import com.fg.mail.smtp.client.request.filter.AgentUrlPath;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Distribution of a batch into 2 or more jobs.
 * It is defined by a time interval that splits batch into two or more jobs by creating an interval from timeframe.
 * Interval can be right-open if upper limit is not provided, last element is null in that case.
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/9/13 9:01 PM u_jli Exp $
 */
public class BatchAgentReq<T> extends AgentReq<T> {

    private final Collection<Long> times;

    public BatchAgentReq(@Nullable AgentUrlPath path, IndexQuery query, long batchTimeframe, TimeUnit batchTimeframeUnit, TypeReference<AgentResponse<T>> typeRef) {
        super(path, query, typeRef);
        assert query != null;
        assert query.getFrom() != null;
        assert batchTimeframeUnit != null;
        this.times = computeInterval(
                query.getFrom(),
                query.getTo() == null ? System.currentTimeMillis() : query.getTo(),
                batchTimeframeUnit.toMillis(batchTimeframe)
        );
        assert times.size() > 0;
    }

    /**
     * interval of the least possible size of 2, if the last element is null, the interval is right-open
     */
    private Collection<Long> computeInterval(long current, long to, long batchTimeframe) {
        List<Long> interval = new LinkedList<Long>();
        while (current < to) {
            interval.add(current);
            current += batchTimeframe;
        }
        interval.add(to);
        return interval;
    }

    public Collection<AgentReq<T>> getRequests() throws ClientNotAvailableException {
        ArrayList<AgentReq<T>> result = new ArrayList<AgentReq<T>>();
        Iterator<Long> iterator = times.iterator();

        if (!iterator.hasNext()) {
            throw new IllegalStateException("Please fix computeInterval method, it should never return empty collection !");
        }
        Long from = iterator.next();
        while (iterator.hasNext()) {
            Long to = iterator.next();
            result.add(new AgentReq<T>(path, query.copy(from, to), typeRef));
            from = to;
        }
        return result;
    }

}
