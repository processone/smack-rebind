package net.processone.sm.provider;

import net.processone.sm.packet.Rebind;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Created by jpcarlino on 14/03/16.
 */
public class RebindFeatureProvider extends ExtensionElementProvider<Rebind.RebindFeature> {
    @Override
    public Rebind.RebindFeature parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment) throws XmlPullParserException, IOException, SmackParsingException {
        return Rebind.RebindFeature.INSTANCE;
    }
}
