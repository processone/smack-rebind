Rebind support for Smack
========================

This module adds ProcessOne rebind support to Smack java xmpp library.

To use this module you need to use `org.jivesoftware.smack.tcp.P1XMPPTCPConnection`
class in place of `XMPPTCPConnection` in your program.

This will give you access to couple methods that can be used to configure
push/rebind for that connection.

Usually your code for having rebind setup should look similar to this:

```
  Builder config = XMPPTCPConnectionConfiguration.builder();
  ...
  P1XMPPTCPConnection connection = new P1XMPPTCPConnection(config.build());
  ...
  if (haveSavedRebindState) {
    connection.setRebindState(rebindStateJid, rebindStateSid);
  }
  connection.connect();
  connection.login();
  if (connection.isPushPossible() && !connection.isPushEnabled()) {
    Push = new Push(...);
    connection.enablePush(Push);
    rebindStateJid = connection.getRebindJID();
    rebindStateSid = connection.getRebindSID();
    // store rebindStateJid and Sid in persistent storage here
  }
```
