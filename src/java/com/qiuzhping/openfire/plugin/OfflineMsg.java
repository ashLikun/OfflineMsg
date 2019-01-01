package com.qiuzhping.openfire.plugin;

import org.jivesoftware.openfire.OfflineMessageStrategy;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
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

public class OfflineMsg implements PacketInterceptor, Plugin, ClearCacheListener {

    private static final Logger log = LoggerFactory.getLogger(OfflineMsg.class);
    public ICache<String, CacheParams> CACHE_CHAT_CONNECTION;
    //Hook for intercpetorn
    private InterceptorManager interceptorManager;
    private static PluginManager pluginManager;
    private UserManager userManager;
    private PresenceManager presenceManager;
    private RoutingTable routingTable;
    private SessionManager sessionManager;
    private OfflineMessageStrategy offlineMessageStrategy;
    PacketDeliverer deliverer;

    public OfflineMsg() {

    }

    public void debug(String str) {
        log.debug(str);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        interceptorManager = InterceptorManager.getInstance();
        interceptorManager.addInterceptor(this);
        CACHE_CHAT_CONNECTION = new DefaultLocalCache(1, 1, this);
        XMPPServer server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        offlineMessageStrategy = server.getOfflineMessageStrategy();
        presenceManager = server.getPresenceManager();
        routingTable = server.getRoutingTable();
        sessionManager = server.getSessionManager();
        deliverer = server.getPacketDeliverer();
        pluginManager = manager;
        this.debug("start offline 1644");
    }

    public void destroyPlugin() {
        CACHE_CHAT_CONNECTION.destroy();
        this.debug("destroy offline 1644");
    }

    /**
     * intercept message
     */
    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
        JID recipient = packet.getTo();
        if (recipient != null) {
            String username = recipient.getNode();
            // if broadcast message or user is not exist
            if (username == null || !UserManager.getInstance().isRegisteredUser(recipient)) {
                checkIq(packet);
                return;
            } else if (!XMPPServer.getInstance().getServerInfo().getXMPPDomain().equals(recipient.getDomain())) {
                return;
            }
        }
        // incoming表示本条消息刚进入openfire。processed为false，表示本条消息没有被openfire处理过。这说明这是一条处女消息，也就是没有被处理过的消息。
        if (processed || !incoming) {
            return;
        }
        this.doAction(packet, session);
    }

    private void checkIq(Packet packet) {
        if ((packet instanceof IQ)) {
            IQ iq = (IQ) packet;
            if (iq.getFrom().getResource() != null && iq.getFrom().getNode() != null && iq.getType() == IQ.Type.result
                    && CACHE_CHAT_CONNECTION.containsKey(iq.getID())
                    && (iq.getTo().getDomain().equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain()))) {
                debug("WWWWWWW----- remove" + iq.getTo().getDomain() + "     getFrom = " + iq.getFrom().getNode());
                CACHE_CHAT_CONNECTION.remove(iq.getID());
            }
        }
    }

    private void doAction(Packet packet, Session session) {
        Packet copyPacket = packet.createCopy();
        if (packet instanceof Message) {
            Message message = (Message) copyPacket;
            if (message.getType() == Message.Type.chat) {
                Message sendmessage = (Message) packet;
                String content = sendmessage.getBody();
                if (content != null) {
                    JID recipient = sendmessage.getTo();
                    try {
                        if (recipient.getNode() == null ||
                                !UserManager.getInstance().isRegisteredUser(recipient.getNode())) {
                            return;
                        }
                        Presence presence = presenceManager.getPresence(userManager.getUser(recipient.getNode()));
                        if (presence != null && presence.isAvailable()) {
                            sessionIdle(sendmessage, presence, session, recipient);
                        }
                    } catch (UserNotFoundException e) {
                        e.printStackTrace();
                        debug("WWWWWWW----- catch" + e.toString());
                    }
                }
            }
        }

    }

    public void sessionIdle(Message sendmessage, Presence presence, Session session, JID recipient) {
        final boolean doPing = JiveGlobals.getBooleanProperty(ConnectionSettings.Client.KEEP_ALIVE_PING, true);
        if (doPing) {
            if (recipient != null) {
                // Ping the connection to see if it is alive.
                final IQ pingRequest = new IQ(IQ.Type.get);
                pingRequest.setChildElement(IQPingHandler.ELEMENT_NAME,
                        IQPingHandler.NAMESPACE);
                pingRequest.setFrom(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
                pingRequest.setTo(recipient);
                CacheParams params = new CacheParams();
                params.recipient = recipient;
                params.message = sendmessage;
                params.presence = presence;
                params.pingRequest = pingRequest;
                try {
                    debug("WWWWWWW-----  start put");
                    CACHE_CHAT_CONNECTION.put(pingRequest.getID(), params, 5);
                    // JID is of the form <user@domain>

                    if (recipient.getResource() == null) {
                        for (JID route : routingTable.getRoutes(recipient, null)) {
                            debug("WWWWWWW-----  routePacket" + route.getNode());
                            routingTable.routePacket(route, pingRequest, false);
                        }
                    } else {
                        deliverer.deliver(pingRequest);
                    }
                    debug("WWWWWWW----- put = " + pingRequest.toXML());
                } catch (Exception e) {
                    e.printStackTrace();
                    CACHE_CHAT_CONNECTION.remove(pingRequest.getID());
                }
//                ClientSession clientSession = this.sessionManager.getSession(pingRequest.getFrom());
//
//                debug("WWWWWWW-----  clientSession = " + clientSession.getStatus());
//                debug("WWWWWWW-----  sessionIdle = " + pingRequest.toXML());
//                XMPPServer.getInstance().getIQRouter().addIQResultListener(pingRequest.getID(),
//                        new MyIQResultListener(recipient, presence, sendmessage), 5000);
//                XMPPServer.getInstance().getIQRouter().route(pingRequest);
            }
        }
    }

    private void userUnavailable(JID recipient, Presence presence, Message sendmessage) {
        //会话失效
        debug("WWWWWWW----- save    user = " + recipient.toFullJID());
        try {
            //插入离线消息
            offlineMessageStrategy.storeOffline(sendmessage);
            //清空内存缓存
            routingTable.removeClientRoute(recipient);
            //用户离线
            presenceManager.userUnavailable(presence);
        } catch (Exception e) {
            e.printStackTrace();
            debug("WWWWWWW----- save  catch  user = " + recipient.toFullJID() + "   e = " + e.toString());
        }

    }

    @Override
    public void doClear(CacheParams cacheParams) {
        if (cacheParams != null) {
            if (cacheParams.recipient != null && cacheParams.presence != null && cacheParams.message != null) {
                userUnavailable(cacheParams.recipient, cacheParams.presence, cacheParams.message);
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
            userUnavailable(recipient, presence, sendmessage);
        }
    }
}
