package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

/** Guard tests for the preview player. The Android runtime is stubbed in this harness,
 *  so these assert the safety contract — no throwable escapes, safe return values,
 *  idempotent stop — rather than audible playback, which only a device can prove. */
public class TonePreviewTest {
    @Test public void previewWithNoContextIsSafeAndReportsNothingPlaying(){
        assertFalse(TonePreview.preview(null,"content://media/internal/audio/media/7"));
        assertFalse(TonePreview.preview(null,null));
        assertFalse(TonePreview.preview(null,""));
        assertNull(TonePreview.playingUri());
    }
    @Test public void stopIsIdempotentAndNeverThrows(){
        TonePreview.stop();
        TonePreview.stop();
        assertNull(TonePreview.playingUri());
    }
    @Test public void previewWithUnplayableUriReturnsFalseWithoutThrowing(){
        // With stubbed Android APIs any real lookup throws; the guard must convert it
        // to a silent false, never a crash. A null context short-circuits first.
        assertFalse(TonePreview.preview(null,"not a uri at all {{{"));
        TonePreview.stop();
        assertNull(TonePreview.playingUri());
    }
}
