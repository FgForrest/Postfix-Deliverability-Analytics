package com.fg.mail.smtp.client.request.query;

import com.fg.mail.smtp.client.request.Printable;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/16/13 9:10 AM u_jli Exp $
 */
public interface By extends Printable {

    public enum Property {
        rcptEmail, queueId, msgId
    }

}
