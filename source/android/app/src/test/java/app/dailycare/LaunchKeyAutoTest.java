package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class LaunchKeyAutoTest {
    @Test public void captureAutoKeepsKnownActionsOnly(){
        assertEquals("record",LaunchKey.captureAuto("","record"));
        assertEquals("snooze",LaunchKey.captureAuto("","snooze"));
        assertEquals("snooze",LaunchKey.captureAuto("record","snooze"));
        assertEquals("record",LaunchKey.captureAuto("snooze","record"));
    }
    @Test public void captureAutoIgnoresAnythingElseAndKeepsPending(){
        assertEquals("record",LaunchKey.captureAuto("record",""));
        assertEquals("record",LaunchKey.captureAuto("record",null));
        assertEquals("record",LaunchKey.captureAuto("record","bogus"));
        assertEquals("",LaunchKey.captureAuto("","bogus"));
        assertEquals("",LaunchKey.captureAuto(null,null));
        assertEquals("",LaunchKey.captureAuto("",""));
    }
    @Test public void handledAutoClearsWithoutTouchingTheKey(){
        assertEquals("",LaunchKey.handledAuto());
        assertEquals("k|2026-10-29|08:00",LaunchKey.peek("k|2026-10-29|08:00"));
    }
}
