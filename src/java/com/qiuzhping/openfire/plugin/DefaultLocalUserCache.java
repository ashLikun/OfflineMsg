package com.qiuzhping.openfire.plugin;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 作者　　: 李坤
 * 创建时间: 2018/12/29　15:53
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */
public class DefaultLocalUserCache<K>
        implements ICache<K, CacheParams> {
    private static final Log Logger = LogFactory.getLog(DefaultLocalUserCache.class);
    private ClearCacheUserListener clearCacheListener;
    private static final DefaultLocalUserCache instance = new DefaultLocalUserCache();
    LinkedHashMap<K, SoftReference<CacheParams>>[] caches;
    LinkedHashMap<K, Long> expiryCache;
    private ScheduledExecutorService scheduleService;

    private void DefaultLocalUserCache() {
    }

    public static final DefaultLocalUserCache instance() {
        return instance;
    }

    private int expiryInterval = 10;
    private int moduleSize = 1;

    public DefaultLocalUserCache() {
        init();
    }

    public DefaultLocalUserCache(ClearCacheUserListener clearCacheListener) {
        this.clearCacheListener = clearCacheListener;
        init();
    }

    public DefaultLocalUserCache(int expiryInterval, int moduleSize, ClearCacheUserListener clearCacheListener) {
        this.expiryInterval = expiryInterval;
        this.moduleSize = moduleSize;
        this.clearCacheListener = clearCacheListener;
        init();
    }

    public DefaultLocalUserCache(int expiryInterval, int moduleSize) {
        this.expiryInterval = expiryInterval;
        this.moduleSize = moduleSize;
        init();
    }

    private void init() {
        this.caches = new LinkedHashMap[this.moduleSize];
        for (int i = 0; i < this.moduleSize; i++) {
            this.caches[i] = new LinkedHashMap();
        }
        this.expiryCache = new LinkedHashMap();

        this.scheduleService = Executors.newScheduledThreadPool(1);

        this.scheduleService.scheduleAtFixedRate(new CheckOutOfDateSchedule(this.caches, this.expiryCache), 0L, this.expiryInterval, TimeUnit.SECONDS);
    }

    public boolean clear() {
        if (this.caches != null) {
            for (LinkedHashMap<K, SoftReference<CacheParams>> cache : this.caches) {
                cache.clear();
            }
        }
        if (this.expiryCache != null) {
            this.expiryCache.clear();
        }
        return true;
    }

    public boolean containsKey(K key) {
        return getCache(key).containsKey(key);
    }

    public CacheParams get(K key) {
        SoftReference<CacheParams> sr = getCache(key).get(key);
        if (sr == null) {
            this.expiryCache.remove(key);
            return null;
        }
        return sr.get();
    }

    public Set<K> keySet() {
        return this.expiryCache.keySet();
    }

    public CacheParams put(K key, CacheParams value) {
        return put(key, value, -1);
    }

    public CacheParams put(K key, CacheParams value, int outTime) {
        SoftReference<CacheParams> res = getCache(key).put(key, new SoftReference(value));
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, outTime);
        expiryCache.put(key, calendar.getTime().getTime());
        return res == null ? null : res.get();
    }

    public CacheParams put(K key, CacheParams value, Date expiry) {
        SoftReference<CacheParams> res = getCache(key).put(key, new SoftReference(value));
        expiryCache.put(key, expiry.getTime());
        return res == null ? null : res.get();
    }


    public void remove(K key) {
        getCache(key).remove(key);
        this.expiryCache.remove(key);
    }

    public int size() {
        return this.expiryCache.size();
    }

    public Collection<CacheParams> values() {
        Collection<CacheParams> values = new ArrayList();
        for (LinkedHashMap<K, SoftReference<CacheParams>> cache : this.caches) {
            for (SoftReference<CacheParams> sr : cache.values()) {
                values.add(sr.get());
            }
        }
        return values;
    }

    private LinkedHashMap<K, SoftReference<CacheParams>> getCache(K key) {
        long hashCode = key.hashCode();
        if (hashCode < 0L) {
            hashCode = -hashCode;
        }
        int moudleNum = (int) hashCode % this.moduleSize;
        return this.caches[moudleNum];
    }


    class CheckOutOfDateSchedule
            implements Runnable {
        LinkedHashMap<K, SoftReference<CacheParams>>[] caches;
        LinkedHashMap<K, Long> expiryCache;

        public CheckOutOfDateSchedule(LinkedHashMap<K, SoftReference<CacheParams>>[] caches, LinkedHashMap<K, Long> expiryCache) {
            this.caches = caches;
            this.expiryCache = expiryCache;
        }

        public void run() {
            check();
        }

        public void check() {
            //超时的用户对于的消息集合
            LinkedHashMap<String, ArrayList<CacheParams>> userTimeOut = new LinkedHashMap<>();
            for (LinkedHashMap<K, SoftReference<CacheParams>> cache : caches) {
                try {
                    Iterator<K> keys = cache.keySet().iterator();
                    while (keys.hasNext()) {
                        K key = keys.next();
                        CacheParams value = cache.get(key).get();
                        String userName = "";
                        if (value != null && value.recipient != null) {
                            userName = value.recipient.getNode();
                            if (userName == null) {
                                userName = "";
                            }
                        }
                        if (userTimeOut.containsKey(userName)) {
                            //已经存在这个用户超时了，就直接把这个缓存加入集合
                            ArrayList<CacheParams> array = userTimeOut.get(userName);
                            if (array == null) {
                                array = new ArrayList<>();
                                userTimeOut.put(userName, array);
                            }
                            array.add(value);
                            this.expiryCache.remove(key);
                            cache.remove(key);
                            cache.remove(key);
                        } else if (this.expiryCache.get(key) != null) {
                            long date = this.expiryCache.get(key);
                            if (date > 0 && new Date(date).before(new Date())) {
                                //这个消息超时没回ping,直接加入用户超时的列表
                                ArrayList<CacheParams> array = new ArrayList<>();
                                array.add(value);
                                userTimeOut.put(userName, array);
                                this.expiryCache.remove(key);
                                cache.remove(key);
                                cache.remove(key);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Logger.debug("WWWWWWW----- user thread error  " + e.toString());
                }
            }
            try {
                if (!userTimeOut.isEmpty()) {
                    if (null != DefaultLocalUserCache.this.clearCacheListener) {
                        Iterator<String> keys = userTimeOut.keySet().iterator();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            DefaultLocalUserCache.this.clearCacheListener.doClear(userTimeOut.get(key));
                        }
                    }
                }
            } catch (Exception e) {
                Logger.debug("WWWWWWW----- user thread error2 " + e.toString());
            }

        }
    }


    public void destroy() {
        try {
            clear();
            if (this.scheduleService != null) {
                this.scheduleService.shutdown();
            }
            this.scheduleService = null;
        } catch (Exception ex) {
            Logger.error(ex);
        }
    }
}
