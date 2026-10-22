package com.watchsuite.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Scroll;
import com.watchsuite.Ui;
import com.watchsuite.core.Index;
import com.watchsuite.core.MediaScan;
import com.watchsuite.core.ThumbCache;

import java.io.File;
import java.util.ArrayList;

/** 相册：三列缩略图网格（参考腕间图库的全出血风格）。 */
public class GalleryScreen extends Screen {

    private static final float L = 40f, R = 426f, TOP = 116f, BOT = 424f;
    private static final int COLS = 3;
    private static final float CELL = (R - L - 2 * 6f) / COLS; // 124.7
    private static final float CELL_GAP = 10f, ROW_GAP = 10f;
    private static final float ROW_H = CELL + ROW_GAP;

    private final ArrayList<MediaScan.Item> items = new ArrayList<>();
    private final Scroll sc = new Scroll(this::invalidate);
    private boolean loading = true;

    public GalleryScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        Index.Snap s0 = Index.get();
        if (s0 != null) apply(s0.images);
        Index.ensure(act, false, s -> apply(s.images));
    }

    private void apply(ArrayList<MediaScan.Item> v) {
        items.clear();
        items.addAll(v);
        int rows = (items.size() + COLS - 1) / COLS;
        sc.max = Math.max(0, rows * ROW_H - (BOT - TOP));
        sc.clamp();
        loading = false;
        invalidate();
    }

    @Override
    public void draw(Canvas c) {
        header(c, "相册", items.size() + " 张");

        c.save();
        c.clipRect(L, TOP, R, BOT);
        c.translate(0, -sc.off);
        for (int i = 0; i < items.size(); i++) {
            final int fi = i;
            int r = i / COLS, col = i % COLS;
            float x = L + col * (CELL + CELL_GAP);
            float y = TOP + r * ROW_H;
            if (y + CELL < TOP + sc.off || y > BOT + sc.off) continue;
            RectF cell = new RectF(x, y, x + CELL, y + CELL);
            Bitmap bm = ThumbCache.get(act, items.get(fi).path, () -> decode(items.get(fi).path));
            if (bm != null) {
                Ui.roundBitmap(c, bm, cell, 18f);
            } else {
                Ui.round(c, cell, 18f, Ui.SURFACE);
                Ui.textC(c, "图", cell.centerX(), cell.centerY() + 8f, 22f, Ui.FAINT);
            }
        }
        c.restore();

        if (loading) Ui.textC(c, "扫描图片中…", 233f, 270f, 20f, Ui.FAINT);
        if (!loading && items.isEmpty()) {
            Ui.textC(c, "没有找到图片", 233f, 260f, 20f, Ui.FAINT);
        }
    }

    private static Bitmap decode(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / sample > 320 || o.outHeight / sample > 320) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) {
            return null;
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
            if (y < TOP || y > BOT || x < L || x > R) return false;
            int col = (int) ((x - L) / (CELL + CELL_GAP));
            if (col >= COLS) col = COLS - 1;
            int row = (int) ((y + sc.off - TOP) / ROW_H);
            int idx = row * COLS + col;
            if (idx >= 0 && idx < items.size()) {
                ArrayList<File> fs = new ArrayList<>();
                for (MediaScan.Item it : items) fs.add(new File(it.path));
                act.push(new ViewerScreen(act, fs, idx));
            }
            return true;
        }
        return false;
    }
}
