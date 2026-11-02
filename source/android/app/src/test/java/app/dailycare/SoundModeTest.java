package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;
public class SoundModeTest{
 JSONObject state(String sound)throws Exception{return new JSONObject().put("settings",new JSONObject().put("enabled",true).put("soundMode",sound));}
 @Test public void ringModeFlag()throws Exception{
  assertTrue(Plan.ringMode(state("ring")));
  assertFalse(Plan.ringMode(state("notification")));
  assertFalse(Plan.ringMode(new JSONObject().put("settings",new JSONObject().put("enabled",true))));
  assertFalse(Plan.ringMode(new JSONObject()));
  assertFalse(Plan.ringMode(null));
 }
 @Test public void toneChoice()throws Exception{
  assertEquals("default",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject())));
  assertEquals("content://media/internal/audio/media/7",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("toneUri","content://media/internal/audio/media/7"))));
  assertEquals("custom:custom-alarm-tone",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("customTone","custom-alarm-tone"))));
  assertEquals("default",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("customTone","../evil"))));
  assertEquals("default",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("customTone","a/b"))));
  assertEquals("content://x",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("customTone","").put("toneUri","content://x"))));
  assertEquals("default",ToneChoice.resolve(null));
  assertEquals("default",ToneChoice.resolve(new JSONObject()));
 }
}
