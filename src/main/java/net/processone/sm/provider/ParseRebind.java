package net.processone.sm.provider;

import net.processone.sm.packet.Rebind;

import org.jivesoftware.smack.util.ParserUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Created by jpcarlino on 14/03/16.
 */
public class ParseRebind {

    public static Rebind.SuccessRebind success(XmlPullParser parser) throws XmlPullParserException, IOException {
        ParserUtils.assertAtStartTag(parser);
        parser.next();
        ParserUtils.assertAtEndTag(parser);
        return new Rebind.SuccessRebind();
    }

    public static Rebind.Failure failure(XmlPullParser parser) throws XmlPullParserException, IOException {
        ParserUtils.assertAtStartTag(parser);
        String name;
        StringBuffer message = new StringBuffer();
        outerloop:
        while(true) {
            int event = parser.next();
            switch (event) {
                case XmlPullParser.TEXT:
                    message.append(parser.getText());
                    break;
                case XmlPullParser.END_TAG:
                    name = parser.getName();
                    if (Rebind.Failure.ELEMENT.equals(name)) {
                        break outerloop;
                    }
                    break;
            }
        }
        ParserUtils.assertAtEndTag(parser);
        return new Rebind.Failure(message.toString());
    }
}
