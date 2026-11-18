package io.whale.ncmlite;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 极简存储：设置走 SharedPreferences，记录走文件 */
public class Store {

    private static SharedPreferences sp;
    private static File dir;

    public static void init(Context c) {
        sp = c.getSharedPreferences("ncm_lite", Context.MODE_PRIVATE);
        dir = new File(c.getExternalFilesDir(null), "");
        if (!dir.exists()) dir.mkdirs();
    }

    // ---- 设置 ----
    public static long getLong(String k, long def) {
        return sp.getLong(k, def);
    }

    public static void putLong(Context c, String k, long v) {
        sp.edit().putLong(k, v).apply();
    }

    public static int getInt(String k, int def) {
        return sp.getInt(k, def);
    }

    public static void putInt(Context c, String k, int v) {
        sp.edit().putInt(k, v).apply();
    }

    public static float getFloat(String k, float def) {
        return sp.getFloat(k, def);
    }

    public static void putFloat(Context c, String k, float v) {
        sp.edit().putFloat(k, v).apply();
    }

    public static String getString(String k, String def) {
        return sp.getString(k, def);
    }

    public static void putString(Context c, String k, String v) {
        sp.edit().putString(k, v).apply();
    }

    // ---- 文件 ----
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static void log(Context c, String msg) {
        try {
            File f = new File(dir, "lite.log");
            FileWriter w = new FileWriter(f, true);
            w.write(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " | " + msg + "\n");
            w.close();
        } catch (Throwable ignored) {}
    }

    public static void appendRecord(Context c, String title, String artist,
                                    long duration, long played, String reason,
                                    long startMs, long endMs) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String line = "{"
                    + "\"start\":\"" + f.format(new Date(startMs)) + "\","
                    + "\"end\":\"" + f.format(new Date(endMs)) + "\","
                    + "\"title\":" + q(title) + ","
                    + "\"artist\":" + q(artist) + ","
                    + "\"duration_ms\":" + duration + ","
                    + "\"played_ms\":" + played + ","
                    + "\"played_sec\":" + (played / 1000) + ","
                    + "\"finished\":" + (duration > 0 && played >= duration - 3000)
                    + ",\"reason\":\"" + reason + "\""
                    + "}";
            FileWriter w = new FileWriter(new File(dir, "records.jsonl"), true);
            w.write(line);
            w.write("\n");
            w.close();
        } catch (Throwable ignored) {}
    }

    private static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
