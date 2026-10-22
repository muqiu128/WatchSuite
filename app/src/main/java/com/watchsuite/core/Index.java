package com.watchsuite.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 一次遍历、全应用共用的一份存储索引。
 *
 * <p>以前 视频/音乐/相册/分析/清理 各自把整盘走一遍（5 次），每次都靠
 * File.length()/isDirectory()/lastModified() 各触发一次 stat。现在改为：
 * ① 单次遍历，用 Os.stat 一次拿全 inode 信息（stat 调用从 3 次降到 1 次）；
 * ② 顶层目录 4 线程并行；
 * ③ 结果落盘缓存，下次开应用先用缓存、再后台刷新。
 */
public final class Index {

    public interface Callback {
        /** 可能在主线程被调用两次：先旧缓存，后新结果。 */
        void onIndex(Snap s);
    }

    /** 一份完整的存储快照。 */
    public static final class Snap {
        public final Fs.Stats stats = new Fs.Stats();
        public final ArrayList<MediaScan.Item> videos = new ArrayList<>();
        public final ArrayList<MediaScan.Item> audios = new ArrayList<>();
        public final ArrayList<MediaScan.Item> images = new ArrayList<>();
        public final ArrayList<File> logs = new ArrayList<>();
        public final ArrayList<File> apks = new ArrayList<>();
        public final ArrayList<File> big = new ArrayList<>();
        public final ArrayList<File> emptyDirs = new ArrayList<>();
        public final ArrayList<File> top = new ArrayList<>();
        public long builtAt;

        private static final long BIG = 200L * 1024 * 1024;

        synchronized void add(String path, String name, long size, long mtime) {
            stats.add(name, size);
            String cat = Fs.cat(name);
            if ("视频".equals(cat)) {
                videos.add(item(path, name, size, mtime));
            } else if ("音频".equals(cat)) {
                audios.add(item(path, name, size, mtime));
            } else if ("图片".equals(cat)) {
                images.add(item(path, name, size, mtime));
            }
            String ext = Fs.ext(name);
            if ("log".equals(ext)) {
                if (logs.size() < 800) logs.add(new File(path));
            } else if ("apk".equals(ext) && path.contains("/Download/") && apks.size() < 200) {
                apks.add(new File(path));
            }
            if (size >= BIG && big.size() < 150) big.add(new File(path));
            if (size > 4L * 1024 * 1024) {
                top.add(new File(path));
                if (top.size() > 24) {
                    Collections.sort(top, (a, b) -> Long.compare(b.length(), a.length()));
                    while (top.size() > 8) top.remove(top.size() - 1);
                }
            }
        }

        synchronized void empty(File dir) {
            if (emptyDirs.size() < 400) emptyDirs.add(dir);
        }

        private static MediaScan.Item item(String path, String name, long size, long mtime) {
            MediaScan.Item it = new MediaScan.Item();
            it.path = path;
            it.name = name;
            it.size = size;
            it.modified = mtime;
            return it;
        }

        void finish() {
            // 所有媒体列表统一按「体积从大到小」，一眼看到最占地方的东西
            java.util.Comparator<MediaScan.Item> bySize =
                    (a, b) -> Long.compare(b.size, a.size);
            Collections.sort(videos, bySize);
            Collections.sort(audios, bySize);
            Collections.sort(images, bySize);
            Collections.sort(top, (a, b) -> Long.compare(b.length(), a.length()));
            while (top.size() > 8) top.remove(top.size() - 1);
            if (!top.isEmpty()) {
                stats.topSize = top.get(0).length();
                stats.topPath = top.get(0).getAbsolutePath();
            }
        }
    }

    private static final long TTL = 20 * 60 * 1000L;
    private static volatile Snap cur;

    private static final ExecutorService BUILD = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Index() {}

    /** 内存里已有的快照，没有则返回 null（不会触发磁盘读取）。 */
    public static Snap get() {
        return cur;
    }

    /**
     * 取索引：先回调旧缓存（秒开），需要时再后台重建并回调新结果。
     * 回调都在主线程。
     */
    public static void ensure(final Context ctx, final boolean force, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        BUILD.execute(() -> {
            Snap s = cur;
            if (s == null) {
                s = loadDisk(app);
                if (s != null) cur = s;
            }
            if (s != null) post(cb, s);
            boolean stale = force || s == null
                    || System.currentTimeMillis() - s.builtAt > TTL;
            if (!stale) return;
            Snap fresh = build();
            cur = fresh;
            saveDisk(app, fresh);
            post(cb, fresh);
        });
    }

    private static void post(final Callback cb, final Snap s) {
        MAIN.post(() -> cb.onIndex(s));
    }

    /**
     * 同步重建（清理完垃圾后立刻要新数据时用）：返回时 cur 与磁盘缓存都已是新快照。
     * 只能在后台线程调用。
     */
    public static synchronized Snap rebuild(Context ctx) {
        Snap s = build();
        cur = s;
        saveDisk(ctx.getApplicationContext(), s);
        return s;
    }

    // ---------------- 遍历 ----------------

    private static Snap build() {
        long t0 = System.currentTimeMillis();
        final Snap s = new Snap();
        File[] tops = Fs.root().listFiles();
        if (tops != null && tops.length > 0) {
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Future<?>> jobs = new ArrayList<>();
            for (final File f : tops) {
                String n = f.getName();
                if (n.equals("Android") || n.startsWith(".")) continue;
                try {
                    jobs.add(pool.submit(() -> walk(f, 0, s)));
                } catch (Throwable ignored) {
                }
            }
            for (Future<?> j : jobs) {
                try {
                    j.get();
                } catch (Throwable ignored) {
                }
            }
            pool.shutdown();
        }
        s.builtAt = System.currentTimeMillis();
        s.finish();
        android.util.Log.i("WatchSuite", "index built: " + s.stats.files + " files, "
                + (s.builtAt - t0) + " ms");
        return s;
    }

