package com.watchsuite.ui;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.Settings;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Scroll;
import com.watchsuite.Ui;
import com.watchsuite.core.AppStore;
import com.watchsuite.core.Apps;
import com.watchsuite.core.Fs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 应用占用：每个已安装应用占了多少空间、占全盘的百分比，从大到小。 */
public class AppsScreen extends Screen {

    private static final float L = 40f, R = 426f, TOP = 176f, BOT = 434f;
    private static final float ROW_H = 68f, GAP = 8f;
    /** 首尾下沉量：列表在顶/底时首尾卡片也能沉到接近全尺寸的位置。 */
    private static final float PAD = Ui.flowPad(ROW_H, TOP, BOT);

    private final List<Apps.App> items = new ArrayList<>();
    private final Scroll sc = new Scroll(this::invalidate);
    private boolean loading = true;

    private long totalSpace = 0, availSpace = 0, appTotal = 0;
    /** 选中的应用下标，-1 表示没有打开详情面板。 */
    private int sel = -1;

    public AppsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        ArrayList<Apps.App> c = Apps.cached();
        if (c != null && !c.isEmpty()) apply(c);
        act.bg(() -> {
            long[] sp = Fs.space();
            // 上次落盘的快照：先进来占位秒开，后台枚举完成后再替换成新数据
            ArrayList<Apps.App> snap = Apps.cached() == null ? AppStore.load(act) : null;
            if (snap != null && !snap.isEmpty()) {
                act.ui(() -> {
                    if (items.isEmpty()) apply(snap);
                });
            }
            ArrayList<Apps.App> list = Apps.list(act, snap);
            act.ui(() -> {
                totalSpace = sp[0];
                availSpace = sp[1];
                apply(list);
            });
        });
    }

    private void apply(List<Apps.App> list) {
        // 后台刷新回来空结果时保留已有内容，不要闪成空列表
        if ((list == null || list.isEmpty()) && !items.isEmpty()) return;
        ArrayList<Apps.App> sorted = new ArrayList<>(list);
        Collections.sort(sorted, (a, b) -> Long.compare(b.size, a.size));
        items.clear();
        items.addAll(sorted);
        long sum = 0;
        for (Apps.App a : items) sum += Math.max(0, a.size);
        appTotal = sum;
        loading = false;
        sc.max = Math.max(0, items.size() * (ROW_H + GAP) + 2 * PAD - (BOT - TOP));
        sc.clamp();
        invalidate();
    }

    private int pctColor() {
        return Ui.TEXT;
    }

    @Override
    public void draw(Canvas c) {
        // 表圈空间圆环：纯装饰，不参与触摸；颜色随紧张度绿→黄→红
        Ui.bezelRing(c, totalSpace > 0
                ? (float) Math.min(1.0, (double) (totalSpace - availSpace) / totalSpace) : 0f,
                MainActivity.SQUARE);

        header(c, "应用占用", items.isEmpty() ? null : items.size() + " 个");

        float ratio = totalSpace > 0 ? (float) Math.min(1.0, (double) appTotal / totalSpace) : 0f;
        Ui.textLBold(c, loading ? "…" : Fs.human(appTotal), 66f, 148f, 24f, Ui.TEXT);
        String right = totalSpace > 0
                ? String.format(Locale.US, "占存储 %d%%", Math.round(ratio * 100))
                : "统计中";
        Ui.textR(c, right, 404f, 146f, 14f, Ui.tension(ratio));

        long maxSize = 1;
        for (Apps.App a : items) maxSize = Math.max(maxSize, a.size);

        c.save();
        c.clipRect(0, TOP, W, BOT);
        c.translate(0, -sc.off);
        for (int i = 0; i < items.size(); i++) {
            Apps.App a = items.get(i);
            float y = TOP + PAD + i * (ROW_H + GAP);
            if (y + ROW_H < TOP + sc.off || y > BOT + sc.off) continue;
            // 卡片流：离屏幕中心越远越小越窄
            RectF r = Ui.flow(L, y, R, y + ROW_H, TOP + sc.off, BOT + sc.off);
            Ui.round(c, r, 20f, Ui.SURFACE);

            // 圆润的圆角方形图标
            float icS = r.height() - 16f;
            RectF ic = new RectF(r.left + 10f, r.top + 8f, r.left + 10f + icS, r.top + 8f + icS);
            int sv = Ui.roundClip(c, ic, icS * 0.32f);
            if (a.icon != null) {
                a.icon.setBounds((int) ic.left, (int) ic.top, (int) ic.right, (int) ic.bottom);
                a.icon.draw(c);
            } else {
                Ui.glyph(c, ic, first(a.label), 0xFF9B7BFF);
            }
            c.restoreToCount(sv);

            String name = Ui.ellipsize(a.label, 16f, r.right - ic.right - 84f);
            Ui.textLBold(c, name, ic.right + 12f, r.top + 28f, 16f, Ui.TEXT);
            String sub = a.size < 0
                    ? "无法读取"
                    : Fs.human(a.size) + (a.cache > 0 ? " · 缓存 " + Fs.human(a.cache) : "");
            Ui.textL(c, Ui.ellipsize(sub, 12f, r.right - ic.right - 84f), ic.right + 12f, r.top + 46f, 12f, Ui.FAINT);
            Ui.textR(c, percent(a.size), r.right - 12f, r.top + 28f, 14f, a.size < 0 ? Ui.FAINT : Ui.DIM);

            // 占比细条（相对最大的应用，方便横向比较）
            RectF bar = new RectF(ic.right + 12f, r.bottom - 10f, r.right - 74f, r.bottom - 7f);
            Ui.bar(c, bar, a.size > 0 ? (float) a.size / maxSize : 0f, Ui.alpha(pctColor(), 0xAA), Ui.SURFACE2);
        }
        c.restore();

        if (loading) Ui.textC(c, "读取应用占用…", 233f, 300f, 18f, Ui.FAINT);
        if (!loading && items.isEmpty()) {
            Ui.textC(c, "读不到应用列表", 233f, 290f, 18f, Ui.FAINT);
            Ui.textC(c, "可尝试重新授权后重进", 233f, 318f, 14f, Ui.FAINT);
        }

        if (sel >= 0 && sel < items.size()) drawDetail(c);
    }

    /** 单个应用的占用拆解：安装包 / 数据 / 缓存。 */
    private void drawDetail(Canvas c) {
        Apps.App a = items.get(sel);
        Ui.fill(c, new RectF(0, 0, W, H), 0xC8000000);
        Ui.round(c, 56f, 126f, 410f, 372f, 22f, Ui.SURFACE2);

        Ui.textC(c, Ui.ellipsize(a.label, 17f, 300f), 233f, 162f, 17f, Ui.TEXT);
        Ui.textC(c, Ui.ellipsize(a.pkg, 11f, 300f), 233f, 184f, 11f, Ui.FAINT);

        row(c, 216f, "安装包 (code)", a.code, Ui.DIM, Ui.TEXT);
        row(c, 242f, "数据 (data)", a.data, Ui.DIM, Ui.TEXT);
        row(c, 268f, "缓存 (cache)", a.cache, Ui.DIM, Ui.TEXT);
        Ui.round(c, 84f, 284f, 382f, 285.5f, 1f, Ui.LINE);
        row(c, 308f, "合计", a.size, Ui.DIM, Ui.TEXT);

        if ("com.android.packageinstaller".equals(a.pkg)) {
            Ui.textC(c, "安装器会先缓存待装 APK 的副本", 233f, 332f, 12f, Ui.WARN);
        }

        float by = detailBtnTop(a);
        Ui.round(c, 92f, by, 228f, by + 34f, 17f, Ui.SURFACE);
        Ui.textC(c, "系统详情", 160f, by + 22f, 15f, Ui.TEXT);
        Ui.round(c, 238f, by, 374f, by + 34f, 17f, Ui.ACCENT);
        Ui.textC(c, "关闭", 306f, by + 22f, 15f, Ui.ON_ACCENT);
    }

    private static float detailBtnTop(Apps.App a) {
        return "com.android.packageinstaller".equals(a.pkg) ? 344f : 326f;
    }

    private void row(Canvas c, float y, String label, long v, int lc, int vc) {
        Ui.textL(c, label, 86f, y, 14f, lc);
        Ui.textR(c, v < 0 ? "—" : Fs.human(v), 380f, y, 14f, vc);
    }

    private String percent(long size) {
        if (size < 0) return "—";
        if (totalSpace <= 0) return Fs.human(size);
        float p = size * 100f / totalSpace;
        if (p >= 10f) return String.format(Locale.US, "%.0f%%", p);
        if (p >= 0.1f) return String.format(Locale.US, "%.1f%%", p);
        return "<0.1%";
    }

    private static String first(String s) {
        if (s == null || s.isEmpty()) return "应";
        return s.substring(0, 1);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (sel >= 0) return detailTouch(action, x, y);
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

    private boolean detailTouch(int action, float x, float y) {
        if (action != MotionEvent.ACTION_UP) return true;
        Apps.App a = items.get(sel);
        float by = detailBtnTop(a);
        if (y >= by && y <= by + 34f) {
            if (x >= 92f && x <= 228f) {
                sel = -1;
                invalidate();
                openDetails(a);
                return true;
            }
            if (x >= 238f && x <= 374f) {
                sel = -1;
                invalidate();
                return true;
            }
        }
        sel = -1;
        invalidate();
        return true;
    }

    /** 旋钮滚动列表；详情面板打开时不滚。 */
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

    private void openDetails(Apps.App a) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Throwable t) {
            act.toast("打不开系统应用信息");
        }
    }
}
