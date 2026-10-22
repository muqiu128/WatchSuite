package com.watchsuite.ui;

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
import java.util.ArrayList;

/** 文件管理：目录浏览 + 打开 / 安装 / 分享 / 详情 / 删除。 */
public class FilesScreen extends Screen {

    private static final float L = 38f, R = 428f, TOP = 120f, BOT = 424f;
    private static final float ROW_H = 72f, GAP = 8f;
    /** 首尾下沉量。 */
    private static final float PAD = Ui.flowPad(ROW_H, TOP, BOT);

    private final ArrayList<File> items = new ArrayList<>();
    private final ArrayList<File> hist = new ArrayList<>();
    /** 目录的体积（文件夹按递归体积算），排序与显示共用。 */
    private final java.util.HashMap<String, Long> sizes = new java.util.HashMap<>();
    private final Scroll sc = new Scroll(this::invalidate);
    private boolean loading = true;
    private String errMsg;

    private File dir;
    private int sheetIdx = -1;      // 操作面板针对的条目
    private int mode = 0;           // 0 列表 1 操作面板 2 详情 3 确认删除

    private static final String[] SHEET = {"打开", "详情", "分享", "删除"};

    public FilesScreen(MainActivity a, File start) {
        super(a);
        dir = start;
        hist.add(dir);
        load();
    }

    private void load() {
        loading = true;
        errMsg = null;
        items.clear();
        sizes.clear();
        sc.reset();
        invalidate();
        final File d = dir;
        act.bg(() -> {
            final String[] err = new String[1];
            final ArrayList<File> out = new ArrayList<>();
            final java.util.HashMap<String, Long> sz = new java.util.HashMap<>();
            // 第一阶段：只列目录（快），先把列表画出来，杜绝黑屏读取中
            try {
                for (File f : Fs.list(d, false)) out.add(f);
                for (File f : out) {
                    if (!f.isDirectory()) sz.put(f.getAbsolutePath(), f.length());
                }
            } catch (Throwable t) {
                err[0] = String.valueOf(t);
            }
            out.sort((a, b) -> {
                boolean da = a.isDirectory(), db = b.isDirectory();
                if (da != db) return da ? -1 : 1;   // 阶段一：文件夹在前
                return a.getName().compareToIgnoreCase(b.getName());
            });
            act.ui(() -> {
                if (!d.equals(dir)) return;
                items.addAll(out);
                sizes.putAll(sz);
                loading = false;
                errMsg = err[0];
                resort();
                invalidate();
            });
            if (err[0] != null) return;
            // 第二阶段：文件夹递归体积后台补，边算边刷新
            int done = 0;
            for (File f : out) {
                if (!f.isDirectory() || !d.equals(dir)) continue;
                final File ff = f;
                final long v;
                try {
                    v = Fs.dirSize(ff);
                } catch (Throwable ignored) {
                    continue;
                }
                if (++done % 3 == 0 || done == 1) {
                    act.ui(() -> {
                        if (d.equals(dir)) {
                            sizes.put(ff.getAbsolutePath(), v);
                            resort();
                            invalidate();
                        }
                    });
                } else {
                    if (d.equals(dir)) sizes.put(ff.getAbsolutePath(), v);
                }
            }
            final int n = done;
            act.ui(() -> {
                if (d.equals(dir)) {
                    resort();
                    invalidate();
                }
            });
        });
    }

    /** 按体积大→小重排（体积未知的排后面），文件夹与文件混合比较。 */
    private void resort() {
        java.util.Collections.sort(items, (a, b) -> Long.compare(sizeOf(b), sizeOf(a)));
        sc.max = Math.max(0, items.size() * (ROW_H + GAP) + 2 * PAD - (BOT - TOP));
        sc.clamp();
    }

    private static long sizeOf(java.util.HashMap<String, Long> m, File f) {
        Long v = m.get(f.getAbsolutePath());
        return v == null ? 0L : v;
    }

    private long sizeOf(File f) {
        return sizeOf(sizes, f);
    }

    private void go(File d) {
        dir = d;
        hist.add(d);
        load();
    }

    private boolean goUp() {
        // 已在存储根目录：交给页面栈弹出，不能越界到 /storage/emulated
        if (Fs.ROOT.equals(dir.getAbsolutePath())) return false;
        File p = dir.getParentFile();
        if (p == null || !dir.getAbsolutePath().startsWith(Fs.ROOT)) return false;
        dir = p;
        hist.add(dir);
        load();
        return true;
    }

