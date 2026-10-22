package com.watchsuite.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.MusicService;
import com.watchsuite.Screen;
import com.watchsuite.Scroll;
import com.watchsuite.Ui;
import com.watchsuite.core.Fs;
import com.watchsuite.core.Index;
import com.watchsuite.core.MediaScan;

import java.util.ArrayList;

/** 音乐：列表 + 底部正在播放卡片，支持后台播放。 */
public class MusicScreen extends Screen {

    private static final float L = 40f, R = 426f;
    private static final float ROW_H = 62f, GAP = 8f;
    private final ArrayList<MediaScan.Item> items = new ArrayList<>();
    private final Scroll sc = new Scroll(this::invalidate);
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean loading = true;

    private final RectF bPrev = new RectF(125f, 407f, 167f, 449f);
    private final RectF bPlay = new RectF(206f, 401f, 260f, 455f);
    private final RectF bNext = new RectF(299f, 407f, 341f, 449f);
    /** 进度条（可拖动）与热区。 */
    private final RectF bar = new RectF(72f, 374f, 394f, 382f);
    private final RectF barHit = new RectF(56f, 362f, 410f, 396f);
    private boolean dragging;
    private float dragRatio;

    /** 每首歌时长缓存（Index 扫描不解析时长，这里按需补）。 */
    private static final java.util.HashMap<String, Long> DUR = new java.util.HashMap<>();
    private static final java.util.concurrent.ExecutorService DUR_POOL =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final android.graphics.Paint GLYPH = new android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG);

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            h.postDelayed(this, 500);
        }
    };

    public MusicScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        h.post(tick);
        Index.Snap s0 = Index.get();
        if (s0 != null) apply(s0.audios);
        Index.ensure(act, false, s -> apply(s.audios));
    }

    private void apply(ArrayList<MediaScan.Item> v) {
        items.clear();
        items.addAll(v);
        sc.max = Math.max(0, items.size() * (ROW_H + GAP) + 2 * pad() - (listBot() - 124f));
        sc.clamp();
        loading = false;
        fetchDurs();
        invalidate();
    }

    /** 后台补齐每首歌的时长（MediaMetadataRetriever，逐条缓存）。 */
    private void fetchDurs() {
        final ArrayList<MediaScan.Item> snapshot = new ArrayList<>(items);
        DUR_POOL.execute(() -> {
            android.media.MediaMetadataRetriever mm = new android.media.MediaMetadataRetriever();
            int n = 0;
            for (MediaScan.Item it : snapshot) {
                if (DUR.containsKey(it.path)) continue;
                long d = 0;
                try {
                    mm.setDataSource(it.path);
                    d = Long.parseLong(mm.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
                } catch (Throwable ignored) {
                }
                DUR.put(it.path, d);
                if (++n % 6 == 0) act.ui(this::invalidate);
            }
            try {
                mm.release();
            } catch (Throwable ignored) {
            }
            act.ui(this::invalidate);
        });
    }

    private long durOf(MediaScan.Item it) {
        if (it.duration > 0) return it.duration;
        Long d = DUR.get(it.path);
        return d == null ? 0 : d;
    }

    @Override
    public void onLeave() {
        h.removeCallbacks(tick);
        MusicService.stop(act);   // 关掉音乐页就停止播放
    }

    private float listBot() {
        return hasNow() ? 330f : 420f;
    }

    /** 首尾下沉量（随播放条出现与否变化，取小的那个，避免内容跳动）。 */
    private float pad() {
        return Ui.flowPad(ROW_H, 124f, 420f);
    }

    private boolean hasNow() {
        return MusicService.index >= 0;
    }

    @Override
    public void draw(Canvas c) {
        header(c, "音乐", items.size() + " 首");

        c.save();
        c.clipRect(0, 124f, W, listBot());
        c.translate(0, -sc.off);
        float top = 124f;
        for (int i = 0; i < items.size(); i++) {
            MediaScan.Item it = items.get(i);
            float y = top + pad() + i * (ROW_H + GAP);
            if (y + ROW_H < top + sc.off || y > listBot() + sc.off) continue;
            boolean cur = MusicService.index == i;
            // 卡片流：离屏幕中心越远越小越窄
            RectF r = Ui.flow(L, y, R, y + ROW_H, 124f + sc.off, listBot() + sc.off);
            Ui.round(c, r, 18f, cur ? Ui.alpha(Ui.ACCENT, 0x30) : Ui.SURFACE);
            RectF g = new RectF(r.left + 12f, r.centerY() - 17f, r.left + 46f, r.centerY() + 17f);
            Ui.glyph(c, g, "♪", cur ? Ui.ACCENT : 0xFF9B7BFF);
            String name = Ui.ellipsize(it.name, 16f, r.right - g.right - 20f);
            Ui.textLBold(c, name, g.right + 12f, r.top + 27f, 16f,
                    cur ? Ui.ACCENT : Ui.TEXT);
            String sub;
            long d = durOf(it);
            if (d > 0) sub = MediaScan.dur(d) + " · " + Fs.human(it.size);
            else if (DUR.containsKey(it.path)) sub = "时长未知 · " + Fs.human(it.size);
            else sub = Fs.human(it.size);
            Ui.textL(c, sub, g.right + 12f, r.top + 47f, 13f, Ui.FAINT);
        }
        c.restore();

        if (loading) Ui.textC(c, "扫描音乐中…", 233f, 260f, 20f, Ui.FAINT);
        if (!loading && items.isEmpty()) Ui.textC(c, "没有找到音乐", 233f, 260f, 20f, Ui.FAINT);

        if (hasNow()) drawNow(c);
    }

    private void drawNow(Canvas c) {
        Ui.round(c, 44f, 338f, 422f, 452f, 24f, Ui.SURFACE);
        Ui.textC(c, Ui.ellipsize(MusicService.curTitle(), 16f, 320f), 233f, 362f, 16f, Ui.TEXT);
        if (MusicService.err != null) {
            Ui.textC(c, MusicService.err, 233f, 362f, 16f, Ui.DANGER);
        }
        long dur = MusicService.dur(), pos = MusicService.pos();
        float ratio = dragging ? dragRatio : (dur > 0 ? (float) pos / dur : 0f);
        if (ratio > 1f) ratio = 1f;

        // 进度条（可拖动）
        Ui.bar(c, bar, ratio, Ui.ACCENT, Ui.SURFACE2);
        float hx = bar.left + ratio * bar.width();
        Ui.circle(c, hx, bar.centerY(), 9f, Ui.ACCENT);

        // 已播 / 总时长
        Ui.textL(c, MediaScan.dur(dragging ? (long) (dragRatio * dur) : pos), 72f, 402f, 12f, Ui.DIM);
        Ui.textR(c, MediaScan.dur(dur), 394f, 402f, 12f, Ui.DIM);

        // 上一首 / 下一首：自绘三角+竖条
        drawPrev(c, bPrev.centerX(), bPrev.centerY());
        drawNext(c, bNext.centerX(), bNext.centerY());

        // 播放/暂停：进度环 + 自绘播放三角 / 暂停双条
        android.graphics.RectF ring = new android.graphics.RectF(
                bPlay.centerX() - 33f, bPlay.centerY() - 33f,
                bPlay.centerX() + 33f, bPlay.centerY() + 33f);
        GLYPH.setStyle(android.graphics.Paint.Style.STROKE);
        GLYPH.setStrokeWidth(3.5f);
        GLYPH.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        GLYPH.setColor(Ui.SURFACE2);
        c.drawCircle(bPlay.centerX(), bPlay.centerY(), 33f, GLYPH);
        GLYPH.setColor(Ui.ACCENT);
        c.drawArc(ring, -90f, 360f * ratio, false, GLYPH);
        Ui.circle(c, bPlay.centerX(), bPlay.centerY(), 26f, Ui.ACCENT);
        if (MusicService.playing()) drawPause(c, bPlay.centerX(), bPlay.centerY());
        else drawPlay(c, bPlay.centerX(), bPlay.centerY());
    }

    private void drawPlay(Canvas c, float cx, float cy) {
        GLYPH.setStyle(android.graphics.Paint.Style.FILL);
        GLYPH.setColor(Ui.ON_ACCENT);
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(cx - 8f, cy - 13f);
        p.lineTo(cx + 14f, cy);
        p.lineTo(cx - 8f, cy + 13f);
        p.close();
        c.drawPath(p, GLYPH);
    }

    private void drawPause(Canvas c, float cx, float cy) {
        GLYPH.setStyle(android.graphics.Paint.Style.FILL);
        GLYPH.setColor(Ui.ON_ACCENT);
        c.drawRoundRect(cx - 12f, cy - 12f, cx - 4f, cy + 12f, 3f, 3f, GLYPH);
        c.drawRoundRect(cx + 4f, cy - 12f, cx + 12f, cy + 12f, 3f, 3f, GLYPH);
    }

    private void drawPrev(Canvas c, float cx, float cy) {
        GLYPH.setStyle(android.graphics.Paint.Style.FILL);
        GLYPH.setColor(Ui.TEXT);
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(cx + 10f, cy - 10f);
        p.lineTo(cx - 8f, cy);
        p.lineTo(cx + 10f, cy + 10f);
        p.close();
        c.drawPath(p, GLYPH);
        c.drawRoundRect(cx - 12f, cy - 10f, cx - 7f, cy + 10f, 2f, 2f, GLYPH);
    }

    private void drawNext(Canvas c, float cx, float cy) {
        GLYPH.setStyle(android.graphics.Paint.Style.FILL);
        GLYPH.setColor(Ui.TEXT);
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(cx - 10f, cy - 10f);
        p.lineTo(cx + 8f, cy);
        p.lineTo(cx - 10f, cy + 10f);
        p.close();
        c.drawPath(p, GLYPH);
        c.drawRoundRect(cx + 7f, cy - 10f, cx + 12f, cy + 10f, 2f, 2f, GLYPH);
    }

    private void updateDrag(float x) {
        dragRatio = (x - bar.left) / bar.width();
        if (dragRatio < 0f) dragRatio = 0f;
        if (dragRatio > 1f) dragRatio = 1f;
        invalidate();
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (hasNow() && barHit.contains(x, y)) {
                dragging = true;
                updateDrag(x);
                return true;
            }
            sc.begin(y);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (dragging) {
                updateDrag(x);
                return true;
            }
            float before = sc.off;
            sc.move(y);
            if (sc.off != before) invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (dragging) {
                dragging = false;
                long d = MusicService.dur();
                if (d > 0) MusicService.seekTo(act, (int) (dragRatio * d));
                invalidate();
                return true;
            }
            sc.end();
            if (sc.dragDist() > 12f) return true;
            if (hitBack(x, y)) {
                act.pop();
                return true;
            }
            if (hasNow()) {
                if (bPrev.contains(x, y)) {
                    MusicService.prev(act);
                    invalidate();
                    return true;
                }
                if (bPlay.contains(x, y)) {
                    MusicService.toggle(act);
                    invalidate();
                    return true;
                }
                if (bNext.contains(x, y)) {
                    MusicService.next(act);
                    invalidate();
                    return true;
                }
            }
            if (y >= 124f && y <= listBot()) {
                int idx = (int) ((y + sc.off - 124f - pad()) / (ROW_H + GAP));
                if (idx >= 0 && idx < items.size()) {
                    MusicService.paths = new ArrayList<>();
                    MusicService.titles = new ArrayList<>();
                    for (MediaScan.Item it : items) {
                        MusicService.paths.add(it.path);
                        MusicService.titles.add(it.name);
                    }
                    MusicService.start(act, idx);
                    invalidate();
                }
            }
            return true;
        }
        return false;
    }
}
