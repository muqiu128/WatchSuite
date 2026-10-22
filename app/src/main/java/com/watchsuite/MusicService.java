package com.watchsuite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/** 音乐前台服务：静态字段保存播放状态，同进程内直接读取。 */
public class MusicService extends Service {

    public static final String CH = "music";

    public static MediaPlayer mp;
    public static ArrayList<String> paths = new ArrayList<>();
    public static ArrayList<String> titles = new ArrayList<>();
    public static int index = -1;
    public static String err;

    public static void playFile(Context c, File f) {
        paths = new ArrayList<>();
        titles = new ArrayList<>();
        paths.add(f.getAbsolutePath());
        titles.add(f.getName());
        start(c, 0);
    }

    public static void start(Context c, int i) {
        if (i < 0 || i >= paths.size()) return;
        index = i;
        err = null;
        Intent in = new Intent(c, MusicService.class);
        in.setAction("PLAY");
        c.startService(in);
    }

    public static void toggle(Context c) {
        c.startService(new Intent(c, MusicService.class).setAction("TOGGLE"));
    }

    public static void next(Context c) {
        c.startService(new Intent(c, MusicService.class).setAction("NEXT"));
    }

    public static void prev(Context c) {
        c.startService(new Intent(c, MusicService.class).setAction("PREV"));
    }

    /** 拖动进度条跳转。 */
    public static void seekTo(Context c, int ms) {
        c.startService(new Intent(c, MusicService.class).setAction("SEEK").putExtra("ms", ms));
    }

    /** 完全停止：释放播放器 + 停掉前台服务。 */
    public static void stop(Context c) {
        if (mp != null) {
            try {
                mp.stop();
                mp.release();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
        index = -1;
        err = null;
        c.stopService(new Intent(c, MusicService.class));
    }

    public static boolean playing() {
        try {
            return mp != null && mp.isPlaying();
        } catch (Throwable t) {
            return false;
        }
    }

    public static int pos() {
        try {
            return mp != null ? mp.getCurrentPosition() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int dur() {
        try {
            return mp != null ? mp.getDuration() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String curTitle() {
        return index >= 0 && index < titles.size() ? titles.get(index) : "未在播放";
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        String act = intent != null ? intent.getAction() : "PLAY";
        if ("PLAY".equals(act)) {
            playCurrent();
            foreground();
        } else if ("TOGGLE".equals(act)) {
            if (mp != null) {
                if (mp.isPlaying()) mp.pause();
                else mp.start();
            } else {
                playCurrent();
            }
        } else if ("NEXT".equals(act)) {
            if (!paths.isEmpty()) {
                index = index + 1 >= paths.size() ? 0 : index + 1;
                playCurrent();
            }
        } else if ("PREV".equals(act)) {
            if (!paths.isEmpty()) {
                index = index - 1 < 0 ? paths.size() - 1 : index - 1;
                playCurrent();
            }
        } else if ("SEEK".equals(act)) {
            if (mp != null) {
                try {
                    mp.seekTo(intent != null ? intent.getIntExtra("ms", 0) : 0);
                } catch (Throwable ignored) {
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void playCurrent() {
        if (index < 0 || index >= paths.size()) return;
        // WMA 要先转码（数分钟级），统一切到后台线程，避免卡主线程
        final String src = paths.get(index);
        new Thread(() -> playSrc(src), "wma-play").start();
    }

    private void playSrc(String src) {
        if (index < 0 || index >= paths.size()) return;
        String playPath = src;
        if (com.watchsuite.core.Wma.isWma(src)) {
            err = "WMA 转换中…";
            runOnUiThread(this::foreground);
            File f = com.watchsuite.core.Wma.convert(this, src);
            if (f == null) {
                err = "WMA 转换失败";
                runOnUiThread(this::foreground);
                return;
            }
            playPath = f.getAbsolutePath();
        }
        final String play = playPath;
        runOnUiThread(() -> {
            if (index < 0 || index >= paths.size()) return;
            if (mp != null) {
                mp.stop();
                mp.release();
                mp = null;
            }
            try {
                mp = new MediaPlayer();
                mp.setDataSource(play);
                mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mp.setOnCompletionListener(m -> {
                    if (!paths.isEmpty()) {
                        index = index + 1 >= paths.size() ? 0 : index + 1;
                        playCurrent();
                    }
                });
                mp.setOnErrorListener((m, w, e) -> {
                    err = "解码失败";
                    foreground();
                    return true;
                });
                mp.prepare();
                mp.start();
                err = null;
            } catch (IOException | RuntimeException e) {
                err = "无法播放";
            }
            foreground();
        });
    }

    private void runOnUiThread(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private void foreground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "音乐播放",
                    NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setContentTitle("腕上管家 · 音乐")
                .setContentText(err != null ? err : curTitle())
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true);
        startForeground(11, b.build());
    }

    @Override
    public void onDestroy() {
        if (mp != null) {
            try {
                mp.stop();
            } catch (Throwable ignored) {
            }
            mp.release();
            mp = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}
