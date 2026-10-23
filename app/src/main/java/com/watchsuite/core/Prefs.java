package com.watchsuite.core;

import android.content.Context;
import android.content.SharedPreferences;

/** 轻量设置存取（默认值与旧行为一致）。 */
public final class Prefs {

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("watchsuite", Context.MODE_PRIVATE);
    }

    /** 离开音乐页后是否继续在后台播放（默认关：离开即停，旧行为）。 */
    public static boolean bgPlay(Context c) {
        return p(c).getBoolean("bg_play", false);
    }

    public static void bgPlay(Context c, boolean v) {
        p(c).edit().putBoolean("bg_play", v).apply();
    }

    /** 列表动效：false=圆弧透视（上下变小），true=等底信息流（默认 false）。 */
    public static boolean flatFlow(Context c) {
        return p(c).getBoolean("flat_flow", false);
    }

    public static void flatFlow(Context c, boolean v) {
        p(c).edit().putBoolean("flat_flow", v).apply();
    }

    /** 空间进度条位置：false=表圈圆环，true=固定在最上端（默认 false）。 */
    public static boolean ringTop(Context c) {
        return p(c).getBoolean("ring_top", false);
    }

    public static void ringTop(Context c, boolean v) {
        p(c).edit().putBoolean("ring_top", v).apply();
    }
}
