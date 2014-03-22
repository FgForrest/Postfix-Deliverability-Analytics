package com.fg.mail.smtp.client.request.query;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/16/13 9:31 AM u_jli Exp $
 */
public class BySingle implements By {

    private Property property;

    public BySingle(Property property) {
        assert property != null;
        this.property = property;
    }

    public String print() {
        return property.toString();
    }
}
