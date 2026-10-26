package com.watchsuite.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Scroll;
import com.watchsuite.Ui;
import com.watchsuite.core.Fs;
import com.watchsuite.core.Index;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/** 空间清理：分类扫描垃圾文件，勾选后一键清理（OPPO 风格）。 */
public class CleanScreen extends Screen {

    private static class Cat {
        String name;
        String desc;
        String glyph;
        int color;
        long size;
        int count;
        boolean checked = true;
        boolean info;          // 仅展示，不可清理
        ArrayList<File> files = new ArrayList<>();

        Cat(String n, String d, String g, int c) {
            name = n;
            desc = d;
            glyph = g;
            color = c;
        }
    }

    private static final float L = 46f, R = 420f, TOP = 244f, BOT = 424f;
    private static final float ROW_H = 56f, GAP = 8f;
    /** 首尾下沉量。 */
    private static final float PAD = Ui.flowPad(ROW_H, TOP, BOT);

    private final ArrayList<Cat> cats = new ArrayList<>();
    private final Scroll sc = new Scroll(this::invalidate);
    private boolean scanning = false;
    private int confirmMode = 0; // 0 无 1 确认
    private long avail = -1, totalSpace = -1;
    /** 存储分析入口（合并进本页的快捷跳转）。 */
    private final RectF btnAnalysis = new RectF(60f, 198f, 406f, 234f);

    private final RectF btnClean = new RectF(262f, 130f, 372f, 184f);

