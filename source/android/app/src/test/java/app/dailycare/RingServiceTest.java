package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class RingServiceTest {
    @Test public void capIsTenMinutes(){
        assertEquals(10L*60L*1000L,RingService.CAP_MS);
    }
    @Test public void capElapsedOnlyAtOrAfterTenMinutes(){
        long started=1_000_000L;
        assertFalse(RingService.capElapsed(started,started+RingService.CAP_MS-1));
        assertTrue(RingService.capElapsed(started,started+RingService.CAP_MS));
        assertTrue(RingService.capElapsed(started,started+RingService.CAP_MS+60_000L));
        assertFalse(RingService.capElapsed(started,started));
    }
    @Test public void actionRequestCodesAreDistinctFromTheTapCode(){
        String key="r1|2026-10-29|08:00";
        int tap=Reminders.tapRequestCode(key);
        int record=Reminders.actionRequestCode(key,"record");
        int snooze=Reminders.actionRequestCode(key,"snooze");
        int gone=Reminders.actionRequestCode(key,"delete");
        assertNotEquals(tap,record);
        assertNotEquals(tap,snooze);
        assertNotEquals(record,snooze);
        assertNotEquals(tap,gone);
        assertEquals(tap,Reminders.tapRequestCode(key));
        assertEquals(record,Reminders.actionRequestCode(key,"record"));
        assertTrue(tap>=0&&record>=0&&snooze>=0&&gone>=0);
    }
}
