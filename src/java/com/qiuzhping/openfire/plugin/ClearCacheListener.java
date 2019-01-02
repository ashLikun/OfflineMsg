package com.qiuzhping.openfire.plugin;

/**
 * 作者　　: 李坤
 * 创建时间: 2018/12/29　15:54
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */

public abstract interface ClearCacheListener<V> {
    public abstract void doClear(V softReference);
}
