package org.jivesoftware.smack.tcp;

import net.processone.sm.packet.Push;
import net.processone.sm.packet.Rebind;
import net.processone.sm.provider.ParseRebind;
import net.processone.sm.provider.RebindFeatureProvider;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Creates a tcp socket connection to an XMPP server that offer ProcessOne rebind functionality.
 *
 * @see XMPPTCPConnection
 */
public class P1XMPPTCPConnection extends XMPPTCPConnection {
    private static final Logger LOGGER = Logger.getLogger(XMPPTCPConnection.class.getName());

    static {
        ProviderManager.addStreamFeatureProvider("rebind", "p1:rebind",
                (ExtensionElementProvider<ExtensionElement>) (Object) new RebindFeatureProvider());
    }

    private final SynchronizationPoint<SmackException> rebindSyncPoint =
            new SynchronizationPoint<>(this, "EBE rebind element");
    private final ScheduledExecutorService whitespacePingService = Executors.newSingleThreadScheduledExecutor(
            new P1ThreadFactory(this, "whitespace pings"));
    private String rebindStreamId = null;
    private String rebindJid = null;
    private boolean useRebind = true;
    private boolean pushEnabled;
    private int pingTimeout = 120;
    private boolean rebindAvailable = false;
    private ScheduledFuture<?> nextWhitespacePing;
    private final Runnable whitespacePingRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                writer.write("\n");
                writer.flush();
            } catch (IOException ignored) {
            }
            P1XMPPTCPConnection.this.scheduleNextWhitespacePing();
        }
    };

    public P1XMPPTCPConnection(XMPPTCPConnectionConfiguration config) {
        super(config);
    }

    public P1XMPPTCPConnection(CharSequence jid, String password) throws XmppStringprepException {
        super(jid, password);
    }

    public P1XMPPTCPConnection(CharSequence username, String password, String serviceName) throws XmppStringprepException {
        super(username, password, serviceName);
    }

    /**
     * Change time between whitecpase pings that are sent to server
     *
     * @param seconds number to seconds between pings, use 0 to disable
     */
    public void setWhitespacePingTime(int seconds) {
        this.pingTimeout = seconds;
    }

    /**
     * Get sid from active session, can be used in call to {@link #setRebindState(String, String)}
     *
     * @return sid to use in next rebind
     */
    public String getRebindJID() {
        return rebindJid;
    }

    /**
     * Get jid from active session, can be used in call to {@link #setRebindState(String, String)}
     *
     * @return jid to use in next rebind
     */
    public String getRebindSID() {
        return rebindStreamId;
    }

    /**
     * Set parameters that can be used to restore previous session using rebind mechanism.
     * <p>
     * Parameters that can be used here can be gathered from calls to
     * {@link #getRebindJID()} and {@link #getRebindSID()} after successful {@link #enablePush(Push)}.
     * <p>
     * Calling this method works only when executed before {@link #connect()}, you don't need
     * to call it if you reuse object from previous connection - in that case rebind parameters
     * are already set.
     *
     * @param jid jid from previous session
     * @param sid session id from previous session
     */
    public void setRebindState(String jid, String sid) {
        if (!connected) {
            this.rebindStreamId = sid;
            this.rebindJid = jid;
        }
    }

    /**
     * Enable push in this session
     *
     * @param push Description of which parameters should be enabled
     * @return true if push was enabled, false otherwise
     */
    public boolean enablePush(Push push) throws SmackException.NotConnectedException, InterruptedException, XMPPException.XMPPErrorException, SmackException.NoResponseException {
        push.setType(IQ.Type.set);
        IQ response = createStanzaCollectorAndSend(push).nextResultOrThrow();
        this.pushEnabled = response.getType() == IQ.Type.result;
        if (this.pushEnabled) {
            scheduleNextWhitespacePing();
        }
        return this.pushEnabled;
    }

    /**
     * Disable push configured previously by calling {@link #enablePush(Push)}
     */
    public void disablePush() throws SmackException.NotConnectedException, InterruptedException, XMPPException.XMPPErrorException, SmackException.NoResponseException {
        IQ push = new SimpleIQ("disable", "p1:push") {
        };
        push.setType(IQ.Type.set);
        createStanzaCollectorAndSend(push).nextResultOrThrow();
        pushEnabled = false;
    }

    /**
     * Checks if push/rebind is enabled in this session
     *
     * @return true if push is enabled, false otherwise
     */
    public boolean isPushEnabled() {
        return pushEnabled;
    }

    /**
     * Checks if server offers push/rebind functionality
     *
     * @return true if server offers push, false otherwise
     */
    public boolean isPushPossible() {
        return rebindAvailable && useRebind && getRebindSID() != null;
    }

    private void scheduleNextWhitespacePing() {
        if (nextWhitespacePing != null)
            nextWhitespacePing.cancel(true);

        if (this.pushEnabled && this.pingTimeout > 0)
            nextWhitespacePing = whitespacePingService.schedule(whitespacePingRunnable,
                    this.pingTimeout, TimeUnit.SECONDS);
    }

    private void dropEBERebindState() {
        rebindStreamId = null;
        rebindAvailable = false;
    }

    @Override
    protected void afterSuccessfulLogin(final boolean resumed) throws SmackException.NotConnectedException, InterruptedException {
        if (resumed) {
            streamId = rebindStreamId;
            pushEnabled = true;
        } else {
            rebindStreamId = streamId;
            rebindJid = user.toString();
            pushEnabled = false;
        }
        super.afterSuccessfulLogin(resumed);
    }

    @Override
    protected synchronized void loginInternal(String username, String password, Resourcepart resource) throws XMPPException,
            SmackException, IOException, InterruptedException {
        rebindAvailable = hasFeature(Rebind.RebindFeature.ELEMENT, Rebind.NAMESPACE);
        if (isPushPossible()) {
            try {
                rebindSyncPoint.sendAndWaitForResponse(new Rebind.RebindSession(rebindJid,
                        rebindStreamId));
                if (rebindSyncPoint.wasSuccessful()) {
                    LOGGER.fine("EBE rebind success");
                    // Stream rebound
                    afterSuccessfulLogin(true);
                    return;
                }
                LOGGER.fine("EBE rebind failed, continuing with normal stream establishment process");
            } finally {
                if (!rebindSyncPoint.wasSuccessful()) {
                    dropEBERebindState();
                }
            }
        } else if (rebindAvailable) {
            LOGGER.fine("Can't rebind at this time");
        }
        super.loginInternal(username, password, resource);
    }

    @Override
    public void sendNonza(Nonza element) throws SmackException.NotConnectedException, InterruptedException {
        super.sendNonza(element);
        scheduleNextWhitespacePing();
    }

    @Override
    protected void sendStanzaInternal(Stanza packet) throws SmackException.NotConnectedException, InterruptedException {
        super.sendStanzaInternal(packet);
        scheduleNextWhitespacePing();
    }

    @Override
    void openStream() throws SmackException, InterruptedException {
        super.openStream();
        packetReader.parser = new XmppParserWrapper(packetReader.parser);
    }

