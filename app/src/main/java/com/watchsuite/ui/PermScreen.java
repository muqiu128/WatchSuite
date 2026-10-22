package com.watchsuite.ui;

import android.graphics.Canvas;
import android.graphics.RectF;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Ui;
import com.watchsuite.core.Perm;

/** 首次进入时申请「所有文件访问」。 */
public class PermScreen extends Screen {

    private final RectF btn = new RectF(88f, 300f, 378f, 358f);
    private final RectF skip = new RectF(88f, 368f, 378f, 414f);

    public PermScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void draw(Canvas c) {
        Ui.textC(c, "需要文件权限", 233f, 128f, 26f, Ui.TEXT);
        Ui.textC(c, "腕上管家要读取存储里的", 233f, 176f, 18f, Ui.DIM);
        Ui.textC(c, "视频、音乐、图片和安装包，", 233f, 202f, 18f, Ui.DIM);
        Ui.textC(c, "请授予「所有文件访问」。", 233f, 228f, 18f, Ui.DIM);

        Ui.round(c, btn, 29f, Ui.ACCENT);
        Ui.textC(c, "去授权", btn.centerX(), btn.centerY() + 8f, 22f, Ui.ON_ACCENT);

        Ui.textC(c, "稍后再说", skip.centerX(), skip.centerY() + 6f, 18f, Ui.FAINT);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action != android.view.MotionEvent.ACTION_UP) return false;
        if (btn.contains(x, y)) {
            Perm.ask(act);
            return true;
        }
        if (skip.contains(x, y)) {
            act.pop();
            return true;
        }
        return false;
    }
}
