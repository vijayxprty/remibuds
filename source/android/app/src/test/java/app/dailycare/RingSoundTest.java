package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class RingSoundTest {
    @Test public void serviceStartsWhenNothingIsPlaying(){
        assertTrue(RingSound.shouldPlay(RingSound.OWNER_SERVICE,RingSound.OWNER_NONE,false));
        assertTrue(RingSound.shouldPlay(RingSound.OWNER_ACTIVITY,RingSound.OWNER_NONE,false));
    }
    @Test public void serviceNeverStartsWhileActivityOwnsTheSound(){
        assertFalse(RingSound.shouldPlay(RingSound.OWNER_SERVICE,RingSound.OWNER_ACTIVITY,true));
    }
    @Test public void serviceRestartsItsOwnSoundForANewerReminder(){
        assertTrue(RingSound.shouldPlay(RingSound.OWNER_SERVICE,RingSound.OWNER_SERVICE,true));
    }
    @Test public void activityAlwaysTakesOverFromTheService(){
        assertTrue(RingSound.shouldPlay(RingSound.OWNER_ACTIVITY,RingSound.OWNER_SERVICE,true));
        assertTrue(RingSound.shouldPlay(RingSound.OWNER_ACTIVITY,RingSound.OWNER_ACTIVITY,true));
    }
    @Test public void retryDecisionMatchesTheOldAlarmPlayerContract(){
        assertTrue(RingSound.shouldRetry(false,false));
        assertFalse(RingSound.shouldRetry(true,false));
        assertFalse(RingSound.shouldRetry(false,true));
        assertFalse(RingSound.shouldRetry(true,true));
        assertTrue(AlarmPlayer.shouldRetry(false,false));
        assertFalse(AlarmPlayer.shouldRetry(true,false));
    }
    @Test public void customToneFileNameIsShared(){
        assertEquals("custom-alarm-tone",RingSound.CUSTOM_FILE);
        assertEquals(RingSound.CUSTOM_FILE,AlarmPlayer.CUSTOM_FILE);
    }
}