class XmppParserWrapper implements XmlPullParser {
        private XmlPullParser baseParser;

        public XmppParserWrapper(XmlPullParser baseParser) {
            this.baseParser = baseParser;
        }

        @Override
        public void setFeature(String s, boolean b) throws XmlPullParserException {
            baseParser.setFeature(s, b);
        }

        @Override
        public boolean getFeature(String s) {
            return baseParser.getFeature(s);
        }

        @Override
        public void setProperty(String s, Object o) throws XmlPullParserException {
            baseParser.setProperty(s, o);
        }

        @Override
        public Object getProperty(String s) {
            return baseParser.getProperty(s);
        }

        @Override
        public void setInput(Reader reader) throws XmlPullParserException {
            baseParser.setInput(reader);
        }

        @Override
        public void setInput(InputStream inputStream, String s) throws XmlPullParserException {
            baseParser.setInput(inputStream, s);
        }

        @Override
        public String getInputEncoding() {
            return baseParser.getInputEncoding();
        }

        @Override
        public void defineEntityReplacementText(String s, String s1) throws XmlPullParserException {
            baseParser.defineEntityReplacementText(s, s1);
        }

        @Override
        public int getNamespaceCount(int i) throws XmlPullParserException {
            return baseParser.getNamespaceCount(i);
        }

