package com.watchsuite.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;

/** 打开 / 安装 / 分享本地文件。 */
public final class Openers {

    private Openers() {}

    public static void install(Context c, File f) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(LocalFileProvider.uriFor(f), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Throwable t) {
            try {
                Intent j = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                j.setData(LocalFileProvider.uriFor(f));
                j.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(j);
            } catch (Throwable t2) {
                Toast.makeText(c, "没有可用的安装器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static boolean view(Context c, File f) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(LocalFileProvider.uriFor(f), Fs.mime(f.getName()));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean share(Context c, File f) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(Fs.mime(f.getName()));
        i.putExtra(Intent.EXTRA_STREAM, LocalFileProvider.uriFor(f));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            c.startActivity(Intent.createChooser(i, "分享到"));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void openUri(Context c, Uri u, String mime) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(c, "无法打开该文件", Toast.LENGTH_SHORT).show();
        }
    }
}
