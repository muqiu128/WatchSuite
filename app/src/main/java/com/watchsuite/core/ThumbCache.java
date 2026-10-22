package com.watchsuite.core;

import android.graphics.Bitmap;

import com.watchsuite.MainActivity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 后台解码缩略图，带字节上限的 LRU 缓存（避免相册刷图把内存吃穿）。 */
public final class ThumbCache {

    public interface Loader {
        Bitmap load();
    }

    private static final int MAX_BYTES = 40 * 1024 * 1024;

    private static final android.util.LruCache<String, Bitmap> MAP =
            new android.util.LruCache<String, Bitmap>(MAX_BYTES) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getByteCount());
                }
            };

    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    /** 解码失败过的文件不再反复重试。 */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    private ThumbCache() {}

    /** 命中返回图；未命中发起后台解码并返回 null，完成后自动刷新界面。 */
    public static Bitmap get(MainActivity act, String key, Loader loader) {
        Bitmap b = MAP.get(key);
        if (b != null) return b;
        if (FAILED.contains(key)) return null;
        if (PENDING.add(key)) {
            POOL.execute(() -> {
                Bitmap r = null;
                try {
                    r = loader.load();
                } catch (Throwable ignored) {
                }
                if (r != null) MAP.put(key, r);
                else FAILED.add(key);
                PENDING.remove(key);
                act.ui(() -> {
                    if (act.shell != null) act.shell.invalidate();
                });
            });
        }
        return null;
    }

    public static void clear() {
        MAP.evictAll();
        FAILED.clear();
    }
}
