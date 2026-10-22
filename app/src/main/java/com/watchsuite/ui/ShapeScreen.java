package com.watchsuite.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Ui;

/** 首次启动：选择方表 / 圆表（决定转场面板形状），选择一次后记住。 */
public class ShapeScreen extends Screen {

    private final RectF roundBtn = new RectF(93f, 168f, 218f, 268f);
    private final RectF squareBtn = new RectF(248f, 168f, 373f, 268f);

    public ShapeScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean back() {
        return true;   // 必须选一个才能走
    }

    @Override
    public void draw(Canvas c) {
        header(c, "选择表盘形状", null);

        Ui.textC(c, "你的手表是方形还是圆形？", 233f, 142f, 17f, Ui.DIM);

        // 圆形按钮
        Ui.round(c, roundBtn.left, roundBtn.top, roundBtn.right, roundBtn.bottom, 18f, Ui.SURFACE);
        Ui.circle(c, roundBtn.centerX(), roundBtn.top + 42f, 28f, 0xFF2A2A2A);
        Ui.textC(c, "圆形表", roundBtn.centerX(), roundBtn.bottom - 22f, 17f, Ui.TEXT);

        // 方形按钮
        Ui.round(c, squareBtn.left, squareBtn.top, squareBtn.right, squareBtn.bottom, 18f, Ui.SURFACE);
        Ui.round(c, squareBtn.centerX() - 30f, squareBtn.top + 14f, squareBtn.centerX() + 30f,
                squareBtn.top + 70f, 14f, 0xFF2A2A2A);
        Ui.textC(c, "方形表", squareBtn.centerX(), squareBtn.bottom - 22f, 17f, Ui.TEXT);

        Ui.textC(c, "之后可在「关于」页重新选择", 233f, 308f, 13f, Ui.FAINT);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action != MotionEvent.ACTION_UP) return false;
        if (hitBack(x, y)) { act.pop(); return true; }
        if (roundBtn.contains(x, y)) { pick(false); return true; }
        if (squareBtn.contains(x, y)) { pick(true); return true; }
        return false;
    }

    private void pick(boolean square) {
        MainActivity.setSquare(act, square);
        act.toast(square ? "已选方形表" : "已选圆形表");
        act.pop();
    }
}
