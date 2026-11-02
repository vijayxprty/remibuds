package app.dailycare;

import android.app.*;
import android.content.*;
import org.json.*;

/** Fires one gentle nudge, then rebuilds the chain. Stale, duplicate or very late
 *  intents never post: they only trigger a rebuild. Executes without an activity
 *  or WebView, and never throws. */
public class NudgeReceiver extends BroadcastReceiver {
    public void onReceive(Context c, Intent intent) {
        try { CrashReports.install(c); } catch (Throwable ignored) {}
        try {
            String key = intent == null ? null : intent.getStringExtra("key");
            long at = intent == null ? 0 : intent.getLongExtra("at", 0);
            long now = System.currentTimeMillis();
            SharedPreferences p = c.getSharedPreferences(Nudges.PREF, 0);
            JSONObject armed = Nudges.optObj(p.getString("armed", null));
            boolean current = armed != null && key != null && key.equals(armed.optString("key")) && armed.optLong("at") == at;
            if (current && !"head".equals(armed.optString("kind")) && now >= at - 1000 && now - at <= 3600000L) show(c, armed);
            Nudges.reschedule(c);
        } catch (Throwable ignored) {
            try { Nudges.reschedule(c); } catch (Throwable ignored2) {}
        }
    }
    /** One gentle notification at a time (tag "nudge" replaces the previous one).
     *  Tapping it simply opens the app; it never targets a reminder card. */
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
                    .setContentTitle(e.optString("title", "A gentle nudge"))
                    .setContentText(e.optString("body", ""))
                    .setStyle(new Notification.BigTextStyle().bigText(e.optString("body", "")))
                    .setContentIntent(tap).setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setShowWhen(true).setWhen(e.optLong("at", System.currentTimeMillis())).build();
            m.notify("nudge", 0, n);
        } catch (Throwable ignored) {}
    }
}
