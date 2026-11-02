package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class AlarmPlayerRetryTest {
    @Test public void retriesOnceWhenChosenToneFailsToPrepare(){
        assertTrue(AlarmPlayer.shouldRetry(false,false));
    }
    @Test public void neverRetriesWhenTheDefaultToneItselfFailed(){
        assertFalse(AlarmPlayer.shouldRetry(true,false));
    }
    @Test public void neverRetriesTwice(){
        assertFalse(AlarmPlayer.shouldRetry(false,true));
        assertFalse(AlarmPlayer.shouldRetry(true,true));
    }
}
