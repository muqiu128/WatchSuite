package com.watchsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import com.watchsuite.core.Perm;
import com.watchsuite.core.Prefs;
import com.watchsuite.ui.HomeScreen;
import com.watchsuite.ui.PermScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    public static MainActivity I;

    /** 表盘形状：false=圆形（默认），true=方形（转场面板为圆角矩形）。 */
    public static boolean SQUARE = false;

    public static void setSquare(MainActivity a, boolean square) {
        SQUARE = square;
        a.getSharedPreferences("watchsuite", Context.MODE_PRIVATE)
                .edit().putBoolean("square", square).apply();
        if (a.shell != null) a.shell.invalidate();
    }

    public static void loadSquare(MainActivity a) {
        SQUARE = a.getSharedPreferences("watchsuite", Context.MODE_PRIVATE)
                .getBoolean("square", false);
    }

    public ShellView shell;

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private boolean askedPerm = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        I = this;
        loadSquare(this);
        boostRefreshRate();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 透明窗口：从启动就半透明（运行时转换会闪），拖出时圆盘外直接透出桌面。
        // 平时 ShellView 自己铺黑底，视觉与不透明完全一致。
        try { setTranslucent(true); } catch (Throwable ignored) {}
        getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        shell = new ShellView(this);
        setContentView(shell);
        // 读取界面偏好：列表动效 / 空间条位置（Ui 静态开关，绘制时直接判断）
        Ui.FLAT_FLOW = Prefs.flatFlow(this);
        Ui.RING_TOP = Prefs.ringTop(this);
        shell.push(new HomeScreen(this), false);
        hideSystemUi();
        // 上次已允许过「读取应用列表」时，尽早把授权写回 AppOps：
        // 有些 ROM 的询问弹窗只对当前进程有效，大退重进会再问一次
        bg(() -> com.watchsuite.core.AppListPerm.persist(this));
    }

    private void hideSystemUi() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** 帧率自适配：在保持当前分辨率的前提下，请求这块表支持的最高刷新率。 */
    private void boostRefreshRate() {
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            android.view.Display.Mode cur = d.getMode();
            android.view.Display.Mode best = cur;
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth()
                        && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) {
                    best = m;
                }
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            if (best.getRefreshRate() > cur.getRefreshRate() + 0.1f) {
                lp.preferredDisplayModeId = best.getModeId();
                getWindow().setAttributes(lp);
            } else {
                lp.preferredRefreshRate = best.getRefreshRate();
                getWindow().setAttributes(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (!Perm.has(this) && !askedPerm) {
            askedPerm = true;
            shell.push(new PermScreen(this), true);
            return;
        }
        if (!getSharedPreferences("watchsuite", Context.MODE_PRIVATE).contains("square")) {
            shell.push(new com.watchsuite.ui.ShapeScreen(this), true);
            return;
        }
        Screen t = shell.top();
        if (t != null) t.onResume();
    }

    public void bg(Runnable r) {
        pool.execute(r);
    }

    public void ui(Runnable r) {
        runOnUiThread(r);
    }

    public void push(Screen s) {
        shell.push(s, true);
    }

    public void pop() {
        shell.popTop();
    }

    public void toast(String s) {
        shell.toast(s);
    }

    @Override
    public void onBackPressed() {
        if (!shell.popTop()) super.onBackPressed();
    }

    /** 页面栈 + 转场动效 + 触摸分发 + 轻提示。 */
    public static class ShellView extends View {

        private static final float W = 466f, CX = 233f, CY = 233f;

        private final List<Screen> stack = new ArrayList<>();
        private final Handler h = new Handler(Looper.getMainLooper());

        private String toastMsg;
        private long toastAt;

        private float downX, downY;
        private boolean edgeSwipe;
        /** 边缘右滑时当前页的实时位移，0 表示没有手势。 */
        private float popDx;

        // 主界面空白处滑动返回表盘：整页跟手位移，拖够即滑出
        private boolean homeExitDrag;
        private float ex, ey;
        private ValueAnimator exitAnim;
        // 退出动画已结束、等待最后一帧全透明画面上屏再 finish 的过渡期
        private boolean exitHold;

        // 通用适配：UI 全部按 466×466 逻辑坐标绘制，整体缩放到实际分辨率
        private float k = 1f, offX = 0f, offY = 0f;

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            k = Math.min(w, h) / 466f;
            offX = (w - 466f * k) / 2f;
            offY = (h - 466f * k) / 2f;
        }

        // 转场：animFrom 是「旧页面」，圆形揭示动画的进度与圆心
        private Screen animFrom;
        private boolean animPush = true;
        private float animP = 1f;
        private boolean animating;
        private ValueAnimator anim;
        /** 转场圆心：进入时取按下点，默认屏幕中心。 */
        private float circX = CX, circY = CY;

        public ShellView(Context c) {
            super(c);
            // 整页常驻硬件层：每一帧都是完整合成后上屏，避免脏区/开关层造成的闪帧
            setLayerType(LAYER_TYPE_HARDWARE, null);
            // 提前建好转场离屏位图：首次点击进页面时不再触发分配（那一下会造成闪屏）
            circBm = android.graphics.Bitmap.createBitmap(466, 466, android.graphics.Bitmap.Config.ARGB_8888);
            circCv = new android.graphics.Canvas(circBm);
            circPaint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            circPaint.setShader(new android.graphics.BitmapShader(
                    circBm, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        }

        public void push(Screen s, boolean animate) {
            Screen prev = top();
            stack.add(s);
            s.onEnter();
            if (animate && prev != null) startTransition(prev, true);
            else {
                animating = false;
                animFrom = null;
                invalidate();
            }
        }

        public boolean popTop() {
            if (stack.size() <= 1) {
                if (!stack.isEmpty() && stack.get(0).back()) {
                    invalidate();
                    return true;
                }
                return false;
            }
            Screen gone = stack.remove(stack.size() - 1);
            gone.onLeave();
            Screen next = top();
            if (next != null) next.onResume();
            startTransition(gone, false);
            return true;
        }

        public Screen top() {
            return stack.isEmpty() ? null : stack.get(stack.size() - 1);
        }

        public void toast(String msg) {
            toastMsg = msg;
            toastAt = System.currentTimeMillis();
            invalidate();
            h.postDelayed(this::invalidate, 1900);
        }

        // ---------------- 转场 ----------------

        private void startTransition(final Screen from, final boolean isPush) {
            // 先把字段摘下来再 cancel：cancel 会同步回调 onAnimationEnd，
            // 若字段还指向旧动画，会被误认为「刚正常结束」而误触 done 逻辑
            if (anim != null) {
                Animator old = anim;
                anim = null;
                old.cancel();
            }
            animFrom = from;
            animPush = isPush;
            animP = 0f;
            animating = true;
            discStale = true;   // 转场起点重画一次快照，之后滑动过程冻结
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(isPush ? 300 : 280);
            anim.setInterpolator(new DecelerateInterpolator(1.7f));
            anim.addUpdateListener(a -> {
                animP = (float) a.getAnimatedValue();
                invalFast();
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    // 只处理「当前正在用的那个」动画结束；被新动画顶掉的旧动画直接忽略
                    if (a == anim && animating) {
                        animating = false;
                        animFrom = null;
                        invalidate();
                    }
                }
            });
            anim.start();
        }

        /** 单层绘制：先按屏幕像素平移，再以屏幕中心缩放。 */
        private final android.graphics.Paint layerBg = new android.graphics.Paint();

        private void drawLayer(Canvas c, Screen s, float dx, float scale) {
            if (s == null) return;
            if (dx == 0f && scale == 1f) {
                s.draw(c);
                return;
            }
            int save = c.save();
            c.translate(dx, 0f);
            c.scale(scale, scale, CX, CY);
            // 层底色：页面本身只画元素不铺底，转场时若不补一块不透明整屏矩形，
            // 上层没画到的像素会透出下层旧页面（残影）。四周多留 8px 防锯齿缝。
            layerBg.setColor(Ui.BG);
            c.drawRect(-8f, -8f, W + 8f, 474f, layerBg);
            s.draw(c);
            c.restoreToCount(save);
        }

        /** 圆心到四角的最远距离：圆形揭示需要铺满全屏的半径。 */
        private static float maxR(float cx, float cy) {
            float m = 0f;
            m = Math.max(m, (float) Math.hypot(cx, cy));
            m = Math.max(m, (float) Math.hypot(W - cx, cy));
            m = Math.max(m, (float) Math.hypot(cx, 466f - cy));
            m = Math.max(m, (float) Math.hypot(W - cx, 466f - cy));
            return m;
        }

        /**
         * 以「实心圆形面板」绘制一层页面：页面（含黑底）整体随圆盘平移 dx，
         * 像一块圆形卡片从侧面滑入/滑出，内容与黑底同步移动。
         */
        private android.graphics.Bitmap circBm;
        private android.graphics.Canvas circCv;
        private android.graphics.Paint circPaint;

        /**
         * 圆盘内容是否需要重画。转场/手势期间把子页面内容「快照」进离屏位图，
         * 滑动过程只平移位图：避免后台数据回调（索引刷新、缩略图解码完成）
         * 在滑动中途反复刷新内容，造成内容闪跳。动画帧本身不置脏。
         */
        private boolean discStale = true;

        @Override
        public void invalidate() {
            // 转场/手势期间圆盘内容冻结，避免中途内容闪跳
            if (!animating && popDx == 0f) discStale = true;
            super.invalidate();
        }

        /** 动画帧专用：只重绘合成结果，不重画圆盘快照。 */
        private void invalFast() {
            super.invalidate();
        }

        private void drawCircled(Canvas c, Screen s, float dx) {
            if (discStale) {
                discStale = false;
                circBm.eraseColor(Ui.BG);   // 黑底铺进面板，和元素一起滑过来
                s.draw(circCv);
            }
            int save = c.save();
            c.translate(dx, 0f);
            if (SQUARE) c.drawRoundRect(0f, 0f, 466f, 466f, 56f, 56f, circPaint);
            else c.drawCircle(CX, CY, CY, circPaint);
            c.restoreToCount(save);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            // 平时铺满不透明黑底；只有主界面拖出/滑出时留透明，让桌面透出来
            boolean seeThrough = stack.size() == 1 && (homeExitDrag || exitAnim != null || exitHold);
            if (!seeThrough) c.drawColor(Ui.BG);
            int sv = c.save();
            c.translate(offX, offY);
            c.scale(k, k);
            draw466(c);
            c.restoreToCount(sv);
        }

        /** 466×466 逻辑坐标下的全部绘制。 */
        private void draw466(Canvas c) {
            Screen cur = top();
            if (cur == null) return;

            if (stack.size() == 1 && (exitAnim != null || exitHold)) {
                // 主界面正在滑出（或等待上屏后退出）：整体平移离开
                drawCircled(c, cur, ex);
            } else if (stack.size() == 1 && homeExitDrag) {
                // 拖动中：主界面圆盘跟手平移，露出的是表盘（屏幕外）
                drawCircled(c, cur, ex);
            } else if (animating && animFrom != null) {
                float p = animP;
                if (animPush) {
                    // 进入：主页面原地不动，子页面是实心圆形面板从右滑入，黑底与内容一起移动
                    drawLayer(c, animFrom, 0f, 1f);
                    drawCircled(c, cur, W * (1f - p));
                } else {
                    // 返回：下一层原地不动，子页面圆盘向右滑出揭出下层
                    drawLayer(c, cur, 0f, 1f);
                    drawCircled(c, animFrom, W * p);
                }
            } else if (popDx > 0f && stack.size() > 1) {
                // 手指拖着返回：下一层原地露出，当前页作为圆盘跟手滑出
                drawLayer(c, stack.get(stack.size() - 2), 0f, 1f);
                drawCircled(c, cur, popDx);
            } else {
                cur.draw(c);
            }

            if (toastMsg != null) {
                long dt = System.currentTimeMillis() - toastAt;
                if (dt < 1900) {
                    float a = dt < 1600 ? 1f : (1900 - dt) / 300f;
                    int col = ((int) (a * 235) << 24) | 0x0A0E13;
                    float w = Ui.measure(toastMsg, 18f) + 44f;
                    if (w > 400f) w = 400f;
                    float l = 233f - w / 2f, t = 372f, r = 233f + w / 2f, b = t + 46f;
                    Ui.round(c, l, t, r, b, 23f, col);
                    Ui.textC(c, Ui.ellipsize(toastMsg, 18f, w - 24f), 233f, t + 30f, 18f, 0xFFEAF1F8);
                } else {
                    toastMsg = null;
                }
            }
        }

        // ---------------- 触摸 ----------------

        /** 把原始触摸事件从屏幕像素坐标变换到 466 逻辑坐标。 */
        private MotionEvent scaleTouch(MotionEvent e) {
            MotionEvent m = MotionEvent.obtain(e);
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

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            Screen s = top();
            if (s == null) return true;
            if (animating || anim != null && anim.isRunning()) return true;   // 转场中不响应，免得点错
            if (s.rawTouch()) {
                s.touchEvent(scaleTouch(e));
                return true;
            }
            float x = (e.getX() - offX) / k, y = (e.getY() - offY) / k;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    circX = x;
                    circY = y;
                    popDx = 0f;
                    edgeSwipe = x < 62f && stack.size() > 1;
                    homeExitDrag = stack.size() == 1 && !edgeSwipe && s.blankAt(x, y);
                    if (homeExitDrag) {
                        ex = 0f;
                        ey = 0f;
                        // 手势开始时快照一次圆盘内容，拖动过程只平移位图，
                        // 不逐帧重渲染界面（逐帧重画会闪）
                        discStale = true;
                        return true;
                    }
                    if (!edgeSwipe) s.touch(MotionEvent.ACTION_DOWN, x, y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (homeExitDrag) {
                        // 计算器同款：圆盘全程跟手，可按住来回拖；只平移快照，不重画内容
                        ex = x - downX;
                        ey = y - downY;
                        invalFast();
                        return true;
                    }                    if (edgeSwipe) {
                        float dx = x - downX;
                        if (dx > 8f) {
                            if (popDx == 0f) discStale = true;   // 手势开始时快照一次
                            popDx = dx;
                            invalFast();
                        }
                    } else {
                        s.touch(MotionEvent.ACTION_MOVE, x, y);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (homeExitDrag) {
                        homeExitDrag = false;
                        finishHomeExit();
                        return true;
                    }
                    if (edgeSwipe) {
                        if (popDx > W * 0.22f) {
                            finishPopGesture();
                        } else if (popDx > 8f) {
                            springBack();
                        } else if (Math.abs(x - downX) < 20f && Math.abs(y - downY) < 20f) {
                            s.touch(MotionEvent.ACTION_UP, x, y);
                        }
                        // popDx 的归零交给动画结束回调，避免中间闪一帧
                        return true;
                    }
                    s.touch(MotionEvent.ACTION_UP, x, y);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (homeExitDrag) {
                        homeExitDrag = false;
                        springHomeExit();
                        return true;
                    }
                    if (edgeSwipe) {
                        springBack();
                    } else {
                        s.touch(MotionEvent.ACTION_CANCEL, x, y);
                    }
                    return true;
            }
            return true;
        }

        /** 转场/手势期间把整页合成进硬件层，避免脏区重绘造成的闪帧。 */
        private void hwLayer(boolean on) {
            setLayerType(on ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, null);
        }

        /** 空白处拖动的距离阈值：超过就滑出回表盘，否则弹回。 */
        private void finishHomeExit() {
            float dist = (float) Math.hypot(ex, ey);
            if (dist < W * 0.5f) {
                springHomeExit();
                return;
            }
            // 计算器同款：跟手拖动 + 过半才退 + 未过半弹回原位。
            // 关键：过半松手时【不要】把 ex 弹回 0 —— 保持圆盘在当前拖到的
            // 位置，先重绘一帧并等它上屏（快速拖动会掉帧，若立刻 finish，
            // 系统动画会从旧的中间帧开始播，看起来像「闪回来一下再退出」），
            // 再 finish() 让系统关闭动画从这里继续滑出去。
            homeExitDrag = false;
            exitHold = true;   // 等待上屏期间仍走透明+圆盘平移路径，不能闪回普通绘制
            final MainActivity a = (MainActivity) getContext();
            invalFast();
            a.shell.postDelayed(a::finish, 32);
        }

        private void springHomeExit() {
            final float fx = ex;
            if (exitAnim != null) exitAnim.cancel();
            exitAnim = ValueAnimator.ofFloat(0f, 1f);
            exitAnim.setDuration(200);
            exitAnim.setInterpolator(new DecelerateInterpolator(1.6f));
            exitAnim.addUpdateListener(a -> {
                float p = 1f - (float) a.getAnimatedValue();
                ex = fx * p;
                ey = 0f;
                invalFast();
            });
            exitAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator an) {
                    if (an == exitAnim) {
                        exitAnim = null;
                        ex = 0f;
                        ey = 0f;
                        discStale = true;   // 弹回结束，恢复正常内容刷新
                        invalFast();
                    }
                }
            });
            exitAnim.start();
        }

        /** 手势滑够了：接着滑完剩下的 1/3，然后真正出栈。 */
        private void finishPopGesture() {
            final float from = popDx;
            // 目标点只前进不后退：拖得已超过 0.55W 时再收尾不能往回弹
            final float to = Math.max(from, W * 0.55f);
            if (to - from < 1f) {
                popDx = 0f;
                popTopNoAnim();
                return;
            }
            startFloatAnim(from, to, 170, () -> {
                popDx = 0f;
                popTopNoAnim();
            });
        }

        private void springBack() {
            final float from = popDx;
            startFloatAnim(from, 0f, 220, () -> popDx = 0f);
        }

        private void startFloatAnim(final float from, final float to, int dur, final Runnable done) {
            if (anim != null) {
                Animator old = anim;
                anim = null;
                old.cancel();
            }
            final ValueAnimator a = ValueAnimator.ofFloat(from, to);
            anim = a;
            a.setDuration(dur);
            a.setInterpolator(new DecelerateInterpolator(1.6f));
            a.addUpdateListener(v -> {
                popDx = (float) v.getAnimatedValue();
                invalFast();
            });
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator an) {
                    if (an == anim) done.run();
                    invalidate();
                }
            });
            a.start();
        }

        /** 手势返回专用：不再播放转场（画面已经跟着手指走完了）。 */
        private void popTopNoAnim() {
            if (stack.size() <= 1) return;
            Screen gone = stack.remove(stack.size() - 1);
            gone.onLeave();
            Screen next = top();
            if (next != null) next.onResume();
            animating = false;
            animFrom = null;
            invalidate();
        }
    }
}
