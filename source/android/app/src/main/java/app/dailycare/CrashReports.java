package app.dailycare;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Last-resort crash capture: writes the stack trace to private storage and chains to the
 *  previous handler, so a crash still surfaces exactly as before. Diagnostics only — this
 *  class never prevents, retries, or hides a crash. File logic is pure (a directory in,
 *  files out) so it is unit-tested without an Android runtime. */
public final class CrashReports {
    static final String DIR_NAME = "crash-reports";
    static final int KEEP = 3;
    static final int MAX_BODY_CHARS = 4000;
    private static volatile boolean installed;
    private CrashReports() {}

    static File dir(File filesDir) { return new File(filesDir, DIR_NAME); }
    static File dir(Context c) { return dir(c.getFilesDir()); }

    /** Install the capturing handler once. Safe to call from an activity or a receiver. */
    public static synchronized void install(Context c) {
        if (c == null) return;
        installIn(dir(c));
    }

    /** Testable core of install(): capture into an explicit directory. */
    static synchronized void installIn(File directory) {
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { writeReport(directory, thread, error, System.currentTimeMillis()); }
            catch (Throwable ignored) { /* capture must never mask the crash */ }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            } else {
                throw new RuntimeException(error);
            }
        });
    }

    /** For tests: allow reinstalling after a previous install. */
    static synchronized void resetForTests() { installed = false; }

    /** Record a caught (non-fatal) failure the same way a crash is captured: write the
     *  stack trace to private storage so a later Settings → About report names it.
     *  Unlike the uncaught handler, this never rethrows: lifecycle entry points use
     *  it to survive an unexpected Throwable instead of crashing. Never throws. */
    public static void record(Context c, String where, Throwable error) {
        try {
            if (c == null || error == null) return;
            writeReport(dir(c), Thread.currentThread(), error, System.currentTimeMillis(), where);
        } catch (Throwable ignored) { /* recording must never throw */ }
    }

    /** Record a caught failure without a location label. Never throws. */
    public static void record(Context c, Throwable error) {
        record(c, null, error);
    }

    static void writeReport(File directory, Thread thread, Throwable error, long now) {
        writeReport(directory, thread, error, now, null);
    }

    static void writeReport(File directory, Thread thread, Throwable error, long now, String where) {
        try {
            if (!directory.exists()) directory.mkdirs();
            String name = "crash-" + now + ".txt";
            File out = new File(directory, name);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            try {
                pw.println("time=" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date(now)));
                pw.println("thread=" + (thread == null ? "?" : thread.getName()));
                if (where != null && !where.isEmpty()) pw.println("where=" + where);
                pw.println("error=" + error.getClass().getName() + ": " + error.getMessage());
                error.printStackTrace(pw);
            } finally { pw.close(); }
            try (FileWriter fw = new FileWriter(out)) { fw.write(sw.toString()); }
            prune(directory);
        } catch (Throwable ignored) { /* capture must never throw */ }
    }

    static void prune(File directory) {
        try {
            File[] files = directory.listFiles((d, n) -> n.startsWith("crash-") && n.endsWith(".txt"));
            if (files == null || files.length <= KEEP) return;
            Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
            for (int i = 0; i < files.length - KEEP; i++) files[i].delete();
        } catch (Throwable ignored) {}
    }

    static File latest(File directory) {
        try {
            File[] files = directory.listFiles((d, n) -> n.startsWith("crash-") && n.endsWith(".txt"));
            if (files == null || files.length == 0) return null;
            Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
            return files[files.length - 1];
        } catch (Throwable ignored) { return null; }
    }

    static String readText(File f, int maxChars) {
        if (f == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                char[] buf = new char[1024];
                int n;
                while ((n = r.read(buf)) != -1 && sb.length() < maxChars) sb.append(buf, 0, n);
            }
            return sb.length() > maxChars ? sb.substring(0, maxChars) : sb.toString();
        } catch (Throwable ignored) { return ""; }
    }

    static String firstLine(File f) {
        String text = readText(f, 500);
        int nl = text.indexOf('\n');
        String line = nl >= 0 ? text.substring(0, nl) : text;
        // The useful first line is the error= line; skip the time=/thread= headers.
        for (String part : text.split("\n")) {
            if (part.startsWith("error=")) return part.substring("error=".length());
        }
        return line;
    }

    /** JSON for the Settings/About block. Never throws. */
    static String describe(Context c) {
        try {
            File f = latest(dir(c));
            if (f == null) return "{\"exists\":false}";
            String body = readText(f, MAX_BODY_CHARS);
            String first = firstLine(f);
            return "{\"exists\":true,\"firstLine\":" + quote(first) + ",\"body\":" + quote(body) + "}";
        } catch (Throwable ignored) {
            return "{\"exists\":false}";
        }
    }

    static String quote(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
            }
        }
        return sb.append("\"").toString();
    }
}
