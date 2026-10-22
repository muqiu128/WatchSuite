package com.watchsuite;

import android.graphics.Canvas;
import android.graphics.RectF;

/** 页面基类：一套统一的内边距、返回按钮与标题栏。 */
public abstract class Screen {

    public static final float W = 466f, H = 466f, CX = 233f, CY = 233f;

    protected final MainActivity act;
    protected final RectF backRect = new RectF(50f, 48f, 102f, 100f);

    public Screen(MainActivity a) {
        this.act = a;
    }

    public void onEnter() {}

    /** 页面重新回到最上层（从子页面返回 / 应用切回前台）时调用，用来刷新数据。 */
    public void onResume() {}

    public void onLeave() {}

    public abstract void draw(Canvas c);

    /** @return true 表示已消费该次事件 */
    public boolean touch(int action, float x, float y) {
        return false;
    }

    /** 需要完整多点触控（缩放/平移）的页面返回 true，绕过边缘返回手势。 */
    public boolean rawTouch() {
        return false;
    }

    /** 原始事件入口（含多指）。仅 rawTouch() 为 true 的页面会收到。 */
    public boolean touchEvent(android.view.MotionEvent e) {
        return false;
    }

    /** 物理返回键；返回 true 表示自己处理了。 */
    public boolean back() {
        return false;
    }

    /**
     * 该点是否是「空白」（没有任何按钮/内容）。
     * 只有主界面会用到：空白处滑动 = 返回表盘。
     */
    public boolean blankAt(float x, float y) {
        return false;
    }

    protected boolean hitBack(float x, float y) {
        return backRect.contains(x, y);
    }

    /** 统一标题栏：左侧返回 + 标题 + 右侧可选文本按钮。 */
    protected void header(Canvas c, String title, String right) {
        Ui.circle(c, backRect.centerX(), backRect.centerY(), 25f, Ui.SURFACE);
        Ui.textC(c, "‹", backRect.centerX(), backRect.centerY() + 11f, 34f, Ui.TEXT);
        float titleMax = right == null ? 250f : 180f;
        Ui.textLBold(c, Ui.ellipsize(title, 24f, titleMax), 116f, 88f, 24f, Ui.TEXT);
        if (right != null) {
            Ui.textR(c, right, 402f, 86f, 20f, Ui.ACCENT);
        }
    }

    protected float contentTop() {
        return 118f;
    }

    protected void invalidate() {
        act.shell.invalidate();
    }
}
