package app.dailycare;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;

/** Separate from gentle nudges: a quiet, non-ringing notification before each of
 *  the user's own reminders. 2.0.4 spec, corrected from the user's own words:
 *  the lead is NOT fixed to one value. Every reminder ALWAYS gets three
 *  mandatory alerts — 1 day before, 1 hour before and 30 minutes before —
 *  PLUS one extra alert at a random time up to a day before (5 minutes, 45
 *  minutes, 1 hour 5 minutes, anything in between; the random lead is stable
 *  per reminder slot, so rebuilding the chain never moves it). Maximum lead
 *  is 1 day. One alarm is armed at a time and rebuilt on every state save,
 *  every reminder fire and every early-alert fire. It has its own on/off switch
 *  (settings.earlyAlerts, default on) and does NOT depend on phone reminders
 *  being on or on gentle nudges being on — only on notifications being allowed.
 *  It is deliberately NOT limited to nudge hours: an 8 AM reminder's 30-minute
 *  alert at 7:30 AM must still show. Never throws. */
public final class EarlyAlerts {
    static final String PREF = "early_alerts";
    static final String ACTION = "app.dailycare.EARLY";
    static final int[] MANDATORY_LEADS = {1440, 60, 30};

    static JSONObject optObj(String raw) {
        try { return raw == null ? null : new JSONObject(raw); }
        catch (Throwable ignored) { return null; }
    }
    static PendingIntent alarm(Context c, String key, long at) {
        Intent intent = new Intent(c, EarlyAlertReceiver.class).setAction(ACTION)
                .setData(Uri.parse("care://early/" + Uri.encode(key)))
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
            p.edit().remove("armed").apply();
        } catch (Throwable ignored) {}
    }
    /** On only when the Early alerts switch is on (default on) and notifications
     *  are actually allowed. Independent of phone-reminder and nudge switches. */
    static boolean enabled(Context c, JSONObject s) {
        try {
            if (!s.getJSONObject("settings").optBoolean("earlyAlerts", true)) return false;
            Reminders.earlyChannel(c);
            NotificationManager m = c.getSystemService(NotificationManager.class);
            NotificationChannel ch = m.getNotificationChannel(Reminders.CHANNEL_EARLY);
            return m.areNotificationsEnabled() && ch != null && ch.getImportance() > NotificationManager.IMPORTANCE_NONE;
        } catch (Throwable ignored) { return false; }
    }
    static String leadText(int lead) {
        if (lead >= 1440) return "1 day";
        if (lead >= 60) {
            int h = lead / 60, m = lead % 60;
            if (m == 0) return h + (h == 1 ? " hour" : " hours");
            return h + (h == 1 ? " hour " : " hours ") + m + " minutes";
        }
        return lead + " minutes";
    }
    /** One stable random extra lead per slot, 5..1440 minutes, never equal to a
     *  mandatory lead, so the same reminder always gets the same random extra
     *  across rebuilds (e.g. 5, 45 or 65 minutes before). */
    static int randomExtraLead(String key) {
        int h = (key.hashCode() * 31 + 17) & 0x7fffffff;
        int lead = 5 + (h % 1436);
        if (lead == 30 || lead == 60 || lead == 1440) lead = Math.min(1440, lead + 7);
        return lead;
    }
    /** Earliest future early alert across all routines, or null. One entry per
     *  day/time/title slot, so a shared reminder produces a single alert. */
    static JSONObject nextEarly(JSONObject s, long now) {
        try {
            JSONObject settings = s.getJSONObject("settings");
            Set<String> done = Plan.complete(s);
            JSONArray routines = s.getJSONArray("routines");
            Map<String, JSONObject> bySlot = new HashMap<>();
            for (int ri = 0; ri < routines.length(); ri++) {
                try {
                    JSONObject original = routines.getJSONObject(ri);
                    if (original.optBoolean("archived") || !original.optBoolean("remind", true)) continue;
                    boolean oneoff = "todo".equals(original.optString("type")) && !original.optString("dueDate", "").isEmpty();
                    int first = oneoff ? 0 : -1, last = oneoff ? 1 : 35;
                    for (int n = first; n < last; n++) {
                        Calendar cal = oneoff ? Plan.date(original.getString("dueDate")) : Calendar.getInstance();
                        if (!oneoff) { cal.setTimeInMillis(now); cal.add(Calendar.DATE, n); }
                        String d = Plan.day(cal);
                        JSONObject r = Plan.snapshot(original, d);
                        String startStr = r.optString("start", "");
                        if (!startStr.isEmpty() && startStr.compareTo(d) > 0) continue;
                        boolean allowed = oneoff;
                        JSONArray days = r.optJSONArray("days");
                        if (days != null) for (int i = 0; i < days.length(); i++)
                            if (days.getInt(i) == cal.get(Calendar.DAY_OF_WEEK) - 1) allowed = true;
                        if (!allowed) continue;
                        JSONArray times = r.optJSONArray("times");
                        if (times == null) continue;
                        for (int ti = 0; ti < times.length(); ti++) {
                            String time = times.getString(ti);
                            if (!time.matches("([01]\\d|2[0-3]):[0-5]\\d")) continue;
                            String key = original.getString("id") + "|" + d + "|" + time;
                            if (done.contains(key)) continue;
                            String[] hm = time.split(":");
                            Calendar at = (Calendar) cal.clone();
                            at.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                            at.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                            at.set(Calendar.SECOND, 0); at.set(Calendar.MILLISECOND, 0);
                            long due = at.getTimeInMillis();
                            if (due <= now) continue;
                            String title = r.optString("name", original.optString("name", "Reminder"));
                            JSONArray groups = s.optJSONArray("groups");
                            if (groups != null) for (int gi = 0; gi < groups.length(); gi++) {
                                JSONObject g = groups.getJSONObject(gi);
                                JSONArray members = g.optJSONArray("members");
                                if (members == null) continue;
                                for (int mi = 0; mi < members.length(); mi++) {
                                    JSONObject m = members.getJSONObject(mi);
                                    if (m.optString("routineId").equals(original.getString("id")) && m.optString("time").equals(time))
                                        title = g.optString("name", title);
                                }
                            }
                            int[] leads = new int[MANDATORY_LEADS.length + 1];
                            System.arraycopy(MANDATORY_LEADS, 0, leads, 0, MANDATORY_LEADS.length);
                            leads[MANDATORY_LEADS.length] = randomExtraLead(key);
                            for (int lead : leads) {
                                long fire = due - lead * 60000L;
                                if (fire <= now) continue;
                                String body = "Due at " + Plan.clock(time, settings.optString("timeFormat", "12")) + " · in " + leadText(lead) + ".";
                                JSONObject e = new JSONObject().put("kind", "early").put("key", "early|" + key + "|" + lead)
                                        .put("title", title).put("body", body).put("at", fire).put("due", due).put("lead", lead);
                                // One entry per slot AND lead: a shared reminder still
                                // produces a single alert per lead, not one per routine.
                                String slot = d + "|" + time + "|" + title + "|" + lead;
                                JSONObject prev = bySlot.get(slot);
                                if (prev == null || fire < prev.getLong("at")) bySlot.put(slot, e);
                            }
                        }
                    }
                } catch (Throwable ignored) { /* one bad routine never kills early alerts */ }
            }
            JSONObject best = null;
            for (JSONObject e : bySlot.values()) if (best == null || e.getLong("at") < best.getLong("at")) best = e;
            return best;
        } catch (Throwable ignored) { return null; }
    }
    static synchronized void reschedule(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREF, 0);
            JSONObject s = Reminders.state(c);
            JSONObject armed = optObj(p.getString("armed", null));
            long now = System.currentTimeMillis();
            if (!enabled(c, s)) { cancelArmed(c, armed); p.edit().remove("armed").apply(); return; }
            JSONObject next = nextEarly(s, now);
            if (armed != null && next != null && armed.optString("key").equals(next.optString("key"))
                    && armed.optLong("at") == next.optLong("at")) return;
            cancelArmed(c, armed);
            if (next == null) { p.edit().remove("armed").apply(); return; }
            AlarmManager m = c.getSystemService(AlarmManager.class);
            long fire = Math.max(next.optLong("at"), now + 250);
            if (Reminders.exact(c)) m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, alarm(c, next.optString("key"), next.optLong("at")));
            else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, alarm(c, next.optString("key"), next.optLong("at")));
            p.edit().putString("armed", next.toString()).apply();
        } catch (Throwable ignored) {}
    }
}