    public CleanScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        loadSpace();
        scan();
    }

    @Override
    public void onResume() {
        loadSpace();
    }

    private void loadSpace() {
        act.bg(() -> {
            long[] s = Fs.space();
            act.ui(() -> {
                totalSpace = s[0];
                avail = s[1];
                invalidate();
            });
        });
    }

    private void scan() {
        scanning = true;
        cats.clear();
        sc.reset();
        invalidate();
        Index.Snap mem = Index.get();
        if (mem != null) act.bg(() -> build(mem));
        Index.ensure(act, false, s -> act.bg(() -> build(s)));
    }

    /** 索引给出了全盘分类，这里只补两个索引没管的目录（缩略图、应用缓存）。 */
    private void build(Index.Snap s) {
        ArrayList<File> thumbs = new ArrayList<>();
        collect(new File(Fs.ROOT, "DCIM/.thumbnails"), thumbs, 0);

        ArrayList<File> cache = new ArrayList<>();
        File[] apps = new File(Fs.ROOT, "Android/data").listFiles();
        if (apps != null) {
            for (File app : apps) {
                collect(new File(app, "cache"), cache, 0);
                collect(new File(app, "code_cache"), cache, 0);
            }
        }

        ArrayList<Cat> out = new ArrayList<>();
        out.add(mk("应用缓存", "各应用临时文件", "缓", 0xFFFFC46B, cache));
        out.add(mk("缩略图", "DCIM/.thumbnails", "图", 0xFF58D6C9, thumbs));
        out.add(mk("日志文件", "存储里的 *.log", "志", 0xFF8CA0B4, s.logs));
        out.add(mk("安装包", "Download 里的 *.apk", "装", 0xFF3DDC84, s.apks));

        Cat big = mk("大文件", "超过 200MB，请自行确认", "大", 0xFFFF8FA3, s.big);
        big.info = true;
        big.checked = false;
        out.add(big);

        Cat empty = mk("空文件夹", "不占空间，可顺手清掉", "空", 0xFF66B3FF, s.emptyDirs);
        empty.checked = false;
        out.add(empty);

        // 分类也按体积从大到小，最占地方的一眼可见
        out.sort((a, b) -> Long.compare(b.size, a.size));

        act.ui(() -> {
            cats.clear();
            cats.addAll(out);
            scanning = false;
            sc.max = Math.max(0, cats.size() * (ROW_H + GAP) + 2 * PAD - (BOT - TOP));
            sc.clamp();
            invalidate();
        });
    }

    private static Cat mk(String name, String desc, String glyph, int color, ArrayList<File> files) {
        Cat ct = new Cat(name, desc, glyph, color);
        ct.files.addAll(files);
        ct.count = ct.files.size();
        for (File f : ct.files) ct.size += f.length();
        return ct;
    }

    private static void collect(File dir, ArrayList<File> out, int depth) {
        if (depth > 8) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, out, depth + 1);
            else out.add(k);
        }
    }

    private long checkedSize() {
        long s = 0;
        for (Cat ct : cats) if (ct.checked && !ct.info) s += ct.size;
        return s;
    }

    private int checkedCount() {
        int n = 0;
        for (Cat ct : cats) if (ct.checked && !ct.info) n += ct.count;
        return n;
    }

    @Override
    public void draw(Canvas c) {
        header(c, "清理分析", scanning ? "扫描中…"
                : (avail >= 0 ? "剩 " + Fs.human(avail) : null));

        // 汇总卡片
        Ui.round(c, 60f, 124f, 406f, 190f, 22f, Ui.SURFACE);
        Ui.textL(c, "可清理", 86f, 154f, 15f, Ui.DIM);
        Ui.textLBold(c, scanning ? "…" : Fs.human(checkedSize()), 86f, 182f, 26f, Ui.TEXT);
        if (!scanning) {
            Ui.round(c, btnClean, 27f, Ui.ACCENT);
            Ui.textC(c, "清理", btnClean.centerX(), btnClean.centerY() + 8f, 20f, Ui.ON_ACCENT);
        }

        // 存储分析入口：全盘占比 + 跳转
        Ui.round(c, btnAnalysis, 16f, Ui.SURFACE);
        float usedRatio = totalSpace > 0 ? (float) Math.min(1.0,
                (double) (totalSpace - avail) / totalSpace) : 0f;
        Ui.textL(c, "存储分析", 82f, btnAnalysis.centerY() + 6f, 16f, Ui.TEXT);
        String rightTxt = totalSpace > 0
                ? String.format(Locale.US, "全盘已用 %d%% · 剩 %s  ›",
                Math.round(usedRatio * 100), Fs.human(avail))
                : "统计中 ›";
        Ui.textR(c, rightTxt, 388f, btnAnalysis.centerY() + 6f, 13f,
                totalSpace > 0 ? Ui.tension(usedRatio) : Ui.FAINT);

        c.save();
        c.clipRect(0, TOP, W, BOT);
        c.translate(0, -sc.off);
        for (int i = 0; i < cats.size(); i++) {
            Cat ct = cats.get(i);
            float y = TOP + PAD + i * (ROW_H + GAP);
            if (y + ROW_H < TOP + sc.off || y > BOT + sc.off) continue;
            RectF r = Ui.flow(L, y, R, y + ROW_H, TOP + sc.off, BOT + sc.off);
            Ui.round(c, r, 16f, Ui.SURFACE);
            float cy = r.centerY();
            // 勾选圈
            if (ct.info || !ct.checked) {
                Ui.circleStroke(c, r.left + 18f, cy, 10f, 2.2f, Ui.FAINT);
            } else {
                Ui.circle(c, r.left + 18f, cy, 10f, Ui.ACCENT);
                Ui.textC(c, "✓", r.left + 18f, cy + 4.5f, 13f, Ui.ON_ACCENT);
            }
            // 彩色分类图标
            RectF g = new RectF(r.left + 36f, cy - 15f, r.left + 64f, cy + 15f);
            Ui.round(c, g, 10f, Ui.alpha(ct.color, 0x30));
            Ui.textC(c, ct.glyph, g.centerX(), g.centerY() + 5.5f, 15f, ct.color);

            float tx = g.right + 10f;
            Ui.textLBold(c, ct.name, tx, y + 24f, 16f, ct.info ? Ui.DIM : Ui.TEXT);
            Ui.textL(c, Ui.ellipsize(ct.desc, 12f, (r.right - tx) * 0.55f), tx, y + 41f, 12f, Ui.FAINT);
            String right = ct.count == 0 ? "—" : ct.count + " 项 · " + Fs.human(ct.size);
            Ui.textR(c, right, r.right - 12f, cy + 5f, 13f, ct.info ? Ui.FAINT : Ui.DIM);
        }
        c.restore();

        if (scanning) Ui.textC(c, "正在扫描存储…", 233f, 300f, 18f, Ui.FAINT);

        if (confirmMode == 1) {
            Ui.fill(c, new RectF(0, 0, W, H), 0xB0000000);
            Ui.round(c, 62f, 180f, 404f, 320f, 22f, Ui.SURFACE2);
            Ui.textC(c, "确认清理所选内容？", 233f, 226f, 20f, Ui.TEXT);
            Ui.textC(c, "将删除 " + Fs.human(checkedSize()), 233f, 254f, 15f, Ui.WARN);
            Ui.round(c, 92f, 280f, 228f, 324f, 22f, Ui.DANGER);
            Ui.textC(c, "清理", 160f, 309f, 19f, 0xFFFFFFFF);
            Ui.round(c, 238f, 280f, 374f, 324f, 22f, Ui.SURFACE);
            Ui.textC(c, "取消", 306f, 309f, 19f, Ui.TEXT);
        }
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (confirmMode != 0) return true;
            sc.begin(y);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (confirmMode != 0) return false;
            float before = sc.off;
            sc.move(y);
            if (sc.off != before) invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (confirmMode == 1) {
                if (x >= 92f && x <= 228f && y >= 280f && y <= 324f) {
                    confirmMode = 0;
                    doClean();
                } else {
                    confirmMode = 0;
                }
                invalidate();
                return true;
            }
            sc.end();
            if (sc.dragDist() > 12f) return true;
            if (hitBack(x, y)) {
                act.pop();
                return true;
            }
            if (btnAnalysis.contains(x, y)) {
                act.push(new StorageScreen(act));
                return true;
            }
            if (btnClean.contains(x, y) && !scanning && checkedCount() > 0) {
                confirmMode = 1;
                invalidate();
                return true;
            }
            if (y >= TOP && y <= BOT) {
                int idx = (int) ((y + sc.off - TOP - PAD) / (ROW_H + GAP));
                if (idx >= 0 && idx < cats.size()) {
                    Cat ct = cats.get(idx);
                    if (!ct.info) {
                        ct.checked = !ct.checked;
                        invalidate();
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void doClean() {
        ArrayList<File> doomed = new ArrayList<>();
        for (Cat ct : cats) {
            if (ct.checked && !ct.info) doomed.addAll(ct.files);
        }
        if (doomed.isEmpty()) return;
        final long before = checkedSize();
        act.toast("清理中…");
        act.bg(() -> {
            int ok = 0;
            for (File f : doomed) {
                if (Fs.delete(f)) ok++;
            }
            // 删完立刻同步重建索引：这样重扫时拿到的是清理后的真实数据，
            // 各页面（分析/视频/相册/清理）不用退出应用就能看到新数字
            Index.rebuild(act);
            final int n = ok;
            act.ui(() -> {
                act.toast("已清理 " + n + " 项 · " + Fs.human(before));
                loadSpace();
                scan();
            });
        });
    }

    /** 旋钮滚动列表。 */
    @Override
    public boolean rotary(float delta) {
        sc.scrollBy(delta * 96f);
        invalidate();
        return true;
    }
    public boolean back() {
        if (confirmMode != 0) {
            confirmMode = 0;
            invalidate();
            return true;
        }
        return false;
    }
}
