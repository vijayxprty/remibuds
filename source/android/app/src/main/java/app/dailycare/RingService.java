package app.dailycare;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.*;

/** Foreground service that guarantees the alarm sound at the set time, even when the
 *  full-screen-intent permission is blocked and the activity never launches. Started
 *  synchronously from the reminder-fire path (Reminders.show, reached from the exact
 *  alarm's BroadcastReceiver — an alarm trigger is a documented exemption from the
 *  Android 12+ background foreground-service start restriction). The service owns the
 *  reminder notification while it rings: the same notification the activity path
 *  would post, with the same tap, full-screen intent and actions. Sound comes from
 *  the shared RingSound engine, so the service and the activity can never sound
 *  together. The service stops when the user acts, when the reminder is resolved, or
 *  after a 10-minute cap; on stopping it detaches the foreground state without
 *  removing the notification unless the stop says otherwise (the action/activity
 *  then owns the notification). Never throws out of a lifecycle entry point. */
public class RingService extends Service {
    static final String ACTION_START = "app.dailycare.RING_START";
    static final int NOTIF_ID = 1001;
    static final long CAP_MS = 10L * 60L * 1000L;

    private static volatile boolean running = false;
    private static volatile String currentKey = "";
    private static volatile boolean stopKeep = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable capRunnable = () -> {
        try { stopForeground(STOP_FOREGROUND_DETACH); } catch (Throwable ignored) {}
        try { stopSelf(); } catch (Throwable ignored) {}
    };

    /** Pure cap decision, unit-tested: the sound stops once the cap has elapsed. */
    static boolean capElapsed(long startedAt, long now) {
        return now - startedAt >= CAP_MS;
    }

    /** Start ringing for {@code entry}. Returns true when the reminder notification is
     *  now owned (by this service, or posted directly by the activity-owns-sound
     *  fallback); false only when nothing could be posted, in which case the caller
     *  falls back to a plain notification. Never throws. */
    static boolean start(Context c, JSONObject entry) {
        try {
            if (RingSound.isPlaying() && RingSound.owner() == RingSound.OWNER_ACTIVITY) {
                // The activity already owns the sound: post the notification directly
                // so the reminder is still visible, and do not start a second sound.
                Notification n = Reminders.buildNotification(c, entry, true);
                c.getSystemService(NotificationManager.class).notify(entry.optString("key", ""), 0, n);
                return true;
            }
            Intent i = new Intent(c, RingService.class).setAction(ACTION_START)
                    .putExtra("key", entry.optString("key", ""))
                    .putExtra("title", entry.optString("title", "Reminder"))
                    .putExtra("body", entry.optString("body", "Open to review your routine."))
                    .putExtra("at", entry.optLong("at", System.currentTimeMillis()));
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
            return true;
        } catch (Throwable e) {
            try { Reminders.recordRingServiceFailure(c, e); } catch (Throwable ignored) {}
            return false;
        }
    }

    /** Stop the service if it is running. The notification is detached but kept when
     *  {@code keep} is true, removed when false. No-op when the service is not
     *  running. Never throws. */
    static void stop(Context c, boolean keep) {
        try {
            if (!running) return;
            stopKeep = keep;
            c.stopService(new Intent(c, RingService.class));
        } catch (Throwable ignored) {}
    }

    /** Stop the service when it is ringing for {@code key} (resolved reminders).
     *  The notification is removed: the reminder no longer needs attention. */
    static void stopIfShowing(Context c, String key) {
        try {
            if (running && key != null && !key.isEmpty() && key.equals(currentKey)) stop(c, false);
        } catch (Throwable ignored) {}
    }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String key = intent == null ? null : intent.getStringExtra("key");
        if (key == null || key.isEmpty()) {
            try { stopSelf(); } catch (Throwable ignored) {}
            return START_NOT_STICKY;
        }
        currentKey = key;
        stopKeep = false;
        JSONObject entry = new JSONObject();
        try {
            entry.put("key", key)
                    .put("title", intent.getStringExtra("title"))
                    .put("body", intent.getStringExtra("body"))
                    .put("at", intent.getLongExtra("at", System.currentTimeMillis()));
        } catch (Throwable ignored) {}
        Notification notification;
        try {
            notification = Reminders.buildNotification(this, entry, true);
        } catch (Throwable e) {
            try { Reminders.recordRingServiceFailure(this, e); } catch (Throwable ignored) {}
            try { stopSelf(); } catch (Throwable ignored) {}
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, notification);
            }
        } catch (Throwable e) {
            // The foreground start itself was refused: post the notification directly
            // so the reminder is still visible, and record why the service failed.
            try { Reminders.recordRingServiceFailure(this, e); } catch (Throwable ignored) {}
            try { getSystemService(NotificationManager.class).notify(key, 0, notification); } catch (Throwable ignored) {}
            try { stopSelf(); } catch (Throwable ignored) {}
            return START_NOT_STICKY;
        }
        try {
            if (RingSound.shouldPlay(RingSound.OWNER_SERVICE, RingSound.owner(), RingSound.isPlaying())) {
                JSONObject state;
                try { state = Reminders.state(this); } catch (Throwable ignored) { state = new JSONObject(); }
                RingSound.play(this, state, RingSound.OWNER_SERVICE);
            }
        } catch (Throwable e) {
            try { Reminders.recordRingServiceFailure(this, e); } catch (Throwable ignored) {}
        }
        handler.removeCallbacks(capRunnable);
        handler.postDelayed(capRunnable, CAP_MS);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        try { handler.removeCallbacks(capRunnable); } catch (Throwable ignored) {}
        try { RingSound.stopIfOwned(RingSound.OWNER_SERVICE); } catch (Throwable ignored) {}
        try { stopForeground(stopKeep ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
        running = false;
        currentKey = "";
        stopKeep = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
