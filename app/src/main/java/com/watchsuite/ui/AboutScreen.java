package com.watchsuite.ui;

import android.graphics.Canvas;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Ui;
import com.watchsuite.core.Prefs;

/** 关于页：版本、功能说明与设置开关。 */
public class AboutScreen extends Screen {

    public AboutScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void draw(Canvas c) {
        header(c, "关于", null);

        String ver = "1.0";
        try {
            String v = act.getPackageManager()
                    .getPackageInfo(act.getPackageName(), 0).versionName;
            if (v != null && v.length() > 0) ver = v;
        } catch (Throwable ignored) {
        }

        Ui.round(c, 78f, 132f, 388f, 252f, 18f, Ui.SURFACE);
        Ui.textLBold(c, "腕上管家 v" + ver, 100f, 168f, 22f, Ui.TEXT);
        Ui.textL(c, "文件 · 应用 · 视频 · 音乐 · 相册", 100f, 198f, 15f, Ui.DIM);
        Ui.textL(c, "清理 · 分析 · 局域网快传", 100f, 222f, 15f, Ui.DIM);

        Ui.textL(c, "反馈邮箱", 86f, 292f, 14f, Ui.FAINT);
        Ui.textL(c, "2176974735@qq.com", 86f, 314f, 14f, Ui.DIM);
        Ui.textL(c, "QQ：2176974735", 86f, 336f, 14f, Ui.DIM);
        Ui.textL(c, "微信：shiMuQiuY", 86f, 358f, 14f, Ui.DIM);

        // 重新选择表盘形状
        Ui.round(c, 140f, 378f, 326f, 414f, 18f, Ui.SURFACE);
        Ui.textC(c, "重选表盘形状（当前：" + (MainActivity.SQUARE ? "方形" : "圆形") + "）",
                233f, 401f, 14f, Ui.TEXT);

        // 音乐后台播放开关
        Ui.round(c, 140f, 424f, 326f, 460f, 18f, Ui.SURFACE);
        boolean on = Prefs.bgPlay(act);
        Ui.textC(c, "后台继续播放音乐：" + (on ? "开" : "关"),
                233f, 447f, 14f, on ? 0xFF4CD964 : Ui.TEXT);

        Ui.textC(c, "WMA 有声书自动转码 · 自研无依赖", 233f, 380f, 11f, Ui.FAINT);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_UP) {
            if (hitBack(x, y)) { act.pop(); return true; }
            if (x >= 140f && x <= 326f && y >= 378f && y <= 414f) {
                act.push(new ShapeScreen(act));
                return true;
            }
            if (x >= 140f && x <= 326f && y >= 424f && y <= 460f) {
                Prefs.bgPlay(act, !Prefs.bgPlay(act));
                act.ui(this::invalidate);
                return true;
            }
        }
        return false;
    }
}
