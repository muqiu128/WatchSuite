package com.watchsuite.core;

import android.content.Context;

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
 */
public final class Wma {

    private static final Object LOCK = new Object();

    private Wma() {}

    public static boolean isWma(String path) {
        return path != null && path.toLowerCase(java.util.Locale.US).endsWith(".wma");
    }

    /**
     * 阻塞转码（后台线程调用）。成功返回缓存文件，失败返回 null。
     */
    public static File convert(Context c, String src) {
        synchronized (LOCK) {
            File dir = new File(c.getFilesDir(), "wma_cache");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(src);
            String key = src.replace('/', '_') + "_" + f.length() + "_" + f.lastModified();
            if (key.length() > 180) {
                key = key.substring(0, 90) + Integer.toHexString(key.hashCode()) + "_"
                        + key.substring(key.length() - 60);
            }
            File dst = new File(dir, key + ".m4a");
            if (dst.exists() && dst.length() > 0) return dst;

            File tmp = new File(dir, key + ".tmp.m4a");
            FFmpegSession s = FFmpegKit.executeWithArguments(new String[]{
                    "-y", "-i", src, "-vn", "-c:a", "aac", "-b:a", "64k",
                    tmp.getAbsolutePath()});
            if (s != null && ReturnCode.isSuccess(s.getReturnCode())
                    && tmp.exists() && tmp.length() > 0) {
                if (dst.exists()) dst.delete();
                tmp.renameTo(dst);
                return dst;
            }
            tmp.delete();
            return null;
        }
    }
}