    @Override
    public void draw(Canvas c) {
        String title = Fs.ROOT.equals(dir.getAbsolutePath()) ? "内部存储" : dir.getName();
        header(c, title, items.size() + " 项");

        c.save();
        c.clipRect(0, TOP, W, BOT);
        c.translate(0, -sc.off);
        for (int i = 0; i < items.size(); i++) {
            File f = items.get(i);
            float y = TOP + PAD + i * (ROW_H + GAP);
            if (y + ROW_H < TOP + sc.off || y > BOT + sc.off) continue;
            RectF r = Ui.flow(L, y, R, y + ROW_H, TOP + sc.off, BOT + sc.off);
            Ui.round(c, r, 20f, Ui.SURFACE);
            float cy = r.centerY();
            RectF g = new RectF(r.left + 12f, cy - 20f, r.left + 50f, cy + 20f);
            int col = f.isDirectory() ? 0xFFFFC46B : Fs.catColor(Fs.cat(f.getName()));
            Ui.glyph(c, g, Fs.glyph(f.getName(), f.isDirectory()), col);
            String name = Ui.ellipsize(f.getName(), 18f, r.right - g.right - 76f);
            Ui.textLBold(c, name, g.right + 12f, cy - 2f, 18f, Ui.TEXT);
            String sub = f.isDirectory()
                    ? (sizes.containsKey(f.getAbsolutePath())
                            ? (sizes.containsKey(f.getAbsolutePath())
                            ? "文件夹 · " + Fs.human(sizeOf(f)) : "文件夹 · 计算中…") : "文件夹 · 计算中…")
                    : Fs.human(f.length()) + " · " + Fs.date(f.lastModified()).substring(5, 10);
            Ui.textL(c, sub, g.right + 12f, cy + 19f, 13f, Ui.FAINT);
            // 右侧 ⋯
            Ui.circle(c, r.right - 26f, cy, 14f, Ui.SURFACE2);
            Ui.textC(c, "⋯", r.right - 26f, cy + 6f, 18f, Ui.DIM);
        }
        c.restore();

        if (loading) Ui.textC(c, "读取中…", 233f, 280f, 20f, Ui.FAINT);
        if (!loading && items.isEmpty()) {
            if (errMsg != null) {
                Ui.textC(c, "读取失败", 233f, 250f, 20f, Ui.DANGER);
                Ui.textC(c, Ui.ellipsize(errMsg, 13f, 360f), 233f, 280f, 13f, Ui.WARN);
                boolean mgr = android.os.Environment.isExternalStorageManager();
                Ui.textC(c, "全盘权限: " + (mgr ? "已授予" : "未授予"),
                        233f, 306f, 14f, Ui.DIM);
            } else {
                Ui.textC(c, "空文件夹", 233f, 280f, 20f, Ui.FAINT);
            }
        }

        // 顶部/底部滚动提示渐隐
        drawOverlays(c);
    }

    private void drawOverlays(Canvas c) {
        if (mode == 1 && sheetIdx >= 0 && sheetIdx < items.size()) {
            File f = items.get(sheetIdx);
            Ui.fill(c, new RectF(0, 0, W, H), 0xB0000000);
            float top = 190f, h = 60f;
            Ui.round(c, 52f, top, 414f, top + 20f + SHEET.length * h, 22f, Ui.SURFACE2);
            for (int i = 0; i < SHEET.length; i++) {
                float y = top + 10f + i * h;
                boolean danger = SHEET[i].equals("删除");
                Ui.textC(c, SHEET[i], 233f, y + 38f, 20f, danger ? Ui.DANGER : Ui.TEXT);
            }
            Ui.textC(c, Ui.ellipsize(f.getName(), 15f, 320f), 233f, top - 12f, 15f, Ui.DIM);
        } else if (mode == 2) {
            drawDetail(c);
        } else if (mode == 3) {
            File f = items.get(sheetIdx);
            Ui.fill(c, new RectF(0, 0, W, H), 0xB0000000);
            Ui.round(c, 62f, 170f, 404f, 330f, 22f, Ui.SURFACE2);
            Ui.textC(c, "删除后不可恢复", 233f, 216f, 20f, Ui.TEXT);
            Ui.textC(c, Ui.ellipsize(f.getName(), 15f, 300f), 233f, 244f, 15f, Ui.FAINT);
            Ui.round(c, 92f, 268f, 228f, 312f, 22f, Ui.DANGER);
            Ui.textC(c, "删除", 160f, 297f, 19f, 0xFFFFFFFF);
            Ui.round(c, 238f, 268f, 374f, 312f, 22f, Ui.SURFACE);
            Ui.textC(c, "取消", 306f, 297f, 19f, Ui.TEXT);
        }
    }

