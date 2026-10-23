package com.watchsuite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * 音乐前台服务：静态字段保存播放状态，同进程内直接读取。
 *
 * <p>带 MediaSession（framework 自带，无依赖）：系统由此把本应用识别为
 * 媒体应用，表盘/通知栏会出现媒体控件，随时暂停/切歌，不必进应用。
 */
public class MusicService extends Service {

    public static final String CH = "music";

    public static MediaPlayer mp;
    public static ArrayList<String> paths = new ArrayList<>();
    public static ArrayList<String> titles = new ArrayList<>();
    public static int index = -1;
    public static String err;

    private MediaSession session;

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
    public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "WatchSuiteMusic");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (mp != null && !mp.isPlaying()) {
                    mp.start();
                    foreground();
                }
            }

            @Override
            public void onPause() {
                if (mp != null && mp.isPlaying()) {
                    mp.pause();
                    foreground();
                }
            }

            @Override
            public void onSkipToNext() {
                nextSong();
            }

            @Override
            public void onSkipToPrevious() {
                prevSong();
            }

            @Override
            public void onSeekTo(long pos) {
                if (mp != null) {
                    try {
                        mp.seekTo((int) pos);
                        foreground();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
        session.setActive(true);
    }

    private void nextSong() {
        if (!paths.isEmpty()) {
            index = index + 1 >= paths.size() ? 0 : index + 1;
            playCurrent();
        }
    }

    private void prevSong() {
        if (!paths.isEmpty()) {
            index = index - 1 < 0 ? paths.size() - 1 : index - 1;
            playCurrent();
        }
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
            foreground();
        } else if ("NEXT".equals(act)) {
            nextSong();
        } else if ("PREV".equals(act)) {
            prevSong();
        } else if ("SEEK".equals(act)) {
            if (mp != null) {
                try {
                    mp.seekTo(intent != null ? intent.getIntExtra("ms", 0) : 0);
                } catch (Throwable ignored) {
                }
            }
            foreground();
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

    /** 同步 MediaSession 状态 + 刷新前台通知。 */
    private void foreground() {
        syncSession();
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
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT);
        // 通知里的三个实体按钮：上一首 / 暂停(播放) / 下一首
        // —— 即使表盘不显示媒体 chip，下拉通知栏也能一键暂停
        b.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_previous, "上一首", serviceIntent("PREV", 101)).build());
        b.addAction(new Notification.Action.Builder(
                playing() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing() ? "暂停" : "播放", serviceIntent("TOGGLE", 102)).build());
        b.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_next, "下一首", serviceIntent("NEXT", 103)).build());
        if (session != null) {
            // MediaStyle + session token：系统据此把通知识别为媒体控制，
            // 表盘上会出现媒体 chip（播放/暂停/切歌）
            b.setStyle(new Notification.MediaStyle()
                    .setMediaSession(session.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        startForeground(11, b.build());
    }

    private android.app.PendingIntent serviceIntent(String action, int code) {
        Intent in = new Intent(this, MusicService.class).setAction(action);
        int flags = Build.VERSION.SDK_INT >= 23
                ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        return android.app.PendingIntent.getService(this, code, in, flags);
    }

    private void syncSession() {
        if (session == null) return;
        int state;
        if (err != null) {
            // 转码是正常等待，不是错误
            state = err.contains("转换") ? PlaybackState.STATE_BUFFERING
                    : PlaybackState.STATE_ERROR;
        } else {
            state = playing() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        }
        PlaybackState.Builder pb = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO)
                .setState(state, pos(), playing() ? 1f : 0f);
        if (state == PlaybackState.STATE_ERROR) {
            pb.setErrorMessage(err);
        }
        session.setPlaybackState(pb.build());

        android.media.MediaMetadata.Builder mb = new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, curTitle())
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "腕上管家");
        long d = dur();
        if (d > 0) {
            mb.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, d);
        }
        session.setMetadata(mb.build());
        session.setActive(true);
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            try {
                session.setActive(false);
                session.release();
            } catch (Throwable ignored) {
            }
            session = null;
        }
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
