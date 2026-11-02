package app.dailycare;

import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

/** One-at-a-time ringtone preview for the Settings tone list. A preview is a single
 *  play-through on the alarm stream — never a loop — and is always stopped before a new
 *  preview starts, before a real reminder rings, and when the screen is left. Every method
 *  is guarded: a preview failure is silence, never a crash. */
public final class TonePreview {
    private static Ringtone current;
    private static String currentUri;
    private TonePreview() {}

    /** Start previewing uri ("" means the default alarm tone). Returns false when nothing plays. */
    public static synchronized boolean preview(Context c, String uriString) {
        try {
            stop();
            if (c == null) return false;
            Uri uri;
            if (uriString == null || uriString.isEmpty()) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            } else {
                uri = Uri.parse(uriString);
            }
            if (uri == null) return false;
            Ringtone ringtone = RingtoneManager.getRingtone(c, uri);
            if (ringtone == null) return false;
            try { ringtone.setStreamType(AudioManager.STREAM_ALARM); } catch (Throwable ignored) {}
            ringtone.play();
            current = ringtone;
            currentUri = uriString == null ? "" : uriString;
            return true;
        } catch (Throwable ignored) {
            stopQuietly();
            return false;
        }
    }

    /** Stop any preview. Safe to call repeatedly and with nothing playing. */
    public static synchronized void stop() {
        try {
            if (current != null) {
                try { current.stop(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        } finally {
            current = null;
            currentUri = null;
        }
    }

    private static void stopQuietly() {
        try { if (current != null) current.stop(); } catch (Throwable ignored) {}
        current = null;
        currentUri = null;
    }

    static synchronized String playingUri() { return currentUri; }
}
