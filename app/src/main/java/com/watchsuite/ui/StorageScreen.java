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

/** 存储分析：总览环形图 + 分类占比 + 大文件排行。 */
public class StorageScreen extends Screen {

    private static final float LIST_TOP = 258f, LIST_BOT = 422f;
    private static final float CAT_H = 44f, TOP_H = 48f, GAP = 6f;
    private static final float LABEL_H = 26f;
    /** 首尾下沉量。 */
    private static final float PAD = Ui.flowPad(CAT_H, LIST_TOP, LIST_BOT);

    private final Scroll sc = new Scroll(this::invalidate);
    private Fs.Stats stats;
    private ArrayList<File> tops = new ArrayList<>();
    private long total = 0, avail = 0;
    private boolean scanning = true;

    public StorageScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        scanning = true;
        loadSpace();
        Index.Snap s0 = Index.get();
        if (s0 != null) apply(s0);
        Index.ensure(act, false, this::apply);
    }

    @Override
    public void onResume() {
        // 清理完返回时：空间与分类数字都要立刻更新
        loadSpace();
        Index.Snap s = Index.get();
        if (s != null) apply(s);
    }

    private void loadSpace() {
        act.bg(() -> {
            long[] s = Fs.space();
            act.ui(() -> {
                total = s[0];
                avail = s[1];
                invalidate();
            });
        });
    }

    private void apply(Index.Snap s) {
        stats = s.stats;
        tops = s.top;
        scanning = false;
        sc.max = Math.max(0, contentH() - (LIST_BOT - LIST_TOP));        sc.clamp();
        invalidate();
    }

    private int catCount() {
        return Fs.CATS.length;
    }

    private float contentH() {
        return 2 * PAD + catCount() * (CAT_H + GAP) + LABEL_H + 5 * (TOP_H + GAP);
    }

    /** 分类按体积从大到小重排后的下标。 */
    private int[] order() {
        int n = catCount();
        int[] ord = new int[n];
        for (int i = 0; i < n; i++) ord[i] = i;
        // 插入排序，n 很小
        for (int i = 1; i < n; i++) {
            int v = ord[i];
            long sv = stats != null ? stats.byCat[v] : 0;
            int j = i - 1;
            while (j >= 0) {
                long sj = stats != null ? stats.byCat[ord[j]] : 0;
                if (sj >= sv) break;
                ord[j + 1] = ord[j];
                j--;
            }
            ord[j + 1] = v;
        }
        return ord;
    }

    @Override
    public void draw(Canvas c) {
        // 表圈空间圆环/顶端条：显示全盘已用比例，纯装饰
        Ui.bezelRing(c, total > 0 ? (float) Math.min(1.0,
                (double) (total - avail) / total) : 0f, MainActivity.SQUARE);

        header(c, "存储分析", null);

        float ratio = total > 0 && stats != null
                ? (float) Math.min(1.0, (double) stats.total / total) : 0f;
        // 中间的环形图已去掉（表圈圆环就是空间占比），改成大字数字
        Ui.textC(c, scanning ? "…" : Math.round(ratio * 100) + "%", 233f, 186f, 44f, Ui.TEXT);
        Ui.textC(c, scanning ? "扫描中…"
                        : "已用 " + Fs.human(stats.total) + " · 剩 " + Fs.human(avail),
                233f, 226f, 14f, Ui.DIM);

        c.save();
        c.clipRect(0, LIST_TOP, W, LIST_BOT);
        c.translate(0, -sc.off);

        // 分类占比（从大到小），卡片流层叠
        long maxCat = 1;
        if (stats != null) for (long v : stats.byCat) maxCat = Math.max(maxCat, v);
        int[] ord = order();
        float y = LIST_TOP + PAD;
        for (int k = 0; k < ord.length; k++, y += CAT_H + GAP) {
            int i = ord[k];
            if (y + CAT_H < LIST_TOP + sc.off || y > LIST_BOT + sc.off) continue;
            RectF r = Ui.flow(56f, y, 410f, y + CAT_H, LIST_TOP + sc.off, LIST_BOT + sc.off);
            Ui.round(c, r, 14f, Ui.SURFACE);
            long v = stats != null ? stats.byCat[i] : 0;
            int col = Fs.catColor(Fs.CATS[i]);
            Ui.circle(c, r.left + 18f, r.centerY(), 5f, col);
            Ui.textLBold(c, Fs.CATS[i], r.left + 32f, r.centerY() + 6f, 16f, Ui.TEXT);
            Ui.textR(c, Fs.human(v), r.right - 14f, r.centerY() + 6f, 15f, Ui.DIM);
            RectF bar = new RectF(r.left + 32f, r.bottom - 8f, r.right - 14f, r.bottom - 4f);
            Ui.bar(c, bar, v / (float) maxCat, col, Ui.SURFACE2);
        }

        // 大文件排行
        y += 12f;
        Ui.textL(c, "大文件 TOP 5", 72f, y + 16f, 15f, Ui.FAINT);
        y += LABEL_H;
        if (stats != null) {
            ArrayList<File> top = tops;
            for (int i = 0; i < top.size() && i < 5; i++, y += TOP_H + GAP) {
                File f = top.get(i);
                if (y + TOP_H < LIST_TOP + sc.off || y > LIST_BOT + sc.off) continue;
                RectF r = Ui.flow(56f, y, 410f, y + TOP_H, LIST_TOP + sc.off, LIST_BOT + sc.off);
                Ui.round(c, r, 16f, Ui.SURFACE);
                Ui.textC(c, String.valueOf(i + 1), r.left + 22f, r.centerY() + 6f, 16f, Ui.ACCENT);
                Ui.textLBold(c, Ui.ellipsize(f.getName(), 15f, r.width() - 96f), r.left + 38f, r.centerY() - 1f, 15f, Ui.TEXT);
                String sub = Fs.human(f.length()) + " · "
                        + f.getParent().replace(Fs.ROOT, "");
                Ui.textL(c, Ui.ellipsize(sub, 12f, r.width() - 96f), r.left + 38f, r.centerY() + 16f, 12f, Ui.FAINT);
            }
        }
        c.restore();
    }


    /** 旋钮滚动列表。 */
    @Override
    public boolean rotary(float delta) {
        sc.scrollBy(delta * 96f);
        invalidate();
        return true;
    }
    @Override
    public boolean touch(int action, float x, float y) {
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
            return true;
        }
        return false;
    }
}