    private void drawDetail(Canvas c) {
        File f = items.get(sheetIdx);
        Ui.fill(c, new RectF(0, 0, W, H), 0xB0000000);
        Ui.round(c, 62f, 150f, 404f, 352f, 22f, Ui.SURFACE2);
        Ui.textC(c, Ui.ellipsize(f.getName(), 18f, 300f), 233f, 190f, 18f, Ui.TEXT);
        Ui.textC(c, f.isDirectory() ? "文件夹 · " + Fs.human(sizeOf(f)) : Fs.human(f.length()),
                233f, 224f, 16f, Ui.DIM);
        String path = f.getAbsolutePath();
        // 简单换行路径
        int cut = path.length() / 2;
        Ui.textC(c, path.substring(0, cut), 233f, 258f, 12f, Ui.FAINT);
        Ui.textC(c, path.substring(cut), 233f, 276f, 12f, Ui.FAINT);
        Ui.textC(c, "修改 " + Fs.date(f.lastModified()), 233f, 306f, 13f, Ui.FAINT);
        Ui.round(c, 152f, 316f, 314f, 348f, 16f, Ui.SURFACE);
        Ui.textC(c, "关闭", 233f, 339f, 16f, Ui.TEXT);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (mode != 0) return overlayTouch(action, x, y);
            sc.begin(y);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (mode != 0) return false;
            float before = sc.off;
            sc.move(y);
            if (sc.off != before) invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (mode != 0) return overlayTouch(action, x, y);
            sc.end();
            if (Math.abs(sc.dragDist()) > 12f) return true; // 拖动不算点击
            if (hitBack(x, y)) {
                if (!goUp()) act.pop();
                return true;
            }
            if (y < TOP || y > BOT) return false;
            int idx = (int) ((y + sc.off - TOP - PAD) / (ROW_H + GAP));
            if (idx < 0 || idx >= items.size()) return false;
            File f = items.get(idx);
            if (x > R - 52f) {
                sheetIdx = idx;
                mode = 1;
                invalidate();
                return true;
            }
            open(f);
            return true;
        }
        return false;
    }

    private boolean overlayTouch(int action, float x, float y) {
        if (action != MotionEvent.ACTION_UP) return true;
        if (mode == 1) {
            float top = 200f, h = 60f;
            int i = (int) ((y - top) / h);
            File f = items.get(sheetIdx);
            if (x < 52f || x > 414f || i < 0 || i >= SHEET.length) {
                mode = 0;
                invalidate();
                return true;
            }
            mode = 0;
            switch (SHEET[i]) {
                case "打开": open(f); break;
                case "详情": mode = 2; break;
                case "分享":
                    act.toast(Openers.share(act, f) ? "已唤起分享" : "无法分享");
                    break;
                case "删除": mode = 3; break;
            }
            invalidate();
            return true;
        }
        if (mode == 2) {
            mode = 0;
            invalidate();
            return true;
        }
        if (mode == 3) {
            File f = items.get(sheetIdx);
            if (x >= 92f && x <= 228f && y >= 268f && y <= 312f) {
                final File target = f;
                final int idx = sheetIdx;
                mode = 0;
                act.toast("删除中…");
                act.bg(() -> {
                    Fs.delete(target);
                    act.ui(() -> {
                        act.toast("已删除");
                        load();
                    });
                });
            } else {
                mode = 0;
            }
            invalidate();
            return true;
        }
        return true;
    }

    private void open(File f) {
        if (f.isDirectory()) {
            go(f);
            return;
        }
        String name = f.getName().toLowerCase();
        if (name.endsWith(".apk")) {
            Openers.install(act, f);
            return;
        }
        String cat = Fs.cat(name);
        switch (cat) {
            case "视频":
                com.watchsuite.VideoPlayerActivity.play(act, f);
                break;
            case "音频":
                com.watchsuite.MusicService.playFile(act, f);
                act.push(new MusicScreen(act));
                break;
            case "图片":
                ArrayList<File> one = new ArrayList<>();
                one.add(f);
                act.push(new ViewerScreen(act, one, 0));
                break;
            default:
                if (!Openers.view(act, f)) act.toast("没有能打开此文件的应用");
                break;
        }
    }

    @Override
    public boolean back() {
        if (mode != 0) {
            mode = 0;
            invalidate();
            return true;
        }
        if (hist.size() > 1) {
            hist.remove(hist.size() - 1);
            dir = hist.get(hist.size() - 1);
            load();
            return true;
        }
        return false;
    }
}
