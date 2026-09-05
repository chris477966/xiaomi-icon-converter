package com.androidinternal.graphics;

/** Bit-equivalent ARGB channel helpers for JVM tests and Android runtime. */
public final class AospColorCompat {
    private AospColorCompat() {}
    public static int alpha(int color) { return (color >>> 24) & 0xff; }
    public static int red(int color) { return (color >>> 16) & 0xff; }
    public static int green(int color) { return (color >>> 8) & 0xff; }
    public static int blue(int color) { return color & 0xff; }
    public static int argb(int a, int r, int g, int b) { return ((a & 0xff) << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff); }
    public static int rgb(int r, int g, int b) { return argb(0xff, r, g, b); }
}
