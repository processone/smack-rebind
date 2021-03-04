package net.processone.sm.packet;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import javax.xml.namespace.QName;

/**
 * Created by jpcarlino on 14/03/16.
 */
public class Rebind {
    public static final String NAMESPACE = "p1:rebind";

    public static class RebindFeature implements ExtensionElement {
        public static final String ELEMENT = "rebind";
        public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

        public static final RebindFeature INSTANCE = new RebindFeature();

        private RebindFeature() {
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        public CharSequence toXML(XmlEnvironment xmlEnvironment) {
            XmlStringBuilder xml = new XmlStringBuilder(this, xmlEnvironment);
            xml.closeEmptyElement();
            return xml;
        }
    }

    public static class RebindSession implements Nonza {
        public static final String ELEMENT = "rebind";
        public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

        public static final Rebind INSTANCE = new Rebind();

        private final String jid;

        private final String sid;

        public RebindSession(String jid, String sid) {
            this.jid = jid;
            this.sid = sid;
        }

        @Override
        public CharSequence toXML(XmlEnvironment xmlEnvironment) {
            XmlStringBuilder xml = new XmlStringBuilder(this, xmlEnvironment);
            xml.rightAngleBracket();
            xml.element("jid", jid);
            xml.element("sid", sid);
            xml.closeElement(ELEMENT);
            return xml;
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public final String getNamespace() {
            return NAMESPACE;
        }

        public String getJid() {
            return jid;
        }

        public String getSid() {
            return sid;
        }
    }

    public static class Success implements Nonza {
        public static final String ELEMENT = "rebind";
        public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

        public static final Success INSTANCE = new Success();

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public CharSequence toXML(XmlEnvironment xmlEnvironment) {
            XmlStringBuilder xml = new XmlStringBuilder(this, xmlEnvironment);
            xml.closeEmptyElement();
            return xml;
        }
    }

    public static class Failure implements Nonza {
        public static final String ELEMENT = "failure";
        public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

        private String message;

        public Failure() {
        }

        public Failure(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public CharSequence toXML(XmlEnvironment xmlEnvironment) {
            XmlStringBuilder xml = new XmlStringBuilder(this, xmlEnvironment);
            if (message != null) {
                xml.rightAngleBracket();
                xml.append(message);
                xml.closeElement(ELEMENT);
            } else {
                xml.closeEmptyElement();
            }
            return xml;
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

    }

}
