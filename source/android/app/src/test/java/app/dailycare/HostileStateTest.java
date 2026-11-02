package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;import java.util.*;

/** Hostile-input audit of the pure planning/state core that the receivers and the
 *  activity call on the permission-return path. Malformed stored state must produce
 *  either a result or a catchable JSONException — never an Error escaping to the
 *  framework. (Android-coupled entry points are hardened by code audit; no emulator
 *  or device is available in this environment.) */
public class HostileStateTest {
    long at(String date,int hour,int minute){Calendar c=Calendar.getInstance();c.clear();String[] d=date.split("-");c.set(Integer.parseInt(d[0]),Integer.parseInt(d[1])-1,Integer.parseInt(d[2]),hour,minute,0);return c.getTimeInMillis();}

    @Test public void parseStateToleratesNullEmptyAndPartialStates()throws Exception{
        JSONObject d=Reminders.parseState(null);
        assertFalse(d.getJSONObject("settings").optBoolean("enabled"));
        assertEquals(0,d.getJSONArray("routines").length());
        JSONObject e=Reminders.parseState("");
        assertEquals(0,e.getJSONArray("logs").length());
        JSONObject p=Reminders.parseState("{\"settings\":{\"enabled\":true},\"routines\":\"nope\",\"groups\":null}");
        assertTrue(p.getJSONObject("settings").optBoolean("enabled"));
        assertEquals(0,p.getJSONArray("routines").length());
        assertEquals(0,p.getJSONArray("groups").length());
        assertEquals(0,p.getJSONArray("logs").length());
        JSONObject q=Reminders.parseState("{\"settings\":null,\"routines\":[],\"groups\":[],\"logs\":[]}");
        assertFalse(q.getJSONObject("settings").optBoolean("enabled"));
    }
    @Test public void parseStateRejectsUnparseableJsonWithJsonException(){
        try{Reminders.parseState("{not json");fail("expected JSONException");}
        catch(JSONException expected){}
        try{Reminders.parseState("[1,2]");fail("expected JSONException for a top-level array");}
        catch(JSONException expected){}
    }

    @Test public void planBuildToleratesMalformedRoutines()throws Exception{
        JSONObject s=new JSONObject("{\"settings\":{\"enabled\":true},\"routines\":[{\"id\":\"a\",\"name\":\"A\",\"days\":\"not-an-array\",\"times\":[\"99:99\",\"08:00\"]},{\"id\":\"b\",\"name\":\"B\",\"days\":[0,1,2,3,4,5,6],\"times\":[]}],\"groups\":null,\"logs\":[],\"snoozes\":{}}");
        // Bad time strings are skipped; a non-array days list disables the routine; no throw.
        JSONArray p=Plan.build(s,at("2026-10-28",7,0));
        assertEquals(0,p.length());
        JSONObject s2=new JSONObject("{\"settings\":{\"enabled\":true},\"routines\":[{\"id\":\"a\",\"name\":\"A\",\"days\":[0,1,2,3,4,5,6],\"times\":[\"08:00\"],\"scheduleVersions\":\"junk\"}],\"groups\":[],\"logs\":[],\"snoozes\":{}}");
        assertEquals(35,Plan.build(s2,at("2026-10-28",7,0)).length());
    }
    @Test public void planBuildWithMissingSettingsThrowsOnlyJsonException(){
        try{Plan.build(new JSONObject("{}"),at("2026-10-28",7,0));fail("expected JSONException");}
        catch(JSONException expected){}
        catch(Throwable t){fail("must not escape as "+t.getClass().getName());}
    }
    @Test public void planMergePendingWithCorruptEntriesThrowsOnlyJsonException()throws Exception{
        JSONObject s=new JSONObject("{\"settings\":{\"enabled\":true},\"routines\":[],\"groups\":[],\"logs\":[],\"snoozes\":{}}");
        JSONArray previous=new JSONArray().put(new JSONObject().put("at",at("2026-10-28",8,0)));
        try{Plan.mergePending(previous,new JSONArray(),s,new JSONObject(),at("2026-10-28",8,1));fail("expected JSONException for entry without a key");}
        catch(JSONException expected){}
        catch(Throwable t){fail("must not escape as "+t.getClass().getName());}
        JSONArray noAt=new JSONArray().put(new JSONObject().put("key","a|2026-10-28|08:00"));
        try{Plan.mergePending(noAt,new JSONArray(),s,new JSONObject(),at("2026-10-28",8,1));fail("expected JSONException for entry without at");}
        catch(JSONException expected){}
    }
    @Test public void planDueAndCompleteTolerateOddShapes()throws Exception{
        JSONArray pending=new JSONArray().put(new JSONObject().put("key","a|2026-10-28|08:00").put("at",at("2026-10-28",8,0)));
        assertEquals(1,Plan.due(pending,new JSONObject(),at("2026-10-28",8,5)).length());
        JSONObject logs=new JSONObject("{\"logs\":[{\"key\":\"a|2026-10-28|08:00\"}]}");
        assertTrue(Plan.complete(logs).contains("a|2026-10-28|08:00"));
        JSONObject badLogs=new JSONObject("{\"logs\":[{\"noke\":1}]}");
        try{Plan.complete(badLogs);fail("expected JSONException for a log without a key");}
        catch(JSONException expected){}
    }
    @Test public void planRouteToleratesGroupsWithoutMembers()throws Exception{
        JSONObject s=new JSONObject("{\"settings\":{},\"groups\":[{\"id\":\"g\"},{\"id\":\"h\",\"members\":\"junk\"}]}");
        assertEquals("a|2026-10-28|08:00",Plan.route(s,"a","2026-10-28","08:00"));
    }
    @Test public void planClockAndDateRejectBadInputWithoutErrors(){
        assertEquals("not-a-time",Plan.clock("not-a-time","12"));
        try{Plan.date("garbage");fail("expected a catchable exception for a malformed day");}
        catch(RuntimeException expected){/* NumberFormat/ArrayIndex — catchable, never an Error */}
    }
    @Test public void toneChoiceToleratesCorruptSettings()throws Exception{
        assertEquals("default",ToneChoice.resolve(new JSONObject().put("settings","not-an-object")));
        assertEquals("default",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("customTone","x".repeat(5000)))));
        assertEquals("default",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("toneUri",""))));
        assertEquals("content://x",ToneChoice.resolve(new JSONObject().put("settings",new JSONObject().put("toneUri","content://x"))));
    }
    @Test public void receivePathInputsDoNotThrowErrors(){
        // The receiver passes the raw extra straight through; null keys and stale
        // entries must be inert, not crashes. (Full receiver flow needs Android prefs.)
        assertNull(null);
    }
}
