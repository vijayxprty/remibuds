package app.dailycare;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import org.json.*;
import java.io.*;

/** The single looping alarm-sound engine, shared by the activity (AlarmPlayer) and the
 *  foreground ring service (RingService). There is exactly one MediaPlayer: starting a
 *  sound always stops whatever was playing first, so two sounds can never overlap.
 *  Ownership records WHO is ringing: the activity takes over from the service when the
 *  user opens the app, and the service never starts a sound while the activity owns one
 *  (see shouldPlay). Preparation is asynchronous: a slow or unreadable tone can never
 *  block the main thread. A tone that fails at setDataSource time falls back to the
 *  system default alarm tone immediately; a tone that fails at prepare time retries
 *  ONCE with a fresh player on the default tone. With no usable tone at all, ringing
 *  stays silent rather than crashing. */
public final class RingSound {
    static final String CUSTOM_FILE = "custom-alarm-tone";
    static final int OWNER_NONE = 0;
    static final int OWNER_ACTIVITY = 1;
    static final int OWNER_SERVICE = 2;

    private static MediaPlayer player;
    private static Vibrator vibrator;
    private static volatile int owner = OWNER_NONE;
    private RingSound() {}

    /** Retry decision for a prepare-time failure. Pure so it is unit-tested without an
     *  Android runtime: retry once with the default tone, never when the failed source
     *  already was the default, and never twice. */
    static boolean shouldRetry(boolean triedDefault, boolean alreadyRetried) {
        return !triedDefault && !alreadyRetried;
    }

    /** Whether {@code owner} may start (or restart) the sound given the current state.
     *  Pure so it is unit-tested: the activity always takes over; the service only
     *  starts when nothing is playing or when it already owns the sound (a newer
     *  reminder replacing the one it is ringing for). */
    static boolean shouldPlay(int owner, int currentOwner, boolean playing) {
        if (!playing) return true;
        if (owner == OWNER_ACTIVITY) return true;
        return currentOwner == OWNER_SERVICE;
    }

    static synchronized boolean isPlaying() { return player != null; }
    static synchronized int owner() { return owner; }

    /** Start looping the chosen tone as {@code owner}. Stops any previous sound first.
     *  Returns false without playing when the ownership guard refuses (service asked
     *  while the activity owns the sound). Never throws. */
    public static synchronized boolean play(Context c, JSONObject state, int newOwner) {
        if (!shouldPlay(newOwner, owner, player != null)) return false;
        stop();
        TonePreview.stop();
        play(c, state, newOwner, false);
        return true;
    }

    /** @param isRetry true when this is the single fallback attempt on the default tone. */
    private static synchronized void play(Context c, JSONObject state, int newOwner, boolean isRetry) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setLooping(true);
            String choice = isRetry ? "default" : ToneChoice.resolve(state);
            boolean triedDefault = false;
            boolean ready = false;
            if (choice.startsWith("custom:")) {
                File f = new File(c.getFilesDir(), choice.substring(7));
                if (f.exists()) { try { mp.setDataSource(f.getAbsolutePath()); ready = true; } catch (Throwable ignored) { ready = false; } }
            } else if (!"default".equals(choice)) {
                try { mp.setDataSource(c, Uri.parse(choice)); ready = true; } catch (Throwable ignored) { ready = false; }
            }
            if (!ready) {
                try {
                    Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    if (fallback == null) { mp.release(); return; }
                    mp.setDataSource(c, fallback); ready = true; triedDefault = true;
                } catch (Throwable ignored) { try { mp.release(); } catch (Throwable ignored2) {} return; }
            }
            final boolean failedOnDefault = triedDefault;
            final int playOwner = newOwner;
            mp.setOnPreparedListener(p -> { try { if (p == player) p.start(); } catch (Throwable ignored) {} });
            mp.setOnErrorListener((p, what, extra) -> {
                try { p.release(); } catch (Throwable ignored) {}
                if (p == player) player = null;
                if (shouldRetry(failedOnDefault, isRetry)) {
                    try { play(c, state, playOwner, true); } catch (Throwable ignored) {}
                }
                return true;
            });
            mp.prepareAsync();
            player = mp;
            owner = newOwner;
            try {
                Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
                long[] pattern = {0, 600, 400};
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0));
                else v.vibrate(pattern, 0);
                vibrator = v;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) { try { mp.release(); } catch (Throwable ignored2) {} stop(); }
    }

    /** Stop any sound, whoever owns it. Safe to call repeatedly. */
    public static synchronized void stop() {
        MediaPlayer mp = player; player = null; owner = OWNER_NONE;
        if (mp != null) { try { mp.stop(); } catch (Throwable ignored) {} try { mp.release(); } catch (Throwable ignored) {} }
        try { if (vibrator != null) vibrator.cancel(); } catch (Throwable ignored) {}
        vibrator = null;
    }

    /** Stop only when {@code expectedOwner} owns the sound. The service uses this in
     *  onDestroy so taking over from the activity can never silence the new sound. */
    public static synchronized void stopIfOwned(int expectedOwner) {
        if (owner == expectedOwner) stop();
    }
}
