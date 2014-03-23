package com.fg.mail.smtp.client.javax;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.util.Random;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2014
 * @version $Id: 3/23/14 11:16 PM u_jli Exp $
 */
public class ClientIdMimeMessage extends MimeMessage {
    private static final Random RND = new SecureRandom();
    private static int ID;
    private final Integer id;
    private final String clientId;

    public static final String clientIdHashCode = String.valueOf("cid".hashCode()); // to recognize message-id with client-id information

    public ClientIdMimeMessage(Session session, String clientId, Integer id) {
        super(session);
        this.clientId = clientId;
        this.id = id;
    }

    @Override
    protected void updateMessageID() throws MessagingException {
        String id = this.id == null ? String.valueOf(ID++) : String.valueOf(this.id);
        setHeader("Message-ID", getUniqueMessageID(id, clientId));
    }

    /**
     * a newsletter client complained about hostname and user presence in message-id, now there will be only 'clientId' in message-id
     * clientIdHashCode.id.random@clientId
     * clientIdHashCode - hashCode of 'cid' string - to recognize a new message-id format
     * id - mail message id
     * random - enforcing 100% uniqueness
     */
    public static String getUniqueMessageID(String id, String clientId) {
        return "<" + clientIdHashCode + '.' + id + '.' + RND.nextInt(1000) + '@' + clientId + ">";
    }
}
