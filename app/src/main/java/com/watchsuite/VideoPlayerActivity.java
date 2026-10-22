package com.watchsuite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.watchsuite.Ui;
import com.watchsuite.core.Fs;

import java.io.File;
import java.util.Locale;

/** 视频播放页：黑底、比例适配、双指缩放/拖动、可隐藏控制层。 */
public class VideoPlayerActivity extends Activity
        implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnVideoSizeChangedListener {

    private static final float W = 466f, H = 466f;
    private static final float MAX_ZOOM = 5f;

    public static void play(Context c, File f) {
        Intent i = new Intent(c, VideoPlayerActivity.class);
        i.putExtra("path", f.getAbsolutePath());
        c.startActivity(i);
    }

    private SurfaceView surface;
    private Overlay overlay;
    private MediaPlayer mp;
    private String path;
    private String title = "视频";
    private boolean started = false;
    private boolean prepared = false;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable hider = () -> overlay.showUi(false);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            overlay.invalidate();
            h.postDelayed(this, 400);
        }
    };

    // 缩放 / 平移
    private float zoom = 1f, panX = 0f, panY = 0f;
    private float pinch0, zoom0, mid0X, mid0Y, pan0X, pan0Y;
    private float downX, downY, lastX, lastY;
    private boolean multi, moved;
    private long lastTapAt;
    private float lastTapX, lastTapY;
    private Runnable pendingTap;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        path = getIntent().getStringExtra("path");
        if (path != null) title = new File(path).getName();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surface = new SurfaceView(this);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sp.gravity = Gravity.CENTER;
        root.addView(surface, sp);

        overlay = new Overlay(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        surface.getHolder().addCallback(this);
        hideSystemUi();
        wakeControls();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void wakeControls() {
        overlay.showUi(true);
        h.removeCallbacks(hider);
        h.postDelayed(hider, 3500);
    }

    private void setupPlayer(SurfaceHolder holder) {
        try {
            mp = new MediaPlayer();
            mp.setDataSource(path);
            mp.setDisplay(holder);
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setOnPreparedListener(this);
            mp.setOnCompletionListener(this);
            mp.setOnErrorListener(this);
            mp.setOnVideoSizeChangedListener(this);
            mp.prepareAsync();
        } catch (Throwable t) {
            Toast.makeText(this, "无法播放该视频", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ---------------- 缩放 ----------------

    private void applyTransform() {
        if (surface == null) return;
        float w = surface.getWidth(), hgt = surface.getHeight();
        if (w <= 0 || hgt <= 0) return;
        // panX/panY 是 466 逻辑坐标下的位移，换算到真实像素
        float maxX = Math.max(0f, (W * zoom - W) / 2f);
        float maxY = Math.max(0f, (H * zoom - H) / 2f);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
        surface.setScaleX(zoom);
        surface.setScaleY(zoom);
        surface.setTranslationX(panX * overlay.k);
        surface.setTranslationY(panY * overlay.k);
    }

    private void zoomAt(float z, float px, float py) {
        z = Math.max(1f, Math.min(MAX_ZOOM, z));
        float r = z / zoom;
        panX = (px - W / 2f) - r * (px - W / 2f - panX);
        panY = (py - H / 2f) - r * (py - H / 2f - panY);
        zoom = z;
        if (zoom <= 1.001f) {
            zoom = 1f;
            panX = panY = 0f;
        }
        applyTransform();
        overlay.invalidate();
    }

    private void resetZoom() {
        zoom = 1f;
        panX = panY = 0f;
        applyTransform();
        overlay.invalidate();
    }

    // ---------------- 播放器回调 ----------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!started) {
            started = true;
            setupPlayer(holder);
        } else if (mp != null) {
            mp.setDisplay(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int hgt) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mp != null) mp.setDisplay(null);
    }

    @Override
    public void onPrepared(MediaPlayer m) {
        prepared = true;
        m.start();
        h.post(ticker);
    }

    @Override
    public void onCompletion(MediaPlayer m) {
        finish();
    }

    @Override
    public boolean onError(MediaPlayer m, int what, int extra) {
        Toast.makeText(this, "播放出错", Toast.LENGTH_SHORT).show();
        finish();
        return true;
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer m, int w, int hgt) {
        if (w <= 0 || hgt <= 0) return;
        float scale = Math.min(W / w, H / hgt);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) surface.getLayoutParams();
        p.width = Math.round(w * scale);
        p.height = Math.round(hgt * scale);
        p.gravity = Gravity.CENTER;
        surface.setLayoutParams(p);
        surface.post(this::applyTransform);
    }

    private void togglePlay() {
        if (mp == null || !prepared) return;
        if (mp.isPlaying()) mp.pause();
        else mp.start();
        overlay.invalidate();
    }

    private void seek(float ratio) {
        if (mp == null || !prepared) return;
        int dur = mp.getDuration();
        if (dur > 0) mp.seekTo((int) (dur * ratio));
        overlay.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        if (mp != null) {
            try {
                mp.stop();
            } catch (Throwable ignored) {
            }
            mp.release();
            mp = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (zoom > 1.001f) {
            resetZoom();
            return;
        }
        finish();
    }

    /** 控制层：标题 / 关闭 / 播放暂停 / 进度条 + 双指缩放。 */
    private class Overlay extends android.view.View {

        private boolean ui = true;
        // 通用适配：控制层按 466 逻辑坐标，缩放到实际分辨率
        private float k = 1f, offX = 0f, offY = 0f;

        public Overlay(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            k = Math.min(w, h) / 466f;
            offX = (w - 466f * k) / 2f;
            offY = (h - 466f * k) / 2f;
        }

        private MotionEvent xform(MotionEvent raw) {
            MotionEvent m = MotionEvent.obtain(raw);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.graphics.Matrix m4 = new android.graphics.Matrix();
                m4.setScale(1f / k, 1f / k);
                m4.postTranslate(-offX / k, -offY / k);
                m.transform(m4);
            } else {
                m.offsetLocation(-offX, -offY);
            }
            return m;
        }

        void showUi(boolean s) {
            ui = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!ui) return;
            int sv = c.save();
            c.translate(offX, offY);
            c.scale(k, k);
            draw466(c);
            c.restoreToCount(sv);
        }

        private void draw466(Canvas c) {
            // 顶部渐变 + 标题 + 关闭
            Ui.round(c, 44f, 40f, 96f, 92f, 26f, 0x66000000);
            Ui.textC(c, "‹", 70f, 78f, 34f, 0xFFFFFFFF);
            Ui.textC(c, Ui.ellipsize(title, 18f, 280f), 233f, 76f, 18f, 0xFFE8E8E8);

            if (zoom > 1.001f) {
                String z = String.format(Locale.US, "%.1f×", zoom);
                Ui.round(c, 372f, 42f, 440f, 72f, 15f, 0x77000000);
                Ui.textC(c, z, 406f, 63f, 14f, 0xFFFFFFFF);
            }

            boolean playing = mp != null && prepared && mp.isPlaying();
            if (!playing) {
                Ui.circle(c, 233f, 233f, 40f, 0x66000000);
                Ui.textC(c, "▶", 233f, 247f, 40f, 0xFFFFFFFF);
            }

            // 底部进度条
            long dur = mp != null && prepared ? mp.getDuration() : 0;
            long pos = mp != null ? mp.getCurrentPosition() : 0;
            float ratio = dur > 0 ? (float) pos / dur : 0f;
            RectF bar = new RectF(60f, 418f, 406f, 428f);
            Ui.bar(c, bar, ratio, 0xFFFFFFFF, 0x33FFFFFF);
            Ui.circle(c, bar.left + ratio * bar.width(), bar.centerY(), 9f, 0xFFFFFFFF);
            Ui.textL(c, Fs.humanMs(pos), 60f, 410f, 13f, 0xFFB9B9B9);
            Ui.textR(c, Fs.humanMs(dur), 406f, 410f, 13f, 0xFFB9B9B9);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            MotionEvent e = xform(ev);
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
                            panX = (mx - W / 2f) - r * (mid0X - W / 2f - pan0X);
                            panY = (my - H / 2f) - r * (mid0Y - H / 2f - pan0Y);
                            zoom = z;
                            if (zoom <= 1.001f) {
                                zoom = 1f;
                                panX = panY = 0f;
                            }
                            applyTransform();
                            invalidate();
                        }
                    } else if (zoom > 1.001f) {
                        panX += e.getX() - lastX;
                        panY += e.getY() - lastY;
                        applyTransform();
                        invalidate();
                    }
                    if (Math.abs(e.getX() - downX) > 14 || Math.abs(e.getY() - downY) > 14) {
                        moved = true;
                    }
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
                    if (!multi && !moved) scheduleTap(e.getX(), e.getY());
                    multi = false;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    multi = false;
                    return true;

                default:
                    return true;
            }
        }

        private float dist(MotionEvent e) {
            float dx = e.getX(0) - e.getX(1);
            float dy = e.getY(0) - e.getY(1);
            return (float) Math.hypot(dx, dy);
        }

        private float mid(MotionEvent e, int axis) {
            return axis == 0 ? (e.getX(0) + e.getX(1)) / 2f : (e.getY(0) + e.getY(1)) / 2f;
        }

        /** 延迟 260ms 再判定单击，避免双击缩放时误触播放/暂停。 */
        private void scheduleTap(final float x, final float y) {
            long now = System.currentTimeMillis();
            if (now - lastTapAt < 320 && Math.abs(x - lastTapX) < 60
                    && Math.abs(y - lastTapY) < 60) {
                lastTapAt = 0;
                if (pendingTap != null) {
                    h.removeCallbacks(pendingTap);
                    pendingTap = null;
                }
                if (zoom > 1.001f) resetZoom();
                else zoomAt(2.5f, x, y);
                return;
            }
            lastTapAt = now;
            lastTapX = x;
            lastTapY = y;
            if (pendingTap != null) h.removeCallbacks(pendingTap);
            pendingTap = () -> {
                pendingTap = null;
                singleTap(x, y);
            };
            h.postDelayed(pendingTap, 260);
        }

        private void singleTap(float x, float y) {
            if (x >= 44f && x <= 96f && y >= 40f && y <= 92f) {
                finish();
                return;
            }
            if (y >= 404f && y <= 442f && x >= 56f && x <= 410f) {
                float r = (x - 60f) / 346f;
                seek(Math.max(0f, Math.min(1f, r)));
                wakeControls();
                return;
            }
            // 中间区域：播放/暂停
            if (y > 100f && y < 400f) {
                togglePlay();
                wakeControls();
                return;
            }
            wakeControls();
        }
    }
}
