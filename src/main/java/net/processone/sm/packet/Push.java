package net.processone.sm.packet;

import org.jivesoftware.smack.packet.IQ;

/**
 * Declare data to pass in push stanza
 */
public class Push extends IQ {
    public static enum Send {all, firstPerUser, first, none}

    public static enum From {jid, username, name, none}

    public boolean sandbox;
    public int keepalive;
    public int session;
    public Send send;
    public boolean groupchat;
    public From from;
    public String status;
    public String statusMsg;
    public boolean offline;
    public String deviceType;
    public String deviceId;
    public String appId;

    /**
     * Create new push object
     *
     * @param keepalive time in second of inactivity after which session will switch to out of reception mode
     * @param session time in minutes that session will be keep alive on server without any server connection
     */
    public Push(int keepalive, int session) {
        this(false, keepalive, session, Send.all, false, From.none, null, null, false, null, null, null);
    }

    /**
     * Create new push object
     *
     * @param sandbox boolean value to denote if user production or sandbox server
     * @param keepalive time in second of inactivity after which session will switch to out of reception mode
     * @param session time in minutes that session will be keep alive on server without any server connection
     * @param send specify when push should be generated, for all messages, just for first from someone,
     *            for first one in general, or never
     * @param groupchat should push be generated for group chat messages
     * @param from how author name should be genarated in produces messages, specify jid to use jid address, username
     *            to use first part of jid, name to get name from roster, or none to skip that part entirely*
     * @param status type of presence to set when switching to out of reception mode
     * @param statusMsg message to set in presence in out of reception mode
     * @param offline boolean value used to specify if offline push delivery should be enabled
     * @param deviceType type of device should be set to "gcm"
     * @param deviceId push token received from OS push service
     * @param appId application id as defined on server
     */
    public Push(boolean sandbox, int keepalive, int session, Send send, boolean groupchat, From from, String status,
                String statusMsg, boolean offline, String deviceType, String deviceId, String appId) {
        super("push", "p1:push");
        this.sandbox = sandbox;
        this.keepalive = keepalive;
        this.session = session;
        this.send = send;
        this.groupchat = groupchat;
        this.from = from;
        this.status = status;
        this.statusMsg = statusMsg;
        this.offline = offline;
        this.deviceType = deviceType;
        this.deviceId = deviceId;
        this.appId = appId;
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
        xml.attribute("apns-sandbox", sandbox);
        xml.rightAngleBracket();
        xml.halfOpenElement("keepalive");
        xml.attribute("max", this.keepalive);
        xml.closeEmptyElement();
        xml.halfOpenElement("session");
        xml.attribute("duration", this.session);
        xml.closeEmptyElement();
        if (this.deviceId != null) {
            xml.halfOpenElement("body");
            xml.attribute("send", this.send);
            xml.attribute("groupchat", this.groupchat);
            xml.attribute("from", this.from);
            xml.closeEmptyElement();
            if (this.status != null) {
                xml.halfOpenElement("status");
                xml.attribute("type", this.status);
                xml.escape(this.statusMsg);
                xml.closeElement("status");
            }
            xml.openElement("offline");
            xml.escape(this.offline ? "true" : "false");
            xml.closeEmptyElement();
            xml.openElement("notification");
            xml.element("type", this.deviceType);
            xml.element("id", this.deviceId);
            xml.closeElement("notification");
            xml.element("appid", this.appId);
        }
        return xml;
    }
}
