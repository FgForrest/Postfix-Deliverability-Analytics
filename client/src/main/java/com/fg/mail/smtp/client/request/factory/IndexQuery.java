package com.fg.mail.smtp.client.request.factory;

import com.fg.mail.smtp.client.request.Printable;
import com.fg.mail.smtp.client.request.query.By;
import com.fg.mail.smtp.client.request.query.Grouping;
import com.fg.mail.smtp.client.request.query.LastOrFirst;
import com.fg.mail.smtp.client.request.query.TimeConstraint;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/5/13 8:38 AM u_jli Exp $
 */
public class IndexQuery implements TimeConstraint, Grouping, LastOrFirst, Printable {

    protected Long from;
    protected Long to;
    protected Boolean lastOrFirst;
    protected By property;

    protected IndexQuery(@Nullable Long from, @Nullable Long to, @Nullable Boolean lastOrFirst, @Nullable By property) {
        this.from = from;
        this.to = to;
        this.lastOrFirst = lastOrFirst;
        this.property = property;
    }

    public Long getFrom() {
        return from;
    }

    public Long getTo() {
        return to;
    }

    public Boolean getLastOrFirst() {
        return lastOrFirst;
    }

    public By getProperty() {
        return property;
    }

    public IndexQuery copy(@Nullable Long from, @Nullable Long to) {
        return new IndexQuery(from, to, lastOrFirst, property);
    }

    public String print() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put(TimeConstraint.P_NAME_FROM, from == null ? null : String.valueOf(from));
        m.put(TimeConstraint.P_NAME_TO, to == null ? null : String.valueOf(to));
        m.put(Grouping.P_NAME, property == null ? null : property.print());
        m.put(LastOrFirst.P_NAME, lastOrFirst == null ? null : lastOrFirst.toString());
        return buildQueryString(Lists.reverse(new ArrayList<Map.Entry<String, String>>(m.entrySet())).iterator(), "");
    }

    private String buildQueryString(Iterator<Map.Entry<String, String>> itr, String result) {
        if (itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            String value = entry.getValue();
            if (value != null) {
                if (result.length() > 0 && !result.startsWith("&")) {
                    result = "&" + result;
                }
                result = entry.getKey() + "=" + value + result;
            }
            return buildQueryString(itr, result);
        } else {
            return result.equals("") ? "" : "?" + result;
        }
    }

    public String toString() {
        return print();
    }

}
