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
    private final RectF bar = new RectF(79f, 92f, 387f, 104f);
    /** 最下面一行的版本信息，点它进关于页。 */
    private final RectF verRect = new RectF(139f, 392f, 327f, 428f);

    private long total = 0, avail = 0;
    private String ver = "1.0";

    public HomeScreen(MainActivity a) {
        super(a);
        float w = 148f, h = 52f, gx = 12f, gy = 8f;
        for (int i = 0; i < 8; i++) {
            int r = i / 2, col = i % 2;
            float l = 79f + col * (w + gx);
            float t = 152f + r * (h + gy);
            tiles[i] = new RectF(l, t, l + w, t + h);
        }
        try {
            String v = a.getPackageManager()
                    .getPackageInfo(a.getPackageName(), 0).versionName;
            if (v != null && v.length() > 0) ver = v;
        } catch (Throwable ignored) {
        }
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
        Ui.textC(c, "腕上管家", 233f, 74f, 24f, Ui.TEXT);

        float ratio = total > 0 ? (float) (total - avail) / total : 0f;
        Ui.bar(c, bar, ratio, Ui.ACCENT, Ui.SURFACE2);
        String info = total > 0
                ? String.format(Locale.US, "已用 %d%% · 剩 %s / %s", Math.round(ratio * 100),
                Fs.human(avail), Fs.human(total))
                : "读取存储中…";
        Ui.textC(c, info, 233f, 130f, 16f, Ui.DIM);

        for (int i = 0; i < 8; i++) {
            RectF t = tiles[i];
            Ui.round(c, t, 16f, Ui.SURFACE);
            RectF g = new RectF(t.left + 12f, t.centerY() - 15f, t.left + 42f, t.centerY() + 15f);
            Ui.round(c, g, 9f, Ui.alpha(COLORS[i], 0x26));
            Ui.textC(c, GLYPHS[i], g.centerX(), g.centerY() + 7f, 17f, COLORS[i]);
            Ui.textLBold(c, NAMES[i], t.left + 50f, t.top + 23f, 20f, Ui.TEXT);
            Ui.textL(c, SUBS[i], t.left + 50f, t.top + 42f, 13f, Ui.FAINT);
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
