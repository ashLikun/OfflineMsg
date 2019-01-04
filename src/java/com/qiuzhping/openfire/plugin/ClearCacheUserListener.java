package com.qiuzhping.openfire.plugin;

import java.util.ArrayList;

/**
 * 作者　　: 李坤
 * 创建时间: 2018/12/29　15:54
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */

public abstract interface ClearCacheUserListener {
    public abstract void doClear(ArrayList<CacheParams> softReference);
}
