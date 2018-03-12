package net.processone.sm.provider;

import net.processone.sm.packet.Rebind;

import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * Created by jpcarlino on 14/03/16.
 */
public class RebindFeatureProvider extends ExtensionElementProvider<Rebind.RebindFeature> {
    @Override
    public Rebind.RebindFeature parse(XmlPullParser parser, int initialDepth) throws Exception {
        return Rebind.RebindFeature.INSTANCE;
    }
}
