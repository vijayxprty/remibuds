package app.dailycare;

import android.content.Context;
import org.json.*;

/** The activity-owned alarm sound. Playback itself lives in the shared RingSound
 *  engine (also used by the foreground RingService), so the activity and the service
 *  can never sound together: starting here stops the service's sound first and takes
 *  ownership. This class keeps the 1.3–1.5 API: start/stop, the retry decision, and
 *  the custom-tone file name. */
public final class AlarmPlayer {
    static final String CUSTOM_FILE = RingSound.CUSTOM_FILE;
    private AlarmPlayer() {}

    /** Retry decision for a prepare-time failure. Pure so it is unit-tested without an
     *  Android runtime: retry once with the default tone, never when the failed source
     *  already was the default, and never twice. Delegates to the shared engine. */
    static boolean shouldRetry(boolean triedDefault, boolean alreadyRetried) {
        return RingSound.shouldRetry(triedDefault, alreadyRetried);
    }

    /** Loop the chosen alarm sound on the alarm stream with vibration until stopped.
     *  Stops the ring service first so the service's sound never overlaps this one. */
    public static synchronized void start(Context c, JSONObject state) {
        try { RingService.stop(c, true); } catch (Throwable ignored) {}
        TonePreview.stop();
        try { RingSound.play(c, state, RingSound.OWNER_ACTIVITY); } catch (Throwable ignored) {}
    }

    /** Stop any alarm sound, whoever owns it. Safe to call repeatedly. */
    public static synchronized void stop() {
        try { RingSound.stop(); } catch (Throwable ignored) {}
    }
}
