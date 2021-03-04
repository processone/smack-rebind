package net.processone.sm.provider;

import net.processone.sm.packet.Rebind;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.NonzaProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

public class RebindFailureProvider extends NonzaProvider<Rebind.Failure> {
    public static final RebindFailureProvider INSTANCE = new RebindFailureProvider();

    @Override
    public Rebind.Failure parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment) throws XmlPullParserException, IOException, SmackParsingException {
        return new Rebind.Failure(parser.nextText());
    }
}
