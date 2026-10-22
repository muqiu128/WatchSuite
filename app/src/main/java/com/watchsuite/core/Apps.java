package com.watchsuite.core;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.storage.StorageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 已安装应用的占用统计。
 *
 * <p>优先走 StorageStatsManager（需要 usage-access 授权），能拿到
 * 安装包 / 数据 / 缓存三段的精确体积；授权缺失时退化为
 * 「外部数据目录 + APK 文件大小」的估算，至少能看到量级。
 */
public final class Apps {

    public static final class App {
        public String pkg = "";
        public String label = "";
        public long code, data, cache;
        /** -1 表示拿不到体积。 */
        public long size = -1;
        public boolean system;
        public boolean exact;
        public Drawable icon;
    }

    /** 内存缓存有效期。 */
    private static final long TTL = 5 * 60 * 1000L;
    /**
     * 磁盘快照有效期。OPPO 等 ROM 对「枚举应用列表」按进程弹隐私询问，
     * 应用体积变化不快，快照没过期就完全不碰那个 API，避免每次大退重进都被问。
     */
    private static final long SNAP_TTL = 12 * 60 * 60 * 1000L;
    private static ArrayList<App> cur;
    private static long builtAt;

    private Apps() {}

    /** 最近一次成功读到的应用列表（可能已过期），用于大退重进时秒开。 */
    public static ArrayList<App> cached() {
        return cur;
    }

    /**
     * 读取应用占用快照。snapshot 为上次持久化到磁盘的结果（可为 null）。
     *
     * <p>顺序：内存缓存 → 未过期的磁盘快照（不触发 ROM 询问）→ 重新枚举。
     */
    public static synchronized ArrayList<App> list(Context ctx, ArrayList<App> snapshot) {
        if (cur != null && !cur.isEmpty()
                && System.currentTimeMillis() - builtAt < TTL) return cur;
        if (snapshot != null && !snapshot.isEmpty()
                && System.currentTimeMillis() - AppStore.savedAt() < SNAP_TTL
                && AppStore.hasIcons()) {
            cur = snapshot;
            builtAt = System.currentTimeMillis();
            return snapshot;
        }
        ArrayList<App> out = load(ctx);
        if (!out.isEmpty()) {
            cur = out;
            builtAt = System.currentTimeMillis();
            AppListPerm.persist(ctx);
            AppStore.save(ctx, out);
        } else if (snapshot != null && !snapshot.isEmpty()) {
            return snapshot;
        }
        return out;
    }

    public static void invalidate() {
        cur = null;
        builtAt = 0;
    }

    private static ArrayList<App> load(Context ctx) {
        ArrayList<App> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> ais;
        try {
            ais = pm.getInstalledApplications(0);
        } catch (Throwable t) {
            return out;
        }
        StorageStatsManager ssm = null;
        try {
            ssm = (StorageStatsManager) ctx.getSystemService(Context.STORAGE_STATS_SERVICE);
        } catch (Throwable ignored) {
        }
        String me = ctx.getPackageName();
        for (ApplicationInfo ai : ais) {
            try {
                App a = new App();
                a.pkg = ai.packageName;
                CharSequence lb = pm.getApplicationLabel(ai);
                a.label = lb == null ? a.pkg : lb.toString();
                a.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (a.pkg.equals(me)) a.label = a.label + "（本应用）";
                try {
                    a.icon = pm.getApplicationIcon(ai);
                } catch (Throwable ignored) {
                }

                if (ssm != null) {
                    try {
                        StorageStats st = ssm.queryStatsForPackage(
                                StorageManager.UUID_DEFAULT, a.pkg, Process.myUserHandle());
                        a.code = st.getAppBytes();
                        a.data = st.getDataBytes();
                        a.cache = st.getCacheBytes();
                        a.size = a.code + a.data + a.cache;
                        a.exact = true;
                    } catch (Throwable ignored) {
                    }
                }
                if (a.size < 0) {
                    // 退化估算：外部数据目录 + 可见的 APK 体积
                    long ext = Fs.dirSize(new File(Fs.ROOT, "Android/data/" + a.pkg));
                    long apk = 0;
                    try {
                        File f = new File(ai.sourceDir);
                        if (f.canRead()) apk = f.length();
                    } catch (Throwable ignored) {
                    }
                    a.data = ext;
                    a.code = apk;
                    a.size = ext + apk;
                }
                out.add(a);
            } catch (Throwable ignored) {
            }
        }
        Collections.sort(out, (x, y) -> Long.compare(y.size, x.size));
        return out;
    }
}
