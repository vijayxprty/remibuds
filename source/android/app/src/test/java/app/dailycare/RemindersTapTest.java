package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class RemindersTapTest {
    @Test public void distinctKeysGetDistinctRequestCodes(){
        int a=Reminders.tapRequestCode("a|2026-10-29|08:00");
        int b=Reminders.tapRequestCode("b|2026-10-29|08:00");
        int c=Reminders.tapRequestCode("a|2026-10-29|20:00");
        assertNotEquals(a,b);
        assertNotEquals(a,c);
        assertNotEquals(b,c);
        assertTrue(a>=0&&b>=0&&c>=0);
    }
    @Test public void sameKeyAlwaysGetsTheSameRequestCode(){
        assertEquals(Reminders.tapRequestCode("a|2026-10-29|08:00"),Reminders.tapRequestCode("a|2026-10-29|08:00"));
    }
    @Test public void nullKeyMapsToZero(){
        assertEquals(0,Reminders.tapRequestCode(null));
    }
}
