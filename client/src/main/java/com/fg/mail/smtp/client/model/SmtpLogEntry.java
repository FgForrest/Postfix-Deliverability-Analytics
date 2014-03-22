package com.fg.mail.smtp.client.model;

import java.util.Comparator;
import java.util.Date;

/**
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/30/13 7:02 PM u_jli Exp $
 */
public class SmtpLogEntry implements Comparable<SmtpLogEntry> {

    /* retrieved from logs */
    private Date date;
    private String queueId;
    private String msgId;
    private String rcptEmail;
    private String senderEmail;
    private String status;
    private String info;

    /* computed */
    /**
     * state enum 0 = soft bounce, 1 = hard bounce, 2 = unknown bounce
     */
    private Integer state;
    private String stateInfo;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getQueueId() {
        return queueId;
    }

    public void setQueueId(String queueId) {
        this.queueId = queueId;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getRcptEmail() {
        return rcptEmail;
    }

    public void setRcptEmail(String rcptEmail) {
        this.rcptEmail = rcptEmail;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
    }

    public String getStateInfo() {
        return stateInfo;
    }

    public void setStateInfo(String stateInfo) {
        this.stateInfo = stateInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SmtpLogEntry logEntry = (SmtpLogEntry) o;

        if (!date.equals(logEntry.date)) return false;
        if (info != null ? !info.equals(logEntry.info) : logEntry.info != null) return false;
        if (stateInfo != null ? !stateInfo.equals(logEntry.stateInfo) : logEntry.stateInfo != null) return false;
        if (!queueId.equals(logEntry.queueId)) return false;
        if (!msgId.equals(logEntry.msgId)) return false;
        if (rcptEmail != null ? !rcptEmail.equals(logEntry.rcptEmail) : logEntry.rcptEmail != null) return false;
        if (!status.equals(logEntry.status)) return false;
        if (!state.equals(logEntry.state)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = date.hashCode();
        result = 31 * result + queueId.hashCode();
        result = 31 * result + msgId.hashCode();
        result = 31 * result + rcptEmail.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + state.hashCode();
        result = 31 * result + (info != null ? info.hashCode() : 0);
        result = 31 * result + (stateInfo != null ? stateInfo.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "date=" + date +
                ", queueId='" + queueId + '\'' +
                ", msgId='" + msgId + '\'' +
                ", rcptEmail='" + rcptEmail + '\'' +
                ", senderEmail='" + senderEmail + '\'' +
                ", status='" + status + '\'' +
                ", info='" + info + '\'' +
                ", state='" + state + '\'' +
                ", stateInfo='" + stateInfo + '\'' +
                '}';
    }

    /* TreeSet would be sorted from newest to oldest */
    public int compareTo(SmtpLogEntry o) {
        if (this.date.after(o.date))
            return -1;
        else if (this.equals(o))
            return 0;
        else
            return 1;
    }

    public static class ReverseComparator implements Comparator<SmtpLogEntry> {

        /* TreeSet would be sorted from oldest to newest */
        public int compare(SmtpLogEntry o1, SmtpLogEntry o2) {
            if (o1.date.after(o2.date))
                return 1;
            else if (o1.equals(o2))
                return 0;
            else
                return -1;
        }
    }
}

