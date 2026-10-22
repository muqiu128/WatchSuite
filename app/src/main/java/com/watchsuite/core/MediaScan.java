package com.watchsuite.core;

/** 媒体条目模型（内容由 {@link Index} 统一扫描后填充）。 */
public final class MediaScan {

    public static final class Item {
        public String path;
        public String name;
        public long size;
        public long duration; // ms，图片为 0
        public long modified;
    }

    private MediaScan() {}

    public static String dur(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
