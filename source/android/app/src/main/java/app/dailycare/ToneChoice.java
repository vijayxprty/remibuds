package app.dailycare;

import org.json.*;

/** Pure reminder-sound choice. No Android types, so it is unit-tested with plain javac/java. */
public final class ToneChoice {
    private ToneChoice(){}
    /** "custom:<file>" when a copied custom tone is set, the chosen ringtone URI, or "default". */
    public static String resolve(JSONObject state){
        try{
            JSONObject s=state==null?null:state.optJSONObject("settings");
            if(s==null)return "default";
            String custom=s.optString("customTone","");
            if(!custom.isEmpty()&&custom.matches("[A-Za-z0-9._-]{1,80}")&&!custom.contains(".."))return "custom:"+custom;
            String uri=s.optString("toneUri","");
            if(!uri.isEmpty())return uri;
            return "default";
        }catch(Exception ignored){return "default";}
    }
}
