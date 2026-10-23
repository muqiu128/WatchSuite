package com.watchsuite.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Ui;
import com.watchsuite.core.Fs;

import java.util.Locale;

/** 首页：存储概览 + 八个功能入口。 */
public class HomeScreen extends Screen {

    private static final String[] NAMES = {"文件", "应用", "视频", "音乐", "相册", "清理", "分析", "快传"};
    private static final String[] SUBS = {"浏览 / 安装", "空间占比", "本地视频", "本地音乐",
            "图片浏览", "缓存垃圾", "空间占用", "网页互传"};
    private static final String[] GLYPHS = {"文", "应", "播", "音", "图", "清", "析", "传"};
    private static final int[] COLORS = {0xFF4DA3FF, 0xFFE070FF, 0xFF58D6C9, 0xFF9B7BFF,
            0xFF3DDC84, 0xFFFFB454, 0xFFFF8FA3, 0xFF66B3FF};

    private final RectF[] tiles = new RectF[8];
    /** 最下面一行的版本信息，点它进关于页。 */
    private final RectF verRect = new RectF(139f, 392f, 327f, 428f);

    private long total = 0, avail = 0;
    private String ver = "1.0";

    public HomeScreen(MainActivity a) {
        super(a);
        if (MainActivity.SQUARE) layoutSquare(); else layoutRound();
        try {
            String v = a.getPackageManager()
                    .getPackageInfo(a.getPackageName(), 0).versionName;
            if (v != null && v.length() > 0) ver = v;
        } catch (Throwable ignored) {
        }
    }

    /** 圆表布局：2 列 × 4 行。 */
    private void layoutRound() {
        float w = 156f, h = 58f, gx = 14f, gy = 9f;
        for (int i = 0; i < 8; i++) {
            int r = i / 2, col = i % 2;
            float l = 71f + col * (w + gx);
            float t = 120f + r * (h + gy);
            tiles[i] = new RectF(l, t, l + w, t + h);
        }
    }

    /** 方表布局：3 列（3+3+2 居中），面积全用上。 */
    private void layoutSquare() {
        float w = 138f, h = 64f, gx = 13f, gy = 10f;
        float l0 = (466f - (3 * w + 2 * gx)) / 2f;
        for (int i = 0; i < 8; i++) {
            int r = i / 3, col = i % 3;
            if (i == 7) col = 2;   // 快传放第三排第三个，与分析左右对称
            float l = l0 + col * (w + gx);
            float t = 112f + r * (h + gy);
            tiles[i] = new RectF(l, t, l + w, t + h);
        }
        verRect.set(90f, 352f, 376f, 388f);
    }

    @Override
    public void onEnter() {
        loadSpace();
    }

    @Override
    public void onResume() {
        // 从清理/文件页返回时空间数字要跟着变
        loadSpace();
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

    @Override
    public void draw(Canvas c) {
        float ratio = total > 0 ? (float) (total - avail) / total : 0f;

        // 表圈空间圆环：纯装饰，不参与触摸；颜色随紧张度绿→黄→红
        Ui.bezelRing(c, ratio, MainActivity.SQUARE);

        Ui.textC(c, "腕上管家", 233f, 62f, 22f, Ui.TEXT);
        String info = total > 0
                ? String.format(Locale.US, "已用 %d%% · 剩 %s", Math.round(ratio * 100),
                Fs.human(avail))
                : "读取存储中…";
        Ui.textC(c, info, 233f, 88f, 13f, Ui.tension(ratio));

        for (int i = 0; i < 8; i++) {
            RectF t = tiles[i];
            Ui.round(c, t, 16f, Ui.SURFACE);
            RectF g = new RectF(t.left + 12f, t.centerY() - 16f, t.left + 44f, t.centerY() + 16f);
            Ui.round(c, g, 9f, Ui.alpha(COLORS[i], 0x26));
            Ui.textC(c, GLYPHS[i], g.centerX(), g.centerY() + 7f, 18f, COLORS[i]);
            Ui.textLBold(c, NAMES[i], t.left + 52f, t.top + 25f, 20f, Ui.TEXT);
            Ui.textL(c, SUBS[i], t.left + 52f, t.top + 45f, 13f, Ui.FAINT);
        }

        // 版本信息固定在最下面一行
        Ui.round(c, verRect, 18f, Ui.SURFACE);
        Ui.textC(c, "腕上管家 v" + ver, verRect.centerX(), verRect.centerY() + 5f, 13f, Ui.FAINT);
    }

    /** 空白 = 不在任何磁贴/版本按钮上（标题和存储条附近也允许划）。 */
    @Override
    public boolean blankAt(float x, float y) {
        for (RectF t : tiles) if (t.contains(x, y)) return false;
        return !verRect.contains(x, y);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action != MotionEvent.ACTION_UP) return false;
        if (verRect.contains(x, y)) {
            act.push(new AboutScreen(act));
            return true;
        }
        for (int i = 0; i < 8; i++) {
            if (tiles[i].contains(x, y)) {
                open(i);
                return true;
            }
        }
        return false;
    }

    private void open(int i) {
        switch (i) {
            case 0: act.push(new FilesScreen(act, Fs.root())); break;
            case 1: act.push(new AppsScreen(act)); break;
            case 2: act.push(new VideosScreen(act)); break;
            case 3: act.push(new MusicScreen(act)); break;
            case 4: act.push(new GalleryScreen(act)); break;
            case 5: act.push(new CleanScreen(act)); break;
            case 6: act.push(new StorageScreen(act)); break;
            case 7: act.push(new TransferScreen(act)); break;
        }
    }
}
