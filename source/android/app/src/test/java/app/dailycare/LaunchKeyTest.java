package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class LaunchKeyTest {
    @Test public void captureKeepsPendingKeyWhenIncomingIsEmpty(){
        assertEquals("k|2026-10-29|08:00",LaunchKey.capture("k|2026-10-29|08:00",""));
        assertEquals("k|2026-10-29|08:00",LaunchKey.capture("k|2026-10-29|08:00",null));
        assertEquals("",LaunchKey.capture("",""));
        assertEquals("",LaunchKey.capture(null,null));
    }
    @Test public void captureReplacesWithNonEmptyIncoming(){
        assertEquals("n|2026-10-29|09:00",LaunchKey.capture("k|2026-10-29|08:00","n|2026-10-29|09:00"));
        assertEquals("n|2026-10-29|09:00",LaunchKey.capture(null,"n|2026-10-29|09:00"));
    }
    @Test public void peekNeverConsumesAndIsNullSafe(){
        assertEquals("k|2026-10-29|08:00",LaunchKey.peek("k|2026-10-29|08:00"));
        assertEquals("k|2026-10-29|08:00",LaunchKey.peek("k|2026-10-29|08:00"));
        assertEquals("",LaunchKey.peek(null));
    }
    @Test public void handledClears(){
        assertEquals("",LaunchKey.handled());
    }
    @Test public void isNewLaunchOnlyForDifferentNonEmptyKeys(){
        assertTrue(LaunchKey.isNewLaunch("a|2026-10-29|08:00","b|2026-10-29|08:00"));
        assertTrue(LaunchKey.isNewLaunch("","b|2026-10-29|08:00"));
        assertTrue(LaunchKey.isNewLaunch(null,"b|2026-10-29|08:00"));
        assertFalse(LaunchKey.isNewLaunch("a|2026-10-29|08:00","a|2026-10-29|08:00"));
        assertFalse(LaunchKey.isNewLaunch("a|2026-10-29|08:00",""));
        assertFalse(LaunchKey.isNewLaunch("a|2026-10-29|08:00",null));
    }
    @Test public void matchesLaunchRequiresEqualNonEmptyKeys(){
        assertTrue(LaunchKey.matchesLaunch("a|2026-10-29|08:00","a|2026-10-29|08:00"));
        assertFalse(LaunchKey.matchesLaunch("a|2026-10-29|08:00","b|2026-10-29|08:00"));
        assertFalse(LaunchKey.matchesLaunch("",""));
        assertFalse(LaunchKey.matchesLaunch(null,"a|2026-10-29|08:00"));
        assertFalse(LaunchKey.matchesLaunch("a|2026-10-29|08:00",null));
    }
}
