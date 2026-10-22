package com.watchsuite.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * 应用占用结果的磁盘快照。
 *
 * <p>OPPO 等 ROM 的「读取应用列表」隐私拦截不只盯 getInstalledApplications，
 * 连批量 getApplicationInfo/getApplicationIcon 都会触发弹窗。
 * 因此快照连图标一起落盘（Base64 PNG），加载路径完全不碰 PackageManager；
 * 只有快照缺失/过期需要重新枚举时，才可能弹窗。
 */
public final class AppStore {

    private static final String FILE = "apps.snap";

    private AppStore() {}

    private static File f(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    public static void save(Context c, ArrayList<Apps.App> list) {
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(f(c)), "UTF-8"));
            w.write("#v2 " + System.currentTimeMillis() + "\n");
            for (Apps.App a : list) {
                if (a.pkg == null || a.pkg.isEmpty()) continue;
                w.write(a.pkg);
                w.write('\t');
                w.write(a.label == null ? a.pkg : a.label.replace('\t', ' ').replace('\n', ' '));
                w.write('\t');
                w.write(a.code + "\t" + a.data + "\t" + a.cache + "\t"
                        + a.size + "\t" + (a.system ? 1 : 0) + "\t" + (a.exact ? 1 : 0));
                w.write('\t');
                w.write(iconB64(a.icon));
                w.write('\n');
            }
            w.close();
        } catch (Throwable ignored) {
        }
    }

    private static String iconB64(Drawable d) {
        try {
            Bitmap bm = null;
            if (d instanceof BitmapDrawable) {
                bm = ((BitmapDrawable) d).getBitmap();
            } else if (d != null && d.getIntrinsicWidth() > 0) {
                bm = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(bm);
                d.setBounds(0, 0, 96, 96);
                d.draw(cv);
            }
            if (bm == null) return "";
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 80, bo);
            return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 快照的落盘时间（毫秒）；load 返回 null 时无意义。 */
    private static long savedAt;
    /** 快照是否带图标（#v2 起）。 */
    private static boolean withIcons;

    public static long savedAt() {
        return savedAt;
    }

    /** 快照里是否已包含图标；false 表示老格式，需要重新枚举一次升级。 */
    public static boolean hasIcons() {
        return withIcons;
    }

    /** 读快照；没有或读坏了返回 null。不调用任何 PackageManager 接口。 */
    public static ArrayList<Apps.App> load(Context c) {
        savedAt = 0;
        withIcons = false;
        File file = f(c);
        if (!file.exists()) return null;
        ArrayList<Apps.App> out = new ArrayList<>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), "UTF-8"));
            String line = r.readLine();
            if (line == null || !line.startsWith("#v")) {
                r.close();
                return null;
            }
            try {
                String[] hs = line.split(" ");
                if (hs.length >= 2) savedAt = Long.parseLong(hs[1]);
                withIcons = hs[0].endsWith("v2");
            } catch (Throwable ignored) {
            }
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 8) continue;
                try {
                    Apps.App a = new Apps.App();
                    a.pkg = p[0];
                    a.label = p[1];
                    a.code = Long.parseLong(p[2]);
                    a.data = Long.parseLong(p[3]);
                    a.cache = Long.parseLong(p[4]);
                    a.size = Long.parseLong(p[5]);
                    a.system = "1".equals(p[6]);
                    a.exact = "1".equals(p[7]);
                    a.icon = iconOf(p.length >= 9 ? p[8] : null);
                    out.add(a);
                } catch (Throwable ignored) {
                }
            }
            r.close();
        } catch (Throwable ignored) {
            return out.isEmpty() ? null : out;
        }
        return out.isEmpty() ? null : out;
    }

    private static Drawable iconOf(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] raw = Base64.decode(b64, Base64.NO_WRAP);
            Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            return bm == null ? null : new BitmapDrawable(bm);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
