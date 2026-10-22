package com.watchsuite.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.watchsuite.MainActivity;
import com.watchsuite.Screen;
import com.watchsuite.Ui;
import com.watchsuite.core.Fs;
import com.watchsuite.core.HttpShare;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

/** 快传：局域网 HTTP 收发 + 系统分享接收入口说明。 */
public class TransferScreen extends Screen {

    private final RectF btn = new RectF(128f, 296f, 338f, 352f);

    public TransferScreen(MainActivity a) {
        super(a);
    }

    @Override
    public void onEnter() {
        HttpShare.ui = this::invalidate;
    }

    @Override
    public void onLeave() {
        HttpShare.ui = null;
    }

    private static String ip() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.getAddress().length == 4) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void draw(Canvas c) {
        header(c, "快传", null);

        boolean run = HttpShare.running();
        Ui.round(c, 60f, 122f, 406f, 276f, 22f, Ui.SURFACE);

        String ip = ip();
        int port = HttpShare.port();
        String url = run && ip != null
                ? String.format(Locale.US, "http://%s:%d", ip, port) : "—";

        Ui.textC(c, run ? "服务运行中" : "服务未启动", 233f, 158f, 20f,
                run ? Ui.OK : Ui.DIM);
        Ui.textC(c, url, 233f, 192f, 22f, Ui.ACCENT);

        Ui.textC(c, "手机连同一 WiFi，浏览器打开上面地址", 233f, 226f, 14f, Ui.DIM);
        Ui.textC(c, "即可互传文件；收件：Download/腕上快传", 233f, 248f, 14f, Ui.DIM);
        if (HttpShare.uploads > 0) {
            Ui.textC(c, "本次已收 " + HttpShare.uploads + " 个文件 · "
                    + Fs.human(HttpShare.uploadedBytes), 233f, 268f, 13f, Ui.OK);
        }

        Ui.round(c, btn, 28f, run ? Ui.SURFACE2 : Ui.ACCENT);
        Ui.textC(c, run ? "停止服务" : "启动服务", btn.centerX(), btn.centerY() + 8f, 20f,
                run ? Ui.TEXT : Ui.ON_ACCENT);

        Ui.textC(c, "提示：QQ / 微信里点「分享」也能发给腕上管家", 233f, 390f, 13f, Ui.FAINT);
    }

    @Override
    public boolean touch(int action, float x, float y) {
        if (action != MotionEvent.ACTION_UP) return false;
        if (hitBack(x, y)) {
            act.pop();
            return true;
        }
        if (btn.contains(x, y)) {
            if (HttpShare.running()) {
                HttpShare.stop();
                act.toast("服务已停止");
            } else {
                act.bg(() -> {
                    try {
                        HttpShare.start();
                        act.ui(() -> act.toast("服务已启动"));
                    } catch (Throwable t) {
                        act.ui(() -> act.toast("启动失败：端口被占用"));
                    }
                });
            }
            invalidate();
            return true;
        }
        return false;
    }
}
