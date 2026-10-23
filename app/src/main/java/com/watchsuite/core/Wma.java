package com.watchsuite.core;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;

/**
 * WMA → M4A 一次性转码。
 *
 * <p>这台手表的媒体框架没有 WMA 解码器（vendor 只配了 opus/raw，
 * Google 软解也不含 WMA），所以内置 FFmpeg（ffmpeg-kit-min，自带 wmadec），
 * 首次播放 .wma 时转成 M4A 缓存，之后直接播缓存。缓存放在应用私有目录，
 * 以「路径+大小+修改时间」区分，源文件变了会自动重转。
 *
 * <p>附带时长服务：手表的 MediaMetadataRetriever 同样读不了 WMA，
 * 转码完成后从 M4A 读出时长缓存（内存），列表/播放页直接用。
 */
public final class Wma {

    private static final Object LOCK = new Object();

    /** 源路径 → 时长 ms（0 = 读不到）。转码完成或读过 M4A 缓存后填。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> DURS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Wma() {}

    public static boolean isWma(String path) {
        return path != null && path.toLowerCase(java.util.Locale.US).endsWith(".wma");
    }

    static String cacheKey(String src) {
        File f = new File(src);
        String key = src.replace('/', '_') + "_" + f.length() + "_" + f.lastModified();
        if (key.length() > 180) {
            key = key.substring(0, 90) + Integer.toHexString(key.hashCode()) + "_"
                    + key.substring(key.length() - 60);
        }
        return key;
    }

    /** 该 WMA 对应的转码缓存文件（可能尚不存在）。 */
    public static File cachedFile(Context c, String src) {
        File dir = new File(c.getFilesDir(), "wma_cache");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, cacheKey(src) + ".m4a");
    }

    /**
     * 已知时长（ms）。只查内存，不碰磁盘；没转过返回 0。
     * 转码完成时会填，所以播放一次之后就有了。
     */
    public static long knownDur(String src) {
        Long d = DURS.get(src);
        return d == null ? 0 : d;
    }

    /**
     * 查时长：内存没有就看 M4A 缓存是否已存在（之前转过的），
     * 存在则读它的时长并缓存。读不到返回 0。
     */
    public static long cachedDur(Context c, String src) {
        Long d = DURS.get(src);
        if (d != null) return d;
        File f = cachedFile(c, src);
        if (!f.exists()) return 0;
        d = probeDur(f);
        DURS.put(src, d);
        return d;
    }

    private static long probeDur(File f) {
        try {
            MediaMetadataRetriever mm = new MediaMetadataRetriever();
            try {
                mm.setDataSource(f.getAbsolutePath());
                return Long.parseLong(mm.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION));
            } finally {
                mm.release();
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 阻塞转码（后台线程调用）。成功返回缓存文件，失败返回 null。
     * 成功后同时把时长写入内存缓存（DURS）。
     */
    public static File convert(Context c, String src) {
        synchronized (LOCK) {
            File dst = cachedFile(c, src);
            if (dst.exists() && dst.length() > 0) {
                if (!DURS.containsKey(src)) DURS.put(src, probeDur(dst));
                return dst;
            }

            File tmp = new File(dst.getParent(), cacheKey(src) + ".tmp.m4a");
            FFmpegSession s = FFmpegKit.executeWithArguments(new String[]{
                    "-y", "-i", src, "-vn", "-c:a", "aac", "-b:a", "64k",
                    tmp.getAbsolutePath()});
            if (s != null && ReturnCode.isSuccess(s.getReturnCode())
                    && tmp.exists() && tmp.length() > 0) {
                if (dst.exists()) dst.delete();
                tmp.renameTo(dst);
                DURS.put(src, probeDur(dst));
                return dst;
            }
            tmp.delete();
            return null;
        }
    }
}
