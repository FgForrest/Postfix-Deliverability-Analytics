package com.fg.mail.smtp.client.request.query;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/16/13 9:14 AM u_jli Exp $
 */
public class ByTuple implements By {

    private Property outer;
    private Property inner;

    public ByTuple(Property outer, Property inner) {
        assert inner != null;
        assert outer != null;
        this.outer = outer;
        this.inner = inner;
    }

    public String print() {
        return outer.toString() + "-and-" + inner.toString();
    }
}
