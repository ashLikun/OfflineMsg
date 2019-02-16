package com.qiuzhping.openfire.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Message;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 作者　　: 李坤
 * 创建时间: 2019/2/14　17:03
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */

class HttpUtils {
    private static final Logger log = LoggerFactory.getLogger(OfflineMsg.class);
    public static ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(5);

    public static void postMessage(Message message) {
        if (message == null || message.getBody() == null || message.getTo() == null) {
            return;
        }
        scheduleService.execute(() -> {
            try {
                log.debug("WWWWWWW----- http start  " + message.getTo().getNode());
                //执行请求
                String urlPath = new String("http://localhost:8090/tools/admin_ajax.ashx");
                StringBuilder param = new StringBuilder("action=xmpp_offline_chat");
                param.append("&receiver=");
                param.append(message.getTo().getNode());
                param.append("&content=");
                param.append(message.getBody());
                //建立连接
                URL url = new URL(urlPath);
                HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                //设置参数
                httpConn.setDoOutput(true);     //需要输出
                httpConn.setDoInput(true);      //需要输入
                httpConn.setUseCaches(false);   //不允许缓存
                httpConn.setRequestMethod("POST");      //设置POST方式连接
                //设置请求属性
                httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                httpConn.setRequestProperty("Connection", "Keep-Alive");// 维持长连接
                httpConn.setRequestProperty("Charset", "UTF-8");
                httpConn.setRequestProperty("Content-Length", String.valueOf(param.toString().getBytes().length));
                //连接,也可以不用明文connect，使用下面的httpConn.getOutputStream()会自动connect
                httpConn.connect();
                //建立输入流，向指向的URL传入参数
                DataOutputStream dos = new DataOutputStream(httpConn.getOutputStream());
                dos.write(param.toString().getBytes("UTF-8"));
                dos.flush();
                dos.close();
                //获得响应状态
                int resultCode = httpConn.getResponseCode();
                if (HttpURLConnection.HTTP_OK == resultCode) {
                    StringBuffer sb = new StringBuffer();
                    String readLine;
                    BufferedReader responseReader = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), "UTF-8"));
                    while ((readLine = responseReader.readLine()) != null) {
                        sb.append(readLine).append("\n");
                    }
                    responseReader.close();
                    log.debug("WWWWWWW----- http ok  " + sb.toString());
                } else {
                    log.debug("WWWWWWW----- http code error  " + resultCode);
                }
            } catch (Exception e) {
                log.debug("WWWWWWW----- http error  " + e.toString());
            }

        });
    }
}
