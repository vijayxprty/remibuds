package app.dailycare;

import android.app.*;
import android.content.*;
import org.json.*;

/** Fires one quiet early alert, then rebuilds the chain. It never rings, never
 *  starts a foreground service and never targets a reminder card with actions —
 *  tapping it simply opens the app. Stale, duplicate or very late intents never
 *  post: they only trigger a rebuild. Executes without an activity or WebView. */
public class EarlyAlertReceiver extends BroadcastReceiver {
    public void onReceive(Context c, Intent intent) {
        try { CrashReports.install(c); } catch (Throwable ignored) {}
        try {
            String key = intent == null ? null : intent.getStringExtra("key");
            long at = intent == null ? 0 : intent.getLongExtra("at", 0);
            long now = System.currentTimeMillis();
            SharedPreferences p = c.getSharedPreferences(EarlyAlerts.PREF, 0);
            JSONObject armed = EarlyAlerts.optObj(p.getString("armed", null));
            boolean current = armed != null && key != null && key.equals(armed.optString("key")) && armed.optLong("at") == at;
            if (current && now >= at - 1000 && now - at <= 3600000L && armed.optLong("due", 0) > now) {
                // Skip if the routine was recorded, archived or removed since arming.
                boolean done = false, gone = true;
                try {
                    JSONObject s = Reminders.state(c);
                    String[] parts = key.split("\\|");
                    if (parts.length >= 4) { // 2.0.4: key carries the lead as a 5th part; parts[1..3] are still id|date|time
                        String slot = parts[1] + "|" + parts[2] + "|" + parts[3];
                        done = Plan.complete(s).contains(slot);
                        JSONArray routines = s.getJSONArray("routines");
                        for (int i = 0; i < routines.length(); i++) {
                            JSONObject r = routines.getJSONObject(i);
                            if (parts[1].equals(r.optString("id")) && !r.optBoolean("archived") && r.optBoolean("remind", true)) gone = false;
                        }
                    }
                } catch (Throwable ignored) {}
                if (!done && !gone) show(c, armed);
            }
            EarlyAlerts.reschedule(c);
        } catch (Throwable ignored) {
            try { EarlyAlerts.reschedule(c); } catch (Throwable ignored2) {}
        }
    }
    /** Quiet notification only: no full-screen intent, no ring, no actions.
     *  Each alert keeps its own tag so walk and water alerts do not overwrite
     *  each other. Tapping opens the app and clears this notification. */
    static void show(Context c, JSONObject e) {
        try {
            Reminders.earlyChannel(c);
            NotificationManager m = c.getSystemService(NotificationManager.class);
            if (!m.areNotificationsEnabled()) return;
            NotificationChannel ch = m.getNotificationChannel(Reminders.CHANNEL_EARLY);
            if (ch == null || ch.getImportance() == NotificationManager.IMPORTANCE_NONE) return;
            Intent open = new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(c, Reminders.tapRequestCode(e.optString("key")), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, Reminders.CHANNEL_EARLY)
                    .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(e.optString("title", "Reminder"))
                    .setContentText(e.optString("body", ""))
                    .setStyle(new Notification.BigTextStyle().bigText(e.optString("body", "")))
                    .setContentIntent(tap).setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setShowWhen(true).setWhen(e.optLong("at", System.currentTimeMillis())).build();
            m.notify(e.optString("key"), 0, n);
        } catch (Throwable ignored) {}
    }
}
