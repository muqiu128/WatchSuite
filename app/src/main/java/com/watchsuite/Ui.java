package com.watchsuite;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** 统一的圆屏绘制工具：配色、圆角卡片、文字、进度条。 */
public final class Ui {

    // 配色：纯黑底 + 黑白两色主视觉（重点色一律用白），仅功能图标保留彩色
    public static final int BG       = 0xFF000000;
    public static final int SURFACE  = 0xFF121212;
    public static final int SURFACE2 = 0xFF1F1F1F;
    public static final int LINE     = 0xFF2C2C2C;
    public static final int TEXT     = 0xFFFFFFFF;
    public static final int DIM      = 0xFFA9A9A9;
    public static final int FAINT    = 0xFF6A6A6A;
    public static final int ACCENT   = 0xFFFFFFFF;
    public static final int ACCENT2  = 0xFFD6D6D6;
    public static final int OK       = 0xFFFFFFFF;
    public static final int WARN     = 0xFFFFC65C;
    public static final int DANGER   = 0xFFFF5A5A;
    /** 强调色上的文字色（按钮/勾选/播放键）。 */
    public static final int ON_ACCENT = 0xFF000000;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint T = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Ui() {}

    public static void fill(Canvas c, RectF r, int color) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawRect(r, P);
    }

    public static void round(Canvas c, RectF r, float rad, int color) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawRoundRect(r, rad, rad, P);
    }

    public static void round(Canvas c, float l, float t, float rr, float b, float rad, int color) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawRoundRect(l, t, rr, b, rad, rad, P);
    }

    public static void stroke(Canvas c, RectF r, float rad, float w, int color) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setColor(color);
        c.drawRoundRect(r, rad, rad, P);
    }

    public static void circle(Canvas c, float x, float y, float rad, int color) {
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawCircle(x, y, rad, P);
    }

    public static void circleStroke(Canvas c, float x, float y, float rad, float w, int color) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setColor(color);
        c.drawCircle(x, y, rad, P);
    }

    public static void textC(Canvas c, String s, float cx, float baseline, float size, int color) {
        T.setStyle(Paint.Style.FILL);
        T.setTextSize(size);
        T.setColor(color);
        T.setTextAlign(Paint.Align.CENTER);
        T.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.drawText(s, cx, baseline, T);
    }

    public static void textL(Canvas c, String s, float x, float baseline, float size, int color) {
        T.setStyle(Paint.Style.FILL);
        T.setTextSize(size);
        T.setColor(color);
        T.setTextAlign(Paint.Align.LEFT);
        T.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        c.drawText(s, x, baseline, T);
    }

    public static void textLBold(Canvas c, String s, float x, float baseline, float size, int color) {
        T.setStyle(Paint.Style.FILL);
        T.setTextSize(size);
        T.setColor(color);
        T.setTextAlign(Paint.Align.LEFT);
        T.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.drawText(s, x, baseline, T);
    }

    public static void textR(Canvas c, String s, float x, float baseline, float size, int color) {
        T.setStyle(Paint.Style.FILL);
        T.setTextSize(size);
        T.setColor(color);
        T.setTextAlign(Paint.Align.RIGHT);
        T.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        c.drawText(s, x, baseline, T);
    }

    public static float measure(String s, float size) {
        T.setTextSize(size);
        T.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return T.measureText(s);
    }

    public static float measureBold(String s, float size) {
        T.setTextSize(size);
        T.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return T.measureText(s);
    }

    /** 超出宽度时截断并加省略号。 */
    public static String ellipsize(String s, float size, float maxW) {
        if (s == null) return "";
        if (measure(s, size) <= maxW) return s;
        String ell = "…";
        float ew = measure(ell, size);
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (measure(s.substring(0, mid), size) + ew <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + ell;
    }

    /** 水平进度条（圆角）。 */
    public static void bar(Canvas c, RectF r, float ratio, int fg, int bg) {
        float rad = r.height() / 2f;
        round(c, r, rad, bg);
        if (ratio <= 0) return;
        if (ratio > 1) ratio = 1;
        float w = r.width() * ratio;
        if (w < r.height()) w = r.height();
        round(c, r.left, r.top, r.left + w, r.bottom, rad, fg);
    }

    /** 环形进度（用于存储占比）。 */
    public static void ring(Canvas c, float cx, float cy, float rad, float stroke,
                            float ratio, int fg, int bg) {
        RectF box = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(stroke);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setColor(bg);
        c.drawArc(box, 0, 360, false, P);
        P.setColor(fg);
        c.drawArc(box, -90, 360f * Math.max(0f, Math.min(1f, ratio)), false, P);
        P.setStrokeCap(Paint.Cap.BUTT);
    }

    /** 列表行的类型图标：圆角方块 + 单字。 */
    public static void glyph(Canvas c, RectF r, String ch, int color) {
        round(c, r, r.width() * 0.28f, alpha(color, 0x26));
        textC(c, ch, r.centerX(), r.centerY() + r.height() * 0.19f,
                r.height() * 0.46f, color);
    }

    /** 把颜色调暗/提亮（比例 0..1，越大越暗）。 */
    public static int dim(int color, float k) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * k);
        int g = (int) (((color >> 8) & 0xFF) * k);
        int b = (int) ((color & 0xFF) * k);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    private static final android.graphics.Rect SR = new android.graphics.Rect();
    private static final android.graphics.Rect DR = new android.graphics.Rect();
    private static BitmapShader SHADER;
    private static final Matrix MATRIX = new Matrix();
    public static final android.graphics.Path PATH = new android.graphics.Path();
    private static final RectF ROUND = new RectF();

    /** 整图绘制到目标矩形（拉伸）。 */
    public static void bitmap(Canvas c, Bitmap b, RectF dst) {
        if (b == null || b.isRecycled()) return;
        SR.set(0, 0, b.getWidth(), b.getHeight());
        DR.set((int) dst.left, (int) dst.top, (int) dst.right, (int) dst.bottom);
        c.drawBitmap(b, SR, DR, null);
    }

    /** 裁剪填充绘制（保持比例，居中裁掉超出的部分）。 */
    public static void bitmapCrop(Canvas c, Bitmap b, RectF dst) {
        if (b == null || b.isRecycled()) return;
        float bw = b.getWidth(), bh = b.getHeight();
        float scale = Math.max(dst.width() / bw, dst.height() / bh);
        float sw = dst.width() / scale, sh = dst.height() / scale;
        SR.set((int) ((bw - sw) / 2f), (int) ((bh - sh) / 2f),
                (int) ((bw + sw) / 2f), (int) ((bh + sh) / 2f));
        DR.set((int) dst.left, (int) dst.top, (int) dst.right, (int) dst.bottom);
        c.drawBitmap(b, SR, DR, null);
    }

    /** 圆角位图（保持比例居中裁剪，边缘圆润）。 */
    public static void roundBitmap(Canvas c, Bitmap b, RectF dst, float rad) {
        if (b == null || b.isRecycled()) return;
        SHADER = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float bw = b.getWidth(), bh = b.getHeight();
        float scale = Math.max(dst.width() / bw, dst.height() / bh);
        MATRIX.reset();
        MATRIX.setScale(scale, scale);
        MATRIX.postTranslate(dst.left + (dst.width() - bw * scale) * 0.5f,
                dst.top + (dst.height() - bh * scale) * 0.5f);
        SHADER.setLocalMatrix(MATRIX);
        P.setStyle(Paint.Style.FILL);
        P.setShader(SHADER);
        c.drawRoundRect(dst, rad, rad, P);
        P.setShader(null);
    }

    /** 圆角裁剪区域，返回 save 计数（配 restoreToCount 用）。 */
    public static int roundClip(Canvas c, RectF r, float rad) {
        int save = c.save();
        ROUND.set(r);
        PATH.reset();
        PATH.addRoundRect(ROUND, rad, rad, android.graphics.Path.Direction.CW);
        c.clipPath(PATH);
        return save;
    }

    /**
     * 卡片流布局：行中心离列表可视中心越远，整行等比缩小并水平收拢（类似手表通知层叠）。
     * 返回该行的实际绘制矩形；布局槽位不变，触摸命中仍按槽位算。
     */
    public static RectF flow(float slotL, float slotT, float slotR, float slotB,
                             float viewT, float viewB) {
        float cy = (slotT + slotB) * 0.5f;
        float mid = (viewT + viewB) * 0.5f;
        float half = (viewB - viewT) * 0.5f;
        float d = Math.min(1f, Math.abs(cy - mid) / half);
        float s = 1f - 0.34f * d * d;   // 平方衰减：中间几乎不变，边缘才明显变小
        float w = (slotR - slotL) * s;
        float h = (slotB - slotT) * s;
        float cx = (slotL + slotR) * 0.5f;
        return new RectF(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f);
    }

    /**
     * 卡片流内容的首尾下沉量：列表在顶/底时，首尾卡片「下沉」到接近全尺寸的
     * 位置渲染，上面/下面留一点空（只在下沉时可见）。
     */
    public static float flowPad(float rowH, float viewT, float viewB) {
        float mid = (viewT + viewB) * 0.5f;
        float half = (viewB - viewT) * 0.5f;
        float target = mid - 0.28f * half;   // 首卡中心放到 d≈0.28（s≈0.97）
        return Math.max(0f, target - viewT - rowH * 0.5f);
    }
}