        @Override
        public String getNamespacePrefix(int i) throws XmlPullParserException {
            return baseParser.getNamespacePrefix(i);
        }

        @Override
        public String getNamespaceUri(int i) throws XmlPullParserException {
            return baseParser.getNamespaceUri(i);
        }

        @Override
        public String getNamespace(String s) {
            return baseParser.getNamespace(s);
        }

        @Override
        public int getDepth() {
            return baseParser.getDepth();
        }

        @Override
        public String getPositionDescription() {
            return baseParser.getPositionDescription();
        }

        @Override
        public int getLineNumber() {
            return baseParser.getLineNumber();
        }

        @Override
        public int getColumnNumber() {
            return baseParser.getColumnNumber();
        }

        @Override
        public boolean isWhitespace() throws XmlPullParserException {
            return baseParser.isWhitespace();
        }

        @Override
        public String getText() {
            return baseParser.getText();
        }

        @Override
        public char[] getTextCharacters(int[] ints) {
            return baseParser.getTextCharacters(ints);
        }

        @Override
        public String getNamespace() {
            return baseParser.getNamespace();
        }

        @Override
        public String getName() {
            return baseParser.getName();
        }

        @Override
        public String getPrefix() {
            return baseParser.getPrefix();
        }

        @Override
        public boolean isEmptyElementTag() throws XmlPullParserException {
            return baseParser.isEmptyElementTag();
        }

        @Override
        public int getAttributeCount() {
            return baseParser.getAttributeCount();
        }

        @Override
        public String getAttributeNamespace(int i) {
            return baseParser.getAttributeNamespace(i);
        }

        @Override
        public String getAttributeName(int i) {
            return baseParser.getAttributeName(i);
        }

        @Override
        public String getAttributePrefix(int i) {
            return baseParser.getAttributePrefix(i);
        }

        @Override
        public String getAttributeType(int i) {
            return baseParser.getAttributeType(i);
        }

        @Override
        public boolean isAttributeDefault(int i) {
            return baseParser.isAttributeDefault(i);
        }

        @Override
        public String getAttributeValue(int i) {
            return baseParser.getAttributeValue(i);
        }

        @Override
        public String getAttributeValue(String s, String s1) {
            return baseParser.getAttributeValue(s, s1);
        }

        @Override
        public int getEventType() throws XmlPullParserException {
            return baseParser.getEventType();
        }

        @Override
        public int next() throws XmlPullParserException, IOException {
            int event = baseParser.next();
            if (event == XmlPullParser.START_TAG && baseParser.getDepth() == 2) {
                String name = baseParser.getName();
                if (name.equals("rebind")) {
                    ParseRebind.success(baseParser);
                    rebindSyncPoint.reportSuccess();
                    return baseParser.next();
                } else if (name.equals("failure") && baseParser.getNamespace().equals("p1:rebind")) {
                    Rebind.Failure rebindFailure = ParseRebind.failure(baseParser);
                    SmackException smackException = new SmackException(rebindFailure.getMessage());
                    if (rebindSyncPoint.requestSent()) {
                        rebindSyncPoint.reportFailure(smackException);
                    }
                }
            }
            return event;
        }

        @Override
        public int nextToken() throws XmlPullParserException, IOException {
            return baseParser.nextToken();
        }

        @Override
        public void require(int i, String s, String s1) throws XmlPullParserException, IOException {
            baseParser.require(i, s, s1);
        }

        @Override
        public String nextText() throws XmlPullParserException, IOException {
            return baseParser.nextText();
        }

        @Override
        public int nextTag() throws XmlPullParserException, IOException {
            return baseParser.nextTag();
        }
    }


private final class P1ThreadFactory implements ThreadFactory {
    private final int connectionCounterValue;
    private final String name;
    private int count = 0;

    public P1ThreadFactory(XMPPConnection connection, String name) {
        this.connectionCounterValue = connection.getConnectionCounter();
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("Smack-" + name + ' ' + count++ + " (" + connectionCounterValue + ")");
        thread.setDaemon(true);
        return thread;
    }
}

}
