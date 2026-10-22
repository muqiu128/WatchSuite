package com.watchsuite.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Ui;

import java.io.File;
import java.util.ArrayList;

/** 图片全屏查看：双指缩放 / 拖动 / 双击放大，左右点击翻页。 */
public class ViewerScreen extends Screen {

    private static final float BOX = 400f;     // 1x 时的适配框
    private static final float VIEW = 210f;    // 可视半边长（用于平移边界）
    private static final float MAX_ZOOM = 6f;
    private static final float DBL_ZOOM = 2.6f;

    private final ArrayList<File> files;
    private int index;
    private Bitmap cur;
    private String curPath;
    private int reqSeq = 0;

    private float zoom = 1f, panX = 0f, panY = 0f;
    private float downX, downY, lastX, lastY;
    private float pinch0, zoom0, pan0X, pan0Y, mid0X, mid0Y;
    private boolean multi, moved;
    private long lastTapAt;
    private float lastTapX, lastTapY;

    private static final Paint BP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Path CIRCLE = new Path();

    static {
        CIRCLE.addCircle(233f, 233f, 228f, Path.Direction.CW);
    }

    public ViewerScreen(MainActivity a, ArrayList<File> files, int start) {
        super(a);
        this.files = files;
        this.index = start;
    }

    private void ensureBitmap() {
        if (index < 0 || index >= files.size()) {
            cur = null;
            return;
        }
        String p = files.get(index).getAbsolutePath();
        if (p.equals(curPath) && cur != null) return;
        curPath = p;
        cur = null;
        final int seq = ++reqSeq;
        act.bg(() -> {
            Bitmap b = decode(p);
            act.ui(() -> {
                if (seq == reqSeq) {
                    cur = b;
                    invalidate();
                }
            });
        });
    }

    private static Bitmap decode(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            // 放大最多 6 倍，留足细节；同时限制内存
            while (o.outWidth / sample > 1400 || o.outHeight / sample > 1400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private float baseScale() {
        if (cur == null) return 1f;
        return Math.min(BOX / cur.getWidth(), BOX / cur.getHeight());
    }

    private void clampPan() {
        if (cur == null) {
            panX = panY = 0f;
            return;
        }
        float s = baseScale() * zoom;
        float maxX = Math.max(0f, cur.getWidth() * s / 2f - VIEW);
        float maxY = Math.max(0f, cur.getHeight() * s / 2f - VIEW);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
    }

    /** 以 (px,py) 为锚点缩放到 z。 */
    private void zoomAt(float z, float px, float py) {
        z = Math.max(1f, Math.min(MAX_ZOOM, z));
        float r = z / zoom;
        panX = (px - CX) - r * (px - CX - panX);
        panY = (py - CY) - r * (py - CY - panY);
        zoom = z;
        if (zoom <= 1.001f) {
            zoom = 1f;
            panX = panY = 0f;
        }
        clampPan();
        invalidate();
    }

    private void resetTransform() {
        zoom = 1f;
        panX = panY = 0f;
    }

    @Override
    public void draw(Canvas c) {
        ensureBitmap();
        c.drawColor(0xFF000000);

        c.save();
        c.clipPath(CIRCLE);
        if (cur != null) {
            float s = baseScale() * zoom;
            c.save();
            c.translate(CX + panX, CY + panY);
            c.scale(s, s);
            c.drawBitmap(cur, -cur.getWidth() / 2f, -cur.getHeight() / 2f, BP);
            c.restore();
        } else {
            Ui.textC(c, "加载中…", 233f, 240f, 20f, Ui.FAINT);
        }
        c.restore();

        Ui.circle(c, 70f, 66f, 24f, 0x99000000);
        Ui.textC(c, "‹", 70f, 76f, 32f, 0xFFFFFFFF);
        Ui.textC(c, (index + 1) + " / " + files.size(), 233f, 74f, 16f, 0xFFB9B9B9);

        if (zoom > 1.01f) {
            String z = String.format(java.util.Locale.US, "%.1f×", zoom);
            Ui.round(c, 193f, 396f, 273f, 424f, 14f, 0x99000000);
            Ui.textC(c, z, 233f, 416f, 14f, 0xFFFFFFFF);
        } else {
            Ui.textC(c, "双指缩放 · 双击放大", 233f, 414f, 13f, 0x66FFFFFF);
        }
    }

    @Override
    public boolean rawTouch() {
        return true;
    }

    @Override
    public boolean touchEvent(MotionEvent e) {
        int am = e.getActionMasked();
        switch (am) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                moved = false;
                multi = e.getPointerCount() > 1;
                pinch0 = 0f;
                zoom0 = zoom;
                pan0X = panX;
                pan0Y = panY;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                multi = true;
                pinch0 = dist(e);
                mid0X = mid(e, 0);
                mid0Y = mid(e, 1);
                zoom0 = zoom;
                pan0X = panX;
                pan0Y = panY;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() >= 2) {
                    if (pinch0 > 1f) {
                        float z = Math.max(1f, Math.min(MAX_ZOOM, zoom0 * dist(e) / pinch0));
                        float r = z / zoom0;
                        float mx = mid(e, 0), my = mid(e, 1);
                        panX = (mx - CX) - r * (mid0X - CX - pan0X);
                        panY = (my - CY) - r * (mid0Y - CY - pan0Y);
                        zoom = z;
                        if (zoom <= 1.001f) {
                            zoom = 1f;
                            panX = panY = 0f;
                        }
                        clampPan();
                        invalidate();
                    }
                } else if (zoom > 1.001f) {
                    panX += e.getX() - lastX;
                    panY += e.getY() - lastY;
                    clampPan();
                    invalidate();
                }
                if (Math.abs(e.getX() - downX) > 14 || Math.abs(e.getY() - downY) > 14) moved = true;
                lastX = e.getX();
                lastY = e.getY();
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerCount() - 1 == 1) {
                    int keep = (e.getActionIndex() == 0) ? 1 : 0;
                    lastX = e.getX(keep);
                    lastY = e.getY(keep);
                    pinch0 = 0f;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!multi && !moved) tap(e.getX(), e.getY());
                multi = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                multi = false;
                return true;
            default:
                return true;
        }
    }

    private static float dist(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private static float mid(MotionEvent e, int axis) {
        return axis == 0 ? (e.getX(0) + e.getX(1)) / 2f : (e.getY(0) + e.getY(1)) / 2f;
    }

    private void tap(float x, float y) {
        long now = System.currentTimeMillis();
        if (zoom > 1.01f) {
            resetTransform();
            lastTapAt = 0;
            invalidate();
            return;
        }
        if (now - lastTapAt < 340 && Math.abs(x - lastTapX) < 60 && Math.abs(y - lastTapY) < 60) {
            lastTapAt = 0;
            zoomAt(DBL_ZOOM, x, y);
            return;
        }
        lastTapAt = now;
        lastTapX = x;
        lastTapY = y;

        if (hitBack(x, y)) {
            act.pop();
            return;
        }
        if (x > 320f) {
            if (index < files.size() - 1) index++;
            else return;
        } else if (x < 146f) {
            if (index > 0) index--;
            else return;
        } else {
            return;
        }
        resetTransform();
        curPath = null;
        invalidate();
    }

    @Override
    public boolean back() {
        if (zoom > 1.01f) {
            resetTransform();
            invalidate();
            return true;
        }
        act.pop();
        return true;
    }
}
