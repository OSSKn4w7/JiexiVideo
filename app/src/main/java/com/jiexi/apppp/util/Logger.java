package com.jiexi.apppp.util;

import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Logger {

    private static final int MAX_ENTRIES = 500;
    private static final List<String> sLogs = new ArrayList<String>();
    private static boolean sEnabled = true;
    private static final SimpleDateFormat sFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());

    public static synchronized void i(String tag, String msg) {
        if (!sEnabled) return;
        log("I", tag, msg);
    }

    public static synchronized void e(String tag, String msg) {
        log("E", tag, msg);
    }

    public static synchronized void e(String tag, String msg, Throwable t) {
        if (t != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.close();
            msg = msg + "\n" + sw.toString();
        }
        log("E", tag, msg);
    }

    private static void log(String level, String tag, String msg) {
        String time = sFormat.format(new Date());
        String entry = time + " " + level + "/" + tag + ": " + msg;
        sLogs.add(entry);
        if (sLogs.size() > MAX_ENTRIES) {
            sLogs.remove(0);
        }
    }

    public static synchronized List<String> getLogs() {
        return new ArrayList<String>(sLogs);
    }

    public static synchronized String getLogsAsString() {
        StringBuilder sb = new StringBuilder();
        for (String entry : sLogs) {
            sb.append(entry).append("\n");
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        sLogs.clear();
    }

    /**
     * Save logs to a file in Downloads/Jiexi/logs/
     */
    public static String saveToFile() {
        String timeStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "Jiexi/logs");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "log_" + timeStr + ".txt");
        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            fw.write("=== 解析App 日志 ===\n");
            fw.write("导出时间: " + sFormat.format(new Date()) + "\n\n");
            fw.write(getLogsAsString());
            fw.flush();
            return file.getAbsolutePath();
        } catch (IOException e) {
            return null;
        } finally {
            if (fw != null) {
                try { fw.close(); } catch (IOException ignored) {}
            }
        }
    }
}
