package com.qiuzhping.openfire.plugin;

import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * 作者　　: 李坤
 * 创建时间: 2018/12/29　16:06
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */

class CacheParams {
    Message message;
    JID recipient;
    Presence presence;
    IQ pingRequest;
}
