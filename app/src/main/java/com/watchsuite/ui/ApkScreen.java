package com.watchsuite.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Scroll;
import com.watchsuite.Ui;
import com.watchsuite.core.Fs;
import com.watchsuite.core.Openers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** 安装包：扫描存储里的 APK，一眼看清是什么应用、多大、什么时候下的，点一下就装。 */
public class ApkScreen extends Screen {

    private static final float L = 44f, R = 422f, TOP = 150f, BOT = 428f;
    private static final float ROW_H = 66f, GAP = 8f;
    private static final float PAD = Ui.flowPad(ROW_H, TOP, BOT);

    /** 一条安装包记录。 */
    private static class Item {
        File file;
        String label;      // 应用名（读不出包信息时退回文件名）
        String version;    // 版本名
        long size;
        long mtime;
        boolean parsed;    // 是否是有效 APK
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final Scroll sc = new Scroll(this::invalidate);
    private boolean scanning = true;
    private int sel = -1;
    private final RectF btnInstall = new RectF(92f, 316f, 228f, 360f);
    private final RectF btnCancel = new RectF(238f, 316f, 374f, 360f);

    public ApkScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        scan();
    }

    private void scan() {
        scanning = true;
        items.clear();
        sc.reset();
        invalidate();
        act.bg(() -> {
            ArrayList<Item> out = new ArrayList<>();
            walk(Fs.root(), out, 0);
            Collections.sort(out, (a, b) -> Long.compare(b.mtime, a.mtime));
            act.ui(() -> {
                items.clear();
                items.addAll(out);
                scanning = false;
                sc.max = Math.max(0, items.size() * (ROW_H + GAP) + 2 * PAD - (BOT - TOP));
                sc.clamp();
                invalidate();
            });
        });
    }

    /** 递归找 APK：跳过 Android/data 和隐藏目录，深度限制 5 层，避免扫爆。 */
    private void walk(File dir, ArrayList<Item> out, int depth) {
        if (depth > 5 || out.size() > 120) return;
        File[] kids;
        try {
            kids = dir.listFiles();
        } catch (Throwable t) {
            return;
        }
        if (kids == null) return;
        for (File f : kids) {
            String n = f.getName();
            if (f.isDirectory()) {
                if (n.startsWith(".") || n.equals("Android") || n.equals("obb")) continue;
                walk(f, out, depth + 1);
            } else if (n.toLowerCase(Locale.US).endsWith(".apk")) {
                out.add(describe(f));
            }
        }
    }

    private Item describe(File f) {
        Item it = new Item();
        it.file = f;
        it.size = f.length();
        it.mtime = f.lastModified();
        it.label = f.getName();
        it.version = "";
        try {
            PackageManager pm = act.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
            if (pi != null && pi.applicationInfo != null) {
                pi.applicationInfo.sourceDir = f.getAbsolutePath();
                pi.applicationInfo.publicSourceDir = f.getAbsolutePath();
                CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                if (cs != null && cs.length() > 0) it.label = cs.toString();
                it.version = pi.versionName == null ? "" : pi.versionName;
                it.parsed = true;
            }
        } catch (Throwable ignored) {
        }
        return it;
    }

    private long totalSize() {
        long s = 0;
        for (Item it : items) s += it.size;
        return s;
    }

