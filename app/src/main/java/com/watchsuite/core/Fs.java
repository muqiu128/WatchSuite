package com.watchsuite.core;

import android.annotation.SuppressLint;
import android.os.StatFs;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/** 文件系统小工具：体积换算、分类、扫描、删除。 */
public final class Fs {

    public static final String ROOT = "/storage/emulated/0";

    public static final String[] CATS = {"视频", "图片", "音频", "文档", "安装包", "压缩包", "其他"};

    private static final String[] VIDEO = {"mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "ts", "m4v", "rmvb", "wmv"};
    private static final String[] IMAGE = {"jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"};
    private static final String[] AUDIO = {"mp3", "m4a", "wav", "ogg", "aac", "flac", "amr", "opus", "wma", "mid"};
    private static final String[] DOC = {"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub", "csv"};
    private static final String[] ZIP = {"zip", "rar", "7z", "tar", "gz", "bz2", "xz"};

    private Fs() {}

    public static File root() {
        return new File(ROOT);
    }

    public static String human(long b) {
        if (b < 1024) return b + " B";
        double k = b / 1024.0;
        if (k < 1024) return String.format(Locale.US, "%.0f KB", k);
        double m = k / 1024.0;
        if (m < 1024) return String.format(Locale.US, "%.1f MB", m);
        double g = m / 1024.0;
        return String.format(Locale.US, "%.2f GB", g);
    }

    public static String date(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    /** 毫秒 → m:ss / h:mm:ss。 */
    public static String humanMs(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    public static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.US);
    }

    public static String cat(String name) {
        String e = ext(name);
        if (in(e, VIDEO)) return "视频";
        if (in(e, IMAGE)) return "图片";
        if (in(e, AUDIO)) return "音频";
        if (e.equals("apk")) return "安装包";
        if (in(e, DOC)) return "文档";
        if (in(e, ZIP)) return "压缩包";
        return "其他";
    }

    private static boolean in(String e, String[] arr) {
        for (String s : arr) if (s.equals(e)) return true;
        return false;
    }

    public static int catColor(String cat) {
        switch (cat) {
            case "视频": return 0xFF4DA3FF;
            case "图片": return 0xFF3DDC84;
            case "音频": return 0xFF9B7BFF;
            case "文档": return 0xFFFFB454;
            case "安装包": return 0xFF58D6C9;
            case "压缩包": return 0xFFFF8FA3;
            default: return 0xFF8CA0B4;
        }
    }

    public static String glyph(String name, boolean dir) {
        if (dir) return "夹";
        switch (cat(name)) {
            case "视频": return "播";
            case "图片": return "图";
            case "音频": return "音";
            case "文档": return "文";
            case "安装包": return "装";
            case "压缩包": return "压";
            default: return "件";
        }
    }

    public static String mime(String name) {
        String e = ext(name);
        if (e.equals("apk")) return "application/vnd.android.package-archive";
        if (in(e, VIDEO)) return "video/*";
        if (in(e, IMAGE)) return "image/*";
        if (in(e, AUDIO)) return "audio/*";
        if (e.equals("pdf")) return "application/pdf";
        if (e.equals("txt") || e.equals("md") || e.equals("csv") || e.equals("log")) return "text/plain";
        if (e.equals("zip")) return "application/zip";
        return "application/octet-stream";
    }

    /** 目录优先、再按名称排序。 */
    public static File[] list(File dir, boolean showHidden) {
        File[] fs = dir.listFiles();
        if (fs == null) return new File[0];
        ArrayList<File> out = new ArrayList<>();
        for (File f : fs) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            out.add(f);
        }
        File[] arr = out.toArray(new File[0]);
        Arrays.sort(arr, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return arr;
    }

    /**
     * 递归统计目录体积。用 Os.stat 一次拿全 inode 信息，比 File.length()
     * 快一个量级；跳过隐藏目录与 Android/data|obb（与存储索引口径一致）。
     */
    public static long dirSize(File dir) {
        long[] acc = new long[1];
        dirSize(dir, acc, 0);
        return acc[0];
    }

    private static void dirSize(File dir, long[] acc, int depth) {
        if (depth > 10) return;
        String[] ns;
        try {
            ns = dir.list();
        } catch (Throwable t) {
            return;
        }
        if (ns == null) return;
        for (String n : ns) {
            if (n.startsWith(".")) continue;
            String p = new File(dir, n).getAbsolutePath();
            try {
                android.system.StructStat st = android.system.Os.stat(p);
                if (android.system.OsConstants.S_ISDIR(st.st_mode)) {
                    String par = dir.getName();
                    if ("Android".equals(par) && (n.equals("data") || n.equals("obb"))) continue;
                    dirSize(new File(p), acc, depth + 1);
                } else {
                    acc[0] += st.st_size;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static long size(File f) {
        if (f == null) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids == null) return 0;
        for (File k : kids) total += size(k);
        return total;
    }

    public static boolean delete(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) delete(k);
        }
        return f.delete();
    }

    /** 递归计数目录内文件数量。 */
    public static int count(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return 1;
        int n = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += count(k);
        return n;
    }

    /** 存储总容量/可用空间（字节）。 */
    @SuppressLint("UsableSpace")
    public static long[] space() {
        try {
            StatFs s = new StatFs(ROOT);
            return new long[]{s.getTotalBytes(), s.getAvailableBytes()};
        } catch (Throwable t) {
            return new long[]{0, 0};
        }
    }

    /** 扫描过程中的统计容器。 */
    public static final class Stats {
        public final long[] byCat = new long[CATS.length];
        public final int[] filesByCat = new int[CATS.length];
        public long total = 0;
        public int files = 0;
        public String topPath;
        public long topSize = 0;

        public void add(File f) {
            add(f.getName(), f.length());
        }

        /** 直接按名字与体积入库，避免为了统计再创建 File 对象。 */
        public void add(String name, long sz) {
            String c = cat(name);
            int idx = 0;
            for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(c)) idx = i;
            byCat[idx] += sz;
            filesByCat[idx]++;
            total += sz;
            files++;
        }
    }

    public interface Progress {
        /** @return false 表示中止 */
        boolean onProgress(int visited);
    }

    /** 扫描整盘（跳过 Android/obb 与 Android/data，避免巨量缓存拖慢）。 */
    public static Stats scan(File root, Progress p) {
        Stats st = new Stats();
        walk(root, st, 0, p, new int[]{0});
        return st;
    }

    private static void walk(File dir, Stats st, int depth, Progress p, int[] visited) {
        if (depth > 12) return;
        File[] kids;
        try {
            kids = dir.listFiles();
        } catch (Throwable t) {
            return;
        }
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (n.equals("obb") && f.getParentFile() != null
                        && "Android".equals(f.getParentFile().getName())) continue;
                if (n.equals("data") && f.getParentFile() != null
                        && "Android".equals(f.getParentFile().getName())) continue;
                walk(f, st, depth + 1, p, visited);
            } else {
                st.add(f);
                visited[0]++;
                if (p != null && visited[0] % 400 == 0 && !p.onProgress(visited[0])) return;
            }
        }
    }

    /** 最近修改时间排序的文件列表。 */
    public static ArrayList<File> recent(File dir, int limit) {
        ArrayList<File> all = new ArrayList<>();
        collect(dir, all, 0);
        all.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        while (all.size() > limit) all.remove(all.size() - 1);
        return all;
    }

    private static void collect(File dir, ArrayList<File> out, int depth) {
        if (depth > 5) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) collect(f, out, depth + 1);
            else if (f.length() > 1024 * 1024) out.add(f);
        }
    }
}
