package com.watchsuite.core;

import android.app.AppOpsManager;
import android.content.Context;

/**
 * 「读取应用列表」隐私开关的读写。
 *
 * <p>部分国产 ROM（OPPO / 一加 / 真我 / 小米等）在进程第一次枚举已安装应用时弹窗询问，
 * 而该弹窗只对「当前进程」生效，大退重进必然再弹一次。若系统把该开关落到 AppOps
 * （GET_INSTALLED_APPS），且允许第三方应用自写，这里就把用户的「始终允许」持久化；
 * 系统不允许写时静默失败，退化为每次询问。
 */
public final class AppListPerm {

    private AppListPerm() {}

    /** 用户是否已经允许过（能在 AppOps 里读到 ALLOWED 返回 true）。 */
    public static boolean granted(Context c) {
        return check(c) == AppOpsManager.MODE_ALLOWED;
    }

    /** 把「允许读取应用列表」写进 AppOps；成功返回 true。 */
    public static boolean persist(Context c) {
        Integer code = opCode(c);
        if (code == null) return false;
        try {
            AppOpsManager am = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (am == null) return false;
            java.lang.reflect.Method m = AppOpsManager.class.getMethod(
                    "setMode", int.class, int.class, String.class, int.class);
            Object r = m.invoke(am, code, android.os.Process.myUid(),
                    c.getPackageName(), AppOpsManager.MODE_ALLOWED);
            return !(r instanceof Integer) || (Integer) r == AppOpsManager.MODE_ALLOWED
                    || check(c) == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int check(Context c) {
        Integer code = opCode(c);
        if (code == null) return AppOpsManager.MODE_ERRORED;
        try {
            AppOpsManager am = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (am == null) return AppOpsManager.MODE_ERRORED;
            java.lang.reflect.Method m = AppOpsManager.class.getMethod(
                    "checkOpNoThrow", int.class, int.class, String.class);
            Object r = m.invoke(am, code, android.os.Process.myUid(), c.getPackageName());
            return r instanceof Integer ? (Integer) r : AppOpsManager.MODE_ERRORED;
        } catch (Throwable ignored) {
            return AppOpsManager.MODE_ERRORED;
        }
    }

    /** GET_INSTALLED_APPS 的 op 编号：API 29+ 有常量，更早的版本没有就放弃。 */
    private static Integer opCode(Context c) {
        try {
            java.lang.reflect.Field f = AppOpsManager.class.getField("OPSTR_GET_INSTALLED_APPS");
            String opStr = (String) f.get(null);
            java.lang.reflect.Method m = AppOpsManager.class.getMethod(
                    "strOpToOp", String.class);
            Object r = m.invoke(null, opStr);
            return r instanceof Integer ? (Integer) r : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
