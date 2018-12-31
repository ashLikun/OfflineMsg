package com.qiuzhping.openfire.plugin;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
public class DefaultLocalCache
        implements ICache<String, CacheParams> {
    private static final Log Logger = LogFactory.getLog(DefaultLocalCache.class);
    private ClearCacheListener clearCacheListener;
    private static final DefaultLocalCache instance = new DefaultLocalCache();
    ConcurrentHashMap<String, SoftReference<CacheParams>>[] caches;
    ConcurrentHashMap<String, Long> expiryCache;
    private ScheduledExecutorService scheduleService;

    private void DefaultLocalCache() {
    }

    public static final DefaultLocalCache instance() {
        return instance;
    }

    private int expiryInterval = 10;
    private int moduleSize = 1;

    public DefaultLocalCache() {
        init();
    }

    public DefaultLocalCache(ClearCacheListener clearCacheListener) {
        this.clearCacheListener = clearCacheListener;
        init();
    }

    public DefaultLocalCache(int expiryInterval, int moduleSize, ClearCacheListener clearCacheListener) {
        this.expiryInterval = expiryInterval;
        this.moduleSize = moduleSize;
        this.clearCacheListener = clearCacheListener;
        init();
    }

    public DefaultLocalCache(int expiryInterval, int moduleSize) {
        this.expiryInterval = expiryInterval;
        this.moduleSize = moduleSize;
        init();
    }

    private void init() {
        this.caches = new ConcurrentHashMap[this.moduleSize];
        for (int i = 0; i < this.moduleSize; i++) {
            this.caches[i] = new ConcurrentHashMap();
        }
        this.expiryCache = new ConcurrentHashMap();

        this.scheduleService = Executors.newScheduledThreadPool(1);

        this.scheduleService.scheduleAtFixedRate(new CheckOutOfDateSchedule(this.caches, this.expiryCache), 0L, this.expiryInterval, TimeUnit.SECONDS);
        if (Logger.isInfoEnabled()) {
            Logger.info("DefaultCache sssssssdddddddddd");
        }
    }

    public boolean clear() {
        if (this.caches != null) {
            for (ConcurrentHashMap<String, SoftReference<CacheParams>> cache : this.caches) {
                cache.clear();
            }
        }
        if (this.expiryCache != null) {
            this.expiryCache.clear();
        }
        return true;
    }

    public boolean containsKey(String key) {
        return getCache(key).containsKey(key);
    }

    public CacheParams get(String key) {
        SoftReference<CacheParams> sr = getCache(key).get(key);
        if (sr == null) {
            this.expiryCache.remove(key);
            return null;
        }
        return sr.get();
    }

    public Set<String> keySet() {
        return this.expiryCache.keySet();
    }

    public CacheParams put(String key, CacheParams value) {
        return put(key, value, -1);
    }

    public CacheParams put(String key, CacheParams value, int outTime) {
        SoftReference<CacheParams> res = getCache(key).put(key, new SoftReference(value));
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, outTime);
        expiryCache.put(key, calendar.getTime().getTime());
        return res == null ? null : res.get();
    }

    public CacheParams put(String key, CacheParams value, Date expiry) {
        SoftReference<CacheParams> res = getCache(key).put(key, new SoftReference(value));
        expiryCache.put(key, expiry.getTime());
        return res == null ? null : res.get();
    }


    public void remove(String key) {
        getCache(key).clear();
        this.expiryCache.remove(key);
    }

    public int size() {
        return this.expiryCache.size();
    }

    public Collection<CacheParams> values() {
        Collection<CacheParams> values = new ArrayList();
        for (ConcurrentHashMap<String, SoftReference<CacheParams>> cache : this.caches) {
            for (SoftReference<CacheParams> sr : cache.values()) {
                values.add(sr.get());
            }
        }
        return values;
    }

    private ConcurrentHashMap<String, SoftReference<CacheParams>> getCache(String key) {
        long hashCode = key.hashCode();
        if (hashCode < 0L) {
            hashCode = -hashCode;
        }
        int moudleNum = (int) hashCode % this.moduleSize;
        return this.caches[moudleNum];
    }

    private void checkValidate(String key) {
        if (key != null && expiryCache.get(key) != null && expiryCache.get(key) != -1L && new Date(expiryCache.get(key)).before(new Date())) {
            try {
                CacheParams params = get(key);
                if (null != clearCacheListener && params != null) {
                    clearCacheListener.doClear(params);
                }
            } catch (Exception e) {

            }
            remove(key);
        }
    }

    private void checkAll() {
        Iterator<String> iter = this.expiryCache.keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            checkValidate(key);
        }
    }

    class CheckOutOfDateSchedule
            implements Runnable {
        ConcurrentHashMap<String, SoftReference<CacheParams>>[] caches;
        ConcurrentHashMap<String, Long> expiryCache;

        public CheckOutOfDateSchedule(ConcurrentHashMap<String, SoftReference<CacheParams>>[] caches, ConcurrentHashMap<String, Long> expiryCache) {
            this.caches = caches;
            this.expiryCache = expiryCache;
        }

        public void run() {
            check();
        }

        public void check() {
            for (ConcurrentHashMap<String, SoftReference<CacheParams>> cache : caches) {
                try {

                    Iterator<String> keys = cache.keySet().iterator();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (this.expiryCache.get(key) != null) {
                            long date = this.expiryCache.get(key);
                            if (date > 0 && new Date(date).before(new Date())) {
                                try {
                                    if (null != DefaultLocalCache.this.clearCacheListener) {
                                        if (cache.get(key) != null && cache.get(key).get() != null) {
                                            DefaultLocalCache.this.clearCacheListener.doClear(cache.get(key).get());
                                        }
                                    }
                                } catch (Exception e) {

                                }
                                this.expiryCache.remove(key);
                                cache.get(key).clear();
                                cache.remove(key);
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
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
