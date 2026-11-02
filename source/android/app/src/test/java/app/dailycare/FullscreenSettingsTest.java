package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class FullscreenSettingsTest {
    @Test public void api34PlusTargetsTheDedicatedFullScreenPage(){
        assertEquals("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",MainActivity.fullscreenSettingsAction(34));
        assertEquals("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",MainActivity.fullscreenSettingsAction(35));
        assertEquals("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",MainActivity.fullscreenSettingsAction(36));
    }
    @Test public void belowApi34FallsBackToAppInfo(){
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS",MainActivity.fullscreenSettingsAction(33));
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS",MainActivity.fullscreenSettingsAction(26));
    }
}
