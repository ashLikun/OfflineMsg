package com.qiuzhping.openfire.plugin;

import java.util.Collection;
import java.util.Date;
import java.util.Set;

/**
 * 作者　　: 李坤
 * 创建时间: 2018/12/29　15:53
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */


public abstract interface ICache<K, V> {
    public abstract V put(K key, V value);

    public abstract V put(K key, V value, Date paramDate);

    public abstract V put(K key, V value, int outTime);

    public abstract V get(K key);

    public abstract void remove(K key);

    public abstract boolean clear();

    public abstract int size();

    public abstract Set<K> keySet();

    public abstract Collection<V> values();

    public abstract boolean containsKey(K key);

    public abstract void destroy();
}
