package com.watchsuite.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Scroll;
import com.watchsuite.Ui;
import com.watchsuite.core.Fs;
import com.watchsuite.core.Index;
import com.watchsuite.core.MediaScan;
import com.watchsuite.core.ThumbCache;

import java.io.File;
import java.util.ArrayList;

/** 视频列表（参考腕凉播放器的全宽条目风格）。 */
public class VideosScreen extends Screen {

    private static final float L = 34f, R = 432f, TOP = 112f, BOT = 430f;
    private static final float ROW_H = 96f, GAP = 10f;
    /** 首尾下沉量。 */
    private static final float PAD = Ui.flowPad(ROW_H, TOP, BOT);

    private final ArrayList<MediaScan.Item> items = new ArrayList<>();
    private final Scroll sc = new Scroll(this::invalidate);
    private boolean loading = true;

    public VideosScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        Index.Snap s0 = Index.get();
        if (s0 != null) apply(s0.videos);
        Index.ensure(act, false, s -> apply(s.videos));
    }

    private void apply(ArrayList<MediaScan.Item> v) {
        items.clear();
        items.addAll(v);
        sc.max = Math.max(0, items.size() * (ROW_H + GAP) + 2 * PAD - (BOT - TOP));
        sc.clamp();
        loading = false;
        invalidate();
    }

    @Override
    public void draw(Canvas c) {
        header(c, "视频", items.size() + " 个");

        c.save();
        c.clipRect(0, TOP, W, BOT);
        c.translate(0, -sc.off);
        for (int i = 0; i < items.size(); i++) {
            MediaScan.Item it = items.get(i);
            float y = TOP + PAD + i * (ROW_H + GAP);
            if (y + ROW_H < TOP + sc.off || y > BOT + sc.off) continue;
            // 卡片流：离屏幕中心越远越小、越窄，像通知层叠
            RectF r = Ui.flow(L, y, R, y + ROW_H, TOP + sc.off, BOT + sc.off);
            Ui.round(c, r, 24f, Ui.SURFACE);

            // 圆角缩略图
            float pad = 8f;
            RectF th = new RectF(r.left + pad, r.top + pad,
                    r.left + pad + (r.height() - 2 * pad) * 1.35f, r.bottom - pad);
            Bitmap bm = ThumbCache.get(act, it.path, () ->
                    ThumbnailUtils.createVideoThumbnail(it.path, MediaStore.Video.Thumbnails.MINI_KIND));
            if (bm != null) {
                Ui.roundBitmap(c, bm, th, 16f);
            } else {
                Ui.round(c, th, 16f, Ui.SURFACE2);
                Ui.textC(c, "▶", th.centerX(), th.centerY() + 8f, 22f, Ui.FAINT);
            }
            // 时长角标
            if (it.duration > 0) {
                String d = MediaScan.dur(it.duration);
                RectF tag = new RectF(th.right - 56f, th.bottom - 26f, th.right - 4f, th.bottom - 2f);
                Ui.round(c, tag, 8f, 0xB4000000);
                Ui.textC(c, d, tag.centerX(), tag.centerY() + 5f, 12f, 0xFFFFFFFF);
            }

            String name = Ui.ellipsize(it.name, 17f, r.right - th.right - 24f);
            Ui.textLBold(c, name, th.right + 12f, r.top + r.height() * 0.42f, 17f, Ui.TEXT);
            Ui.textL(c, Fs.human(it.size), th.right + 12f, r.top + r.height() * 0.68f, 13f, Ui.FAINT);
        }
        c.restore();

        if (loading) Ui.textC(c, "扫描视频中…", 233f, 280f, 20f, Ui.FAINT);
        if (!loading && items.isEmpty()) {
            Ui.textC(c, "没有找到视频", 233f, 270f, 20f, Ui.FAINT);
            Ui.textC(c, "可先用快传把视频传到手表", 233f, 300f, 15f, Ui.FAINT);
        }
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
            if (y < TOP || y > BOT) return false;
            int idx = (int) ((y + sc.off - TOP - PAD) / (ROW_H + GAP));
            if (idx < 0 || idx >= items.size()) return false;
            MediaScan.Item it = items.get(idx);
            File f = it.path != null ? new File(it.path) : null;
            if (f != null && f.exists()) {
                com.watchsuite.VideoPlayerActivity.play(act, f);
            } else {
                act.toast("文件不存在");
            }
            return true;
        }
        return false;
    }
}