    private static void walk(File dir, int depth, Snap s) {
        if (depth > 12) return;
        String[] names;
        try {
            names = dir.list();
        } catch (Throwable t) {
            return;
        }
        if (names == null) return;
        if (names.length == 0) {
            if (depth > 0) s.empty(dir);
            return;
        }
        for (String nm : names) {
            String p = new File(dir, nm).getAbsolutePath();
            StructStat st;
            try {
                st = Os.stat(p);
            } catch (Throwable t) {
                continue;
            }
            if (OsConstants.S_ISDIR(st.st_mode)) {
                if (nm.equals("data") || nm.equals("obb")) {
                    if ("Android".equals(dir.getName())) continue;
                }
                if (nm.startsWith(".") && !nm.equals(".thumbnails")) continue;
                walk(new File(dir, nm), depth + 1, s);
            } else {
                s.add(p, nm, st.st_size, st.st_mtime * 1000L);
            }
        }
    }

    // ---------------- 磁盘缓存 ----------------

    private static File cacheFile(Context c) {
        return new File(c.getFilesDir(), "storage.idx");
    }

    private static void saveDisk(Context c, Snap s) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(cacheFile(c)), "UTF-8"), 1 << 16);
            StringBuilder sb = new StringBuilder(256);
            sb.append("S|").append(s.builtAt).append('|').append(s.stats.total)
                    .append('|').append(s.stats.files);
            for (long v : s.stats.byCat) sb.append('|').append(v);
            for (int v : s.stats.filesByCat) sb.append('|').append(v);
            w.write(sb.toString());
            w.write('\n');
            for (MediaScan.Item it : s.images) w.write("U|" + it.size + "|" + it.modified + "|" + it.path + "\n");
            for (MediaScan.Item it : s.videos) w.write("V|" + it.size + "|" + it.modified + "|" + it.path + "\n");
            for (MediaScan.Item it : s.audios) w.write("A|" + it.size + "|" + it.modified + "|" + it.path + "\n");
            for (File f : s.logs) w.write("G|" + f.length() + "|" + f.getAbsolutePath() + "\n");
            for (File f : s.apks) w.write("P|" + f.length() + "|" + f.getAbsolutePath() + "\n");
            for (File f : s.big) w.write("B|" + f.length() + "|" + f.getAbsolutePath() + "\n");
            for (File f : s.emptyDirs) w.write("E|" + f.getAbsolutePath() + "\n");
            for (File f : s.top) w.write("T|" + f.length() + "|" + f.getAbsolutePath() + "\n");
        } catch (Throwable ignored) {
        } finally {
            if (w != null) try {
                w.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static Snap loadDisk(Context c) {
        File f = cacheFile(c);
        if (!f.exists()) return null;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 1 << 16);
            Snap s = new Snap();
            String line = r.readLine();
            if (line == null || !line.startsWith("S|")) return null;
            String[] p = line.split("\\|");
            s.builtAt = Long.parseLong(p[1]);
            s.stats.total = Long.parseLong(p[2]);
            s.stats.files = Integer.parseInt(p[3]);
            int cats = Fs.CATS.length;
            for (int i = 0; i < cats; i++) s.stats.byCat[i] = Long.parseLong(p[4 + i]);
            for (int i = 0; i < cats; i++) s.stats.filesByCat[i] = Integer.parseInt(p[4 + cats + i]);
            while ((line = r.readLine()) != null) {
                int a = line.indexOf('|');
                if (a < 0) continue;
                char t = line.charAt(0);
                String rest = line.substring(a + 1);
                switch (t) {
                    case 'U':
                    case 'V':
                    case 'A': {
                        int b1 = rest.indexOf('|');
                        int b2 = rest.indexOf('|', b1 + 1);
                        if (b2 < 0) break;
                        long size = Long.parseLong(rest.substring(0, b1));
                        long mt = Long.parseLong(rest.substring(b1 + 1, b2));
                        String path = rest.substring(b2 + 1);
                        String name = path.substring(path.lastIndexOf('/') + 1);
                        MediaScan.Item it = Snap.item(path, name, size, mt);
                        if (t == 'U') s.images.add(it);
                        else if (t == 'V') s.videos.add(it);
                        else s.audios.add(it);
                        break;
                    }
                    case 'G':
                    case 'P':
                    case 'B':
                    case 'T': {
                        int b1 = rest.indexOf('|');
                        if (b1 < 0) break;
                        File ff = new File(rest.substring(b1 + 1));
                        if (t == 'G') s.logs.add(ff);
                        else if (t == 'P') s.apks.add(ff);
                        else if (t == 'B') s.big.add(ff);
                        else s.top.add(ff);
                        break;
                    }
                    case 'E':
                        s.emptyDirs.add(new File(rest));
                        break;
                    default:
                        break;
                }
            }
            if (!s.top.isEmpty()) {
                s.stats.topSize = s.top.get(0).length();
                s.stats.topPath = s.top.get(0).getAbsolutePath();
            }
            s.finish(); // 旧缓存可能是按时间存的，统一重排成体积降序
            return s;
        } catch (Throwable t) {
            return null;
        } finally {
            if (r != null) try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
