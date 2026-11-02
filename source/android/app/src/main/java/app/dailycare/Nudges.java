package app.dailycare;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;

/** Gentle nudges: heads-ups before the user's own reminders (a random lead time of
 *  30 minutes, 1 hour, 2 hours, 6 hours or 1 day, picked per reminder) plus rotating
 *  wellness texts, only in the user's chosen nudge hours (default 9 AM to 9 PM).
 *  Before-reminder alerts moved to EarlyAlerts in 1.9.2 and are no longer part
 *  of gentle nudges. One alarm is armed at a time and the
 *  chain is rebuilt on every state save, every reminder fire and every nudge fire,
 *  so the cadence survives restarts without a permanent process. Never throws. */
public final class Nudges {
    static final String PREF = "nudges";
    static final String ACTION = "app.dailycare.NUDGE";
    static final int[] LEADS = {30, 60, 120, 360, 1440};
    static final int WINDOW_START_HOUR = 9, WINDOW_END_HOUR = 21;

    /** 1.9.2: user-chosen nudge hours, stored as settings.nudges.window. */
    /** 2.0: generic window parser, default All day. Any "start-end" id in the
     *  dropdown (6-23, 7-23, 8-23, 9-23, 10-23, 11-23, 6-22, 7-22, 8-22, 9-21,
     *  7-21, 8-21) parses directly; unknown or missing values fall back to
     *  All day, never to a narrower window the user did not pick. */
    static int[] windowHours(JSONObject s) {
        try {
            String w = s.getJSONObject("settings").optJSONObject("nudges").optString("window", "all");
            if ("all".equals(w)) return new int[]{0, 24};
            String[] parts = w.split("-");
            if (parts.length == 2) {
                int start = Integer.parseInt(parts[0]), end = Integer.parseInt(parts[1]);
                if (start >= 0 && start < end && end <= 24) return new int[]{start, end};
            }
        } catch (Throwable ignored) {}
        return new int[]{0, 24};
    }
    static boolean inWindow(Calendar c) { return inWindow(c, WINDOW_START_HOUR, WINDOW_END_HOUR); }
    static boolean inWindow(Calendar c, int start, int end) {
        if (start == 0 && end == 24) return true;
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= start && h < end;
    }
    static JSONObject optObj(String raw) {
        try { return raw == null ? null : new JSONObject(raw); }
        catch (Throwable ignored) { return null; }
    }
    static PendingIntent alarm(Context c, String key, long at) {
        Intent intent = new Intent(c, NudgeReceiver.class).setAction(ACTION)
                .setData(Uri.parse("care://nudge/" + Uri.encode(key)))
                .putExtra("key", key).putExtra("at", at);
        return PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancelArmed(Context c, JSONObject armed) {
        try { if (armed != null) c.getSystemService(AlarmManager.class).cancel(alarm(c, armed.optString("key"), armed.optLong("at"))); }
        catch (Throwable ignored) {}
    }
    static void cancel(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREF, 0);
            cancelArmed(c, optObj(p.getString("armed", null)));
            p.edit().remove("armed").remove("pendingWellness").apply();
        } catch (Throwable ignored) {}
    }
    /** The feature is on only when phone reminders are on, the gentle-nudge toggle
     *  is on, and notifications are actually allowed. */
    static boolean enabled(Context c, JSONObject s) {
        try {
            JSONObject n = s.getJSONObject("settings").optJSONObject("nudges");
            if (n == null || !n.optBoolean("enabled")) return false;
            if (!s.getJSONObject("settings").optBoolean("enabled")) return false;
            Reminders.earlyChannel(c);
            NotificationManager m = c.getSystemService(NotificationManager.class);
            NotificationChannel ch = m.getNotificationChannel(Reminders.CHANNEL_EARLY);
            return m.areNotificationsEnabled() && ch != null && ch.getImportance() > NotificationManager.IMPORTANCE_NONE;
        } catch (Throwable ignored) { return false; }
    }
    static boolean textOn(JSONObject s, String textId) {
        try {
            JSONArray texts = s.getJSONObject("settings").optJSONObject("nudges").optJSONArray("texts");
            if (texts == null) return false;
            for (int i = 0; i < texts.length(); i++) {
                JSONObject t = texts.getJSONObject(i);
                if (textId.equals(t.optString("id"))) return t.optBoolean("on");
            }
            return false;
        } catch (Throwable ignored) { return false; }
    }
    /** Next wellness nudge: the next enabled text in weighted rotation (phone,
     *  office and job texts carry weight 3), 30–60 minutes out, inside the window. */
    static JSONObject nextWellness(JSONObject s, SharedPreferences p, long now) {
        try {
            JSONObject n = s.getJSONObject("settings").optJSONObject("nudges");
            if (n == null) return null;
            JSONArray texts = n.optJSONArray("texts");
            if (texts == null) return null;
            List<JSONObject> expanded = new ArrayList<>();
            for (int i = 0; i < texts.length(); i++) {
                JSONObject t = texts.getJSONObject(i);
                if (!t.optBoolean("on")) continue;
                int w = t.optInt("weight", 1) > 1 ? 3 : 1;
                for (int k = 0; k < w; k++) expanded.add(t);
            }
            if (expanded.isEmpty()) return null;
            int idx = p.getInt("wellnessIndex", 0);
            JSONObject t = expanded.get(idx % expanded.size());
            p.edit().putInt("wellnessIndex", idx + 1).apply();
            long minuteSlot = now / 60000L + idx;
            int[] win = windowHours(s);
            Calendar fire = Calendar.getInstance();
            fire.setTimeInMillis(now);
            if (fire.get(Calendar.HOUR_OF_DAY) < win[0]) {
                fire.set(Calendar.HOUR_OF_DAY, win[0]);
                fire.set(Calendar.MINUTE, (int) (minuteSlot % 30));
                fire.set(Calendar.SECOND, 0); fire.set(Calendar.MILLISECOND, 0);
            } else {
                int delayMin = 30 + (int) (minuteSlot % 31);
                fire.setTimeInMillis(now + delayMin * 60000L);
                if (!inWindow(fire, win[0], win[1])) {
                    fire.add(Calendar.DATE, 1);
                    fire.set(Calendar.HOUR_OF_DAY, win[0]);
                    fire.set(Calendar.MINUTE, (int) (minuteSlot % 30));
                    fire.set(Calendar.SECOND, 0); fire.set(Calendar.MILLISECOND, 0);
                }
            }
            if (fire.getTimeInMillis() <= now) return null;
            String emoji = t.optString("emoji", ""), text = t.optString("text", "");
            String title = emoji.isEmpty() ? "A gentle nudge" : emoji + " A gentle nudge";
            return new JSONObject().put("kind", "well").put("key", "nudge|well|" + t.optString("id", ""))
                    .put("textId", t.optString("id", "")).put("title", title).put("body", text)
                    .put("at", fire.getTimeInMillis());
        } catch (Throwable ignored) { return null; }
    }
    /** Rebuild the single armed gentle nudge (wellness text only, 1.9.2).
     *  A heads-up armed by an older build is cancelled here instead of firing
     *  again — EarlyAlerts owns before-reminder alerts now. A still-valid armed
     *  or pending wellness nudge is kept, so saving never resets the cadence. */
    static synchronized void reschedule(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREF, 0);
            JSONObject s = Reminders.state(c);
            JSONObject armed = optObj(p.getString("armed", null));
            JSONObject pendingWell = optObj(p.getString("pendingWellness", null));
            long now = System.currentTimeMillis();
            if (!enabled(c, s)) {
                cancelArmed(c, armed);
                p.edit().remove("armed").remove("pendingWellness").apply();
                return;
            }
            if (armed != null && "head".equals(armed.optString("kind"))) { cancelArmed(c, armed); armed = null; }
            JSONObject well = null;
            if (armed != null && "well".equals(armed.optString("kind")) && armed.optLong("at") > now
                    && textOn(s, armed.optString("textId"))) well = armed;
            if (well == null && pendingWell != null && pendingWell.optLong("at") > now
                    && textOn(s, pendingWell.optString("textId"))) well = pendingWell;
            if (well == null) well = nextWellness(s, p, now);
            if (armed != null && (well == null || !armed.optString("key").equals(well.optString("key"))
                    || armed.optLong("at") != well.optLong("at"))) cancelArmed(c, armed);
            if (well == null) { p.edit().remove("armed").remove("pendingWellness").apply(); return; }
            AlarmManager m = c.getSystemService(AlarmManager.class);
            long fire = Math.max(well.optLong("at"), now + 250);
            if (Reminders.exact(c)) m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, alarm(c, well.optString("key"), well.optLong("at")));
            else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, alarm(c, well.optString("key"), well.optLong("at")));
            p.edit().putString("armed", well.toString()).remove("pendingWellness").apply();
        } catch (Throwable ignored) {}
    }
}
