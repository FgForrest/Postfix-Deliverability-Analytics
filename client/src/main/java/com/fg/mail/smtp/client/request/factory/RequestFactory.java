package com.fg.mail.smtp.client.request.factory;

import com.fg.mail.smtp.client.request.filter.Eq;
import com.fg.mail.smtp.client.request.query.QueryFactory;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/9/13 8:01 PM u_jli Exp $
 */
public interface RequestFactory {

    QueryFactory forClientId(String clientId);

    QueryFactory forClientIdAnd(String clientId, Eq equals);
}