    @Override
    public void draw(Canvas c) {
        header(c, "安装包", scanning ? null : items.size() + " 个");

        if (!scanning) {
            Ui.textLBold(c, Fs.human(totalSize()), 62f, 148f, 24f, Ui.TEXT);
            Ui.textR(c, items.size() + " 个 · 点一下直接装", 404f, 146f, 13f, Ui.FAINT);
        }

        c.save();
        c.clipRect(0, TOP, W, BOT);
        c.translate(0, -sc.off);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float y = TOP + PAD + i * (ROW_H + GAP);
            if (y + ROW_H < TOP + sc.off || y > BOT + sc.off) continue;
            RectF r = Ui.flow(L, y, R, y + ROW_H, TOP + sc.off, BOT + sc.off);
            Ui.round(c, r, 18f, Ui.SURFACE);

            // 左侧图标块：有效 APK 用绿色，损坏/非标准包用灰色
            RectF g = new RectF(r.left + 10f, r.centerY() - 19f, r.left + 48f, r.centerY() + 19f);
            int col = it.parsed ? 0xFF3DDC84 : 0xFF8A8A8A;
            Ui.round(c, g, 11f, Ui.alpha(col, 0x2A));
            String glyph = it.parsed && it.label.length() > 0
                    ? it.label.substring(0, 1) : "包";
            Ui.textC(c, glyph, g.centerX(), g.centerY() + 7f, 19f, col);

            float tx = g.right + 12f;
            float maxW = r.right - tx - 76f;
            Ui.textLBold(c, Ui.ellipsize(it.label, 17f, maxW), tx, r.top + 27f, 17f, Ui.TEXT);
            String sub = (it.version.length() > 0 ? "v" + it.version + " · " : "")
                    + Fs.human(it.size) + " · " + date(it.mtime);
            Ui.textL(c, Ui.ellipsize(sub, 12f, maxW + 20f), tx, r.top + 47f, 12f, Ui.FAINT);
            Ui.textR(c, "安装", r.right - 12f, r.centerY() + 6f, 15f, Ui.ACCENT);
        }
        c.restore();

        if (scanning) Ui.textC(c, "扫描安装包…", 233f, 300f, 18f, Ui.FAINT);
        if (!scanning && items.isEmpty()) {
            Ui.textC(c, "没找到安装包", 233f, 280f, 18f, Ui.FAINT);
            Ui.textC(c, "先往手表里拷 APK，再回来看", 233f, 308f, 13f, Ui.FAINT);
        }

        if (sel >= 0 && sel < items.size()) drawConfirm(c);
    }

    private void drawConfirm(Canvas c) {
        Item it = items.get(sel);
        Ui.fill(c, new RectF(0, 0, W, H), 0xC8000000);
        Ui.round(c, 52f, 176f, 414f, 376f, 22f, Ui.SURFACE2);
        Ui.textC(c, Ui.ellipsize(it.label, 18f, 320f), 233f, 212f, 18f, Ui.TEXT);
        Ui.textC(c, (it.version.length() > 0 ? "v" + it.version + " · " : "") + Fs.human(it.size),
                233f, 238f, 14f, Ui.DIM);
        Ui.textC(c, Ui.ellipsize(it.file.getName(), 12f, 330f), 233f, 262f, 12f, Ui.FAINT);
        if (!it.parsed) {
            Ui.textC(c, "包信息读取失败，可能不是有效 APK", 233f, 288f, 12f, Ui.WARN);
        }
        Ui.round(c, btnInstall, 22f, Ui.ACCENT);
        Ui.textC(c, "安装", 160f, 344f, 19f, Ui.ON_ACCENT);
        Ui.round(c, btnCancel, 22f, Ui.SURFACE);
        Ui.textC(c, "取消", 306f, 344f, 19f, Ui.TEXT);
    }

    private static String date(long t) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(t));
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (sel >= 0) {
            if (action != MotionEvent.ACTION_UP) return true;
            if (btnInstall.contains(x, y)) {
                Item it = items.get(sel);
                sel = -1;
                invalidate();
                Openers.install(act, it.file);
                return true;
            }
            sel = -1;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            sc.begin(y);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float before = sc.off;
            sc.move(y);
            if (sc.off != before) invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            sc.end();
            if (sc.dragDist() > 12f) return true;
            if (hitBack(x, y)) {
                act.pop();
                return true;
            }
            if (y < TOP || y > BOT) return false;
            int idx = (int) ((y + sc.off - TOP - PAD) / (ROW_H + GAP));
            if (idx < 0 || idx >= items.size()) return false;
            sel = idx;
            invalidate();
            return true;
        }
        return false;
    }

    /** 旋钮滚动列表。 */
    @Override
    public boolean rotary(float delta) {
        if (sel >= 0) return true;
        sc.scrollBy(delta * 96f);
        invalidate();
        return true;
    }

    @Override
    public boolean back() {
        if (sel >= 0) {
            sel = -1;
            invalidate();
            return true;
        }
        return false;
    }
}
