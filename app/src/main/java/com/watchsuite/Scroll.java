package com.watchsuite;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * 简易滚动：拖拽位移 + 松手惯性（fling）。
 * 惯性用逐帧衰减模拟，越接近边界越快停住；到边界立即停，不回弹。
 */
public final class Scroll {

    public float off = 0f;
    public float max = 0f;

    private float lastY = 0f;
    private float downY = 0f;
    private boolean dragging = false;

    private long lastT = 0L;
    /** off 的每秒变化率（正=内容往上走）。 */
    private float vy = 0f;

    private final Runnable onFrame;
    private final Handler H = new Handler(Looper.getMainLooper());
    private int token = 0;

    public Scroll(Runnable onFrame) {
        this.onFrame = onFrame;
    }

    public Scroll() {
        this(null);
    }

    public void begin(float y) {
        stopFling();
        lastY = y;
        downY = y;
        lastT = SystemClock.uptimeMillis();
        vy = 0f;
        dragging = true;
    }

    /** 本次按下后累计拖动距离。 */
    public float dragDist() {
        return Math.abs(downY - lastY);
    }

    /** 快速甩动后松手时的速度（像素/秒），用于父级判断是否算滑动。 */
    public float flingSpeed() {
        return vy;
    }

    public void move(float y) {
        if (!dragging) return;
        long now = SystemClock.uptimeMillis();
        float dy = y - lastY;
        if (now > lastT) {
            float inst = -dy * 1000f / (now - lastT);   // 向上拖 = off 变大
            vy = vy * 0.6f + inst * 0.4f;               // 平滑，滤掉抖动
        }
        off -= dy;
        lastY = y;
        lastT = now;
        clamp();
    }

    /** 松手：位移够了就交给惯性继续滑。 */
    public void end() {
        dragging = false;
        if (Math.abs(vy) > 420f) fling(vy);
        vy = 0f;
    }

    public boolean isDragging() {
        return dragging;
    }

    private void fling(final float v0) {
        if (onFrame == null) return;
        final int my = ++token;
        H.post(new Runnable() {
            private long last = SystemClock.uptimeMillis();
            private float v = v0;

            @Override
            public void run() {
                if (my != token) return;
                long now = SystemClock.uptimeMillis();
                float dt = (now - last) / 1000f;
                last = now;
                if (dt > 0.05f) dt = 0.05f;              // 掉帧保护
                off += v * dt;
                v *= Math.pow(0.90f, dt * 60f);          // 约每帧 ×0.9
                boolean atEdge = off <= 0f || off >= max;
                clamp();
                onFrame.run();
                if (atEdge || Math.abs(v) < 40f) return;
                H.postDelayed(this, 16);
            }
        });
    }

    private void stopFling() {
        token++;
    }

    public void clamp() {
        if (max < 0f) max = 0f;
        if (off < 0f) off = 0f;
        if (off > max) off = max;
    }

    public void reset() {
        stopFling();
        off = 0f;
        max = 0f;
        vy = 0f;
    }
}
