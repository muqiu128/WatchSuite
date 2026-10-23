package com.watchsuite.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Ui;
import com.watchsuite.core.Prefs;

/** 设置页：列表动效、空间条位置、后台播放、表盘形状。 */
public class SettingsScreen extends Screen {

    private static final float L = 86f, R = 380f, H = 46f, GY = 12f;

    private final RectF[] rows = new RectF[4];

    public SettingsScreen(MainActivity a) {
        super(a);
        for (int i = 0; i < 4; i++) {
            float t = 146f + i * (H + GY);
            rows[i] = new RectF(L, t, R, t + H);
        }
    }

    @Override
    public void onEnter() {
        // 从表盘形状页回来时刷新（SQUARE 可能变了，顺手同步一遍）
        Ui.FLAT_FLOW = Prefs.flatFlow(act);
        Ui.RING_TOP = Prefs.ringTop(act);
    }

    @Override
    public void draw(Canvas c) {
        header(c, "设置", null);
        String[] labels = {
                "列表动效：" + (Ui.FLAT_FLOW ? "等底信息流" : "圆弧透视"),
                "空间条位置：" + (Ui.RING_TOP ? "最上端" : "表圈圆环"),
                "后台继续播放音乐：" + (Prefs.bgPlay(act) ? "开" : "关"),
                "重选表盘形状（当前：" + (MainActivity.SQUARE ? "方形" : "圆形") + "）"
        };
        for (int i = 0; i < 4; i++) {
            RectF r = rows[i];
            Ui.round(c, r, 18f, Ui.SURFACE);
            int col = i == 2 && Prefs.bgPlay(act) ? 0xFF4CD964 : Ui.TEXT;
            Ui.textC(c, labels[i], r.centerX(), r.centerY() + 5f, 14f, col);
        }
        Ui.textC(c, "动效：透视=上下收拢 · 等底=满宽平铺", 233f, rows[3].bottom + 26f, 12f, Ui.FAINT);
        Ui.textC(c, "顶端空间条可避免列表遮挡表圈", 233f, rows[3].bottom + 48f, 12f, Ui.FAINT);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action != MotionEvent.ACTION_UP) return false;
        if (hitBack(x, y)) { act.pop(); return true; }
        for (int i = 0; i < 4; i++) {
            if (!rows[i].contains(x, y)) continue;
            switch (i) {
                case 0:
                    Ui.FLAT_FLOW = !Ui.FLAT_FLOW;
                    Prefs.flatFlow(act, Ui.FLAT_FLOW);
                    act.toast(Ui.FLAT_FLOW ? "等底信息流" : "圆弧透视");
                    break;
                case 1:
                    Ui.RING_TOP = !Ui.RING_TOP;
                    Prefs.ringTop(act, Ui.RING_TOP);
                    act.toast(Ui.RING_TOP ? "空间条固定在最上端" : "空间条画在表圈");
                    break;
                case 2:
                    Prefs.bgPlay(act, !Prefs.bgPlay(act));
                    break;
                case 3:
                    act.push(new ShapeScreen(act));
                    break;
            }
            invalidate();
            return true;
        }
        return false;
    }
}
