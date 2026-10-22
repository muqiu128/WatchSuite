package com.watchsuite;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import com.watchsuite.core.HttpShare;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** 接收系统「分享」来的文件（QQ / 微信 / 浏览器 …）。 */
public class ShareReceiverActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ArrayList<Uri> uris = new ArrayList<>();
        if (IntentCompat.isSendMultiple(getIntent())) {
            ArrayList<Uri> list = getIntent().getParcelableArrayListExtra(IntentCompat.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else {
            Uri u = getIntent().getParcelableExtra(IntentCompat.EXTRA_STREAM);
            if (u != null) uris.add(u);
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "没有可接收的内容", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        final ArrayList<Uri> list = uris;
        new Thread(() -> {
            int ok = 0;
            for (Uri u : list) {
                if (copy(u)) ok++;
            }
            final int n = ok;
            runOnUiThread(() -> {
                Toast.makeText(this, n > 0 ? "已收到 " + n + " 个文件" : "接收失败",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        }).start();
    }

    private boolean copy(Uri u) {
        try {
            File dir = new File(HttpShare.DIR);
            dir.mkdirs();
            String name = displayName(u);
            File dst = new File(dir, name);
            int k = 2;
            while (dst.exists()) {
                int dot = name.lastIndexOf('.');
                String base = dot > 0 ? name.substring(0, dot) : name;
                String ext = dot > 0 ? name.substring(dot) : "";
                dst = new File(dir, base + "(" + k++ + ")" + ext);
            }
            try (InputStream in = getContentResolver().openInputStream(u);
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private String displayName(Uri u) {
        String name = null;
        if ("content".equals(u.getScheme())) {
            Cursor c = getContentResolver().query(u, null, null, null, null);
            if (c != null) {
                try {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0 && c.moveToFirst()) name = c.getString(i);
                } finally {
                    c.close();
                }
            }
        }
        if (name == null) {
            String p = u.getLastPathSegment();
            name = p == null ? String.valueOf(System.currentTimeMillis()) : p;
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
        }
        return name;
    }

    /** 兼容类：避免直接引用 Intent 常量名冲突。 */
    private static final class IntentCompat {
        static final String EXTRA_STREAM = "android.intent.extra.STREAM";

        static boolean isSendMultiple(android.content.Intent i) {
            return "android.intent.action.SEND_MULTIPLE".equals(i.getAction());
        }
    }
}
