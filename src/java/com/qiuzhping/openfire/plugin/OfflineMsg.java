package com.qiuzhping.openfire.plugin;

import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.File;
/**
 * else if (message.getType() == Message.Type.groupchat) {
 * <p>
 * List<?> els = message.getElement().elements("x");
 * if (els != null && !els.isEmpty()) {
 * <p>
 * } else {
 * }
 * } else {
 * <p>
 * }
 * <p>
 * else if (packet instanceof IQ) {
 * <p>
 * IQ iq = (IQ) copyPacket;
 * <p>
 * if (iq.getType() == IQ.Type.set && iq.getChildElement() != null && "session".equals(iq.getChildElement().getName())) {
 * <p>
 * }
 * <p>
 * } else if (packet instanceof Presence) {
 * <p>
 * Presence presence = (Presence) copyPacket;
 * <p>
 * if (presence.getType() == Presence.Type.unavailable) {
 * <p>
 * <p>
 * }
 * <p>
 * }
 */

/**
 * 作者　　: 李坤
 * 创建时间: 2018/12/28　15:57
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */

public class OfflineMsg implements PacketInterceptor, Plugin {

    private static final Logger log = LoggerFactory.getLogger(OfflineMsg.class);

    //Hook for intercpetorn
    private InterceptorManager interceptorManager;
    private static PluginManager pluginManager;
    private UserManager userManager;
    private PresenceManager presenceManager;
    //    private OfflineMessageStore offlineMessageStore;
    private RoutingTable routingTable;

    public OfflineMsg() {

    }

    public void debug(String str) {
        log.debug(str);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        interceptorManager = InterceptorManager.getInstance();
        interceptorManager.addInterceptor(this);

        XMPPServer server = XMPPServer.getInstance();
        userManager = server.getUserManager();
//        offlineMessageStore = server.getOfflineMessageStore();
        presenceManager = server.getPresenceManager();
        routingTable = server.getRoutingTable();

        pluginManager = manager;

        this.debug("start offline 1640");

    }

    public void destroyPlugin() {
        this.debug("start offline 1640");
    }

    /**
     * intercept message
     */
    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {

//        this.debug("likun interceptPacket");
        JID recipient = packet.getTo();
        if (recipient != null) {
            String username = recipient.getNode();
            // if broadcast message or user is not exist
            if (username == null || !UserManager.getInstance().isRegisteredUser(recipient)) {
                return;
            } else if (!XMPPServer.getInstance().getServerInfo().getXMPPDomain().equals(recipient.getDomain())) {
                return;
            } else if ("".equals(recipient.getResource())) {
            }
        }
        this.doAction(packet, incoming, processed, session);
    }

    private void doAction(Packet packet, boolean incoming, boolean processed, Session session) {
        Packet copyPacket = packet.createCopy();
        if (packet instanceof Message) {
            debug("likun offlin doAction ");
            Message message = (Message) copyPacket;
            if (message.getType() == Message.Type.chat) {
                if (processed || !incoming) {
                    return;
                }
                Message sendmessage = (Message) packet;
                String content = sendmessage.getBody();
                JID recipient = sendmessage.getTo();
                try {
                    if (recipient.getNode() == null ||
                            !UserManager.getInstance().isRegisteredUser(recipient.getNode())) {
                        throw new UserNotFoundException("Username is null");
                    }
                    Presence presence = presenceManager.getPresence(userManager.getUser(recipient.getNode()));
                    debug("likun offlin doAction 44444" + presence.toXML());
                    debug("likun offlin doAction 44444" + sendmessage.toXML());
                    debug("likun offlin doAction 44444" + sendmessage.getTo().toBareJID());
                    if (presence != null) {
                        sessionIdle(sendmessage, presence, recipient);
                    }
                } catch (UserNotFoundException e) {
                    e.printStackTrace();
                    debug("likun offlin " + e.toString());
                }
            }
        }

    }

    public void sessionIdle(Message sendmessage, Presence presence, JID recipient) throws UserNotFoundException {
        final boolean doPing = JiveGlobals.getBooleanProperty(ConnectionSettings.Client.KEEP_ALIVE_PING, true);
        if (doPing) {
            if (recipient != null) {
                // Ping the connection to see if it is alive.
                final IQ pingRequest = new IQ(IQ.Type.get);
                pingRequest.setChildElement("ping",
                        IQPingHandler.NAMESPACE);
                pingRequest.setFrom(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
                pingRequest.setTo(recipient);
                debug("likun offlin doAction 44444" + pingRequest.toXML());
                XMPPServer.getInstance().getIQRouter().addIQResultListener(pingRequest.getID(),
                        new MyIQResultListener(recipient, presence, sendmessage), 5000);
                XMPPServer.getInstance().getIQRouter().route(pingRequest);
            }
        }
    }

    private class MyIQResultListener implements IQResultListener {
        Message sendmessage;
        Presence presence;
        JID recipient;

        public MyIQResultListener(JID recipient, Presence presence, Message sendmessage) {
            this.sendmessage = sendmessage;
            this.presence = presence;
            this.recipient = recipient;
        }

        @Override
        public void receivedAnswer(IQ iq) {
        }

        @Override
        public void answerTimeout(String s) {
            debug("likun offlin save    user = " + recipient.toFullJID());
            //插入离线消息
            //offlineMessageStore.addMessage(sendmessage);
            //清空内存缓存
            routingTable.removeClientRoute(recipient);
            //用户离线
            presenceManager.userUnavailable(presence);
        }
    }
}
