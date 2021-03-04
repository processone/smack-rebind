package org.jivesoftware.smack.tcp;

import net.processone.sm.packet.Push;
import net.processone.sm.packet.Rebind;
import net.processone.sm.provider.RebindFailureProvider;
import net.processone.sm.provider.RebindFeatureProvider;
import net.processone.sm.provider.RebindSuccessProvider;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
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
        ProviderManager.addNonzaProvider(RebindFailureProvider.INSTANCE);
        ProviderManager.addNonzaProvider(RebindSuccessProvider.INSTANCE);
    }

    private final ScheduledExecutorService whitespacePingService = Executors.newSingleThreadScheduledExecutor(
            new P1ThreadFactory(this, "whitespace pings"));
    private String rebindStreamId = null;
    private String rebindJid = null;
    private boolean pushEnabled;
    private int pingTimeout = 120;
    private boolean rebindAvailable = false;
    private ScheduledFuture<?> nextWhitespacePing;
    private final Runnable whitespacePingRunnable = () -> {
        try {
            writer.write("\n");
            writer.flush();
        } catch (IOException ignored) {
        }
        P1XMPPTCPConnection.this.scheduleNextWhitespacePing();
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
        return rebindAvailable && getRebindSID() != null;
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
            user = JidCreate.entityFullFromOrNull(this.rebindJid);
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
                if (sendAndWaitForResponse(
                        new Rebind.RebindSession(rebindJid, rebindStreamId),
                        Rebind.Success.class, Rebind.Failure.class).getElementName().equals("rebind"))
                {
                    LOGGER.fine("EBE rebind success");
                    // Stream rebound
                    afterSuccessfulLogin(true);
                    return;
                }
            }catch (Exception ignore) {
                LOGGER.fine("EBE rebind failed, continuing with normal stream establishment process");
                dropEBERebindState();
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
