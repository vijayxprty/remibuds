package app.dailycare;

import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** Pure local-wall-clock planner. Occurrence identity never depends on an alert's firing time. */
public final class Plan {
    static String day(Calendar c) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime()); }
    static Calendar date(String day) {
        String[] bits = day.split("-");
        Calendar c = Calendar.getInstance(); c.clear();
        c.set(Integer.parseInt(bits[0]), Integer.parseInt(bits[1])-1, Integer.parseInt(bits[2]), 12, 0, 0);
        return c;
    }
    /** True when the stored settings ask reminders to ring as well as notify. */
    static boolean ringMode(JSONObject state){
        try{return "ring".equals(state.getJSONObject("settings").optString("soundMode","notification"));}
        catch(Exception ignored){return false;}
    }
    public static String clock(String time, String format) {
        if (!time.matches("\\d\\d:\\d\\d")) return time;
        if ("24".equals(format)) return time;
        int hour = Integer.parseInt(time.substring(0,2));
        return (hour % 12 == 0 ? 12 : hour % 12) + time.substring(2) + (hour >= 12 ? " PM" : " AM");
    }
    static JSONObject snapshot(JSONObject r, String day) throws JSONException {
        if (day.compareTo(r.optString("scheduleFrom", "0000-01-01")) >= 0) return r;
        JSONArray versions=r.optJSONArray("scheduleVersions"); JSONObject chosen=r; String from="";
        if (versions!=null) for(int i=0;i<versions.length();i++) {
            JSONObject v=versions.getJSONObject(i); String f=v.optString("from", "0000-01-01");
            if(f.compareTo(day)<=0 && f.compareTo(from)>=0) { chosen=v.getJSONObject("snapshot"); from=f; }
        }
        return chosen;
    }
    static Set<String> complete(JSONObject s) throws JSONException {
        Set<String> done = new HashSet<>(); JSONArray logs=s.optJSONArray("logs");
        if(logs!=null) for(int i=0;i<logs.length();i++) { JSONObject l=logs.getJSONObject(i); if(l.optString("undone", "").isEmpty()) done.add(l.getString("key")); }
        return done;
    }
    /** Pending reminders were authorized before their due time. Never manufacture a backlog on enable. */
    public static JSONArray due(JSONArray pending, JSONObject ledger, long now) throws JSONException {
        JSONArray out=new JSONArray();
        for(int i=0;i<pending.length();i++) {
            JSONObject entry=pending.getJSONObject(i);long at=entry.getLong("at");String token=entry.getString("key")+"|"+at;
            if(at<=now && now-at<=86400000L && !ledger.has(token))out.put(entry);
        }
        return out;
    }
    static String body(String type,String time,String format,boolean grouped) {
        if(grouped)return "Group reminder · "+clock(time,format)+". Open to review each member.";
        String label="todo".equals(type)?"To-do":"water".equals(type)?"Water":"meal".equals(type)?"Meal":"walk".equals(type)?"Walk":"outing".equals(type)?"Plan":"custom".equals(type)?"Activity":"cream".equals(type)||"gel".equals(type)||"patch".equals(type)?"Prescribed care":"Medicine";
        return label+("todo".equals(type)?" · due ":" · scheduled ")+clock(time,format)+". Open to review and record what you did.";
    }
    public static JSONArray afterPermissionTransition(JSONArray pending,boolean wasAllowed,boolean allowed,long now)throws JSONException {
        if(wasAllowed||!allowed)return pending;
        JSONArray future=new JSONArray();for(int i=0;i<pending.length();i++)if(pending.getJSONObject(i).getLong("at")>now)future.put(pending.getJSONObject(i));return future;
    }
    static String signature(JSONObject r,String time) {
        return r.optString("type")+"|"+time+"|"+r.optString("dueMode","at")+"|"+r.optInt("beforeMinutes",15)+"|"+r.optInt("repeatMinutes",0);
    }
    static String route(JSONObject s,String routineId,String day,String time)throws JSONException {
        JSONArray groups=s.optJSONArray("groups");
        if(groups!=null)for(int i=0;i<groups.length();i++){JSONObject g=groups.getJSONObject(i);JSONArray members=g.optJSONArray("members");if(members!=null)for(int j=0;j<members.length();j++){JSONObject m=members.getJSONObject(j);if(routineId.equals(m.optString("routineId"))&&time.equals(m.optString("time")))return g.getString("id")+"|"+day+"|"+time;}}
        return routineId+"|"+day+"|"+time;
    }
    /** Preserve authorized but Android-delayed alerts during refresh, unless explicitly made obsolete. */
    public static JSONArray mergePending(JSONArray previous,JSONArray future,JSONObject s,JSONObject ledger,long now)throws Exception {
        JSONObject settings=s.getJSONObject("settings");JSONArray out=new JSONArray();
        if(!settings.optBoolean("enabled")||(settings.has("zone")&&!TimeZone.getTimeZone(settings.getString("zone")).hasSameRules(TimeZone.getDefault())))return out;
        Set<String> done=complete(s),seen=new HashSet<>();List<JSONObject> entries=new ArrayList<>();
        for(int i=0;i<future.length();i++){JSONObject e=future.getJSONObject(i);String token=e.getString("key")+"|"+e.getLong("at");if(!ledger.has(token)&&seen.add(token))entries.add(e);}
        JSONArray routines=s.getJSONArray("routines");JSONObject snoozes=s.optJSONObject("snoozes");
        for(int i=0;i<previous.length();i++) {
            JSONObject e=previous.getJSONObject(i);long at=e.getLong("at");String token=e.getString("key")+"|"+at;
            if(at>now||now-at>86400000L||ledger.has(token)||seen.contains(token))continue;
            JSONArray members=e.optJSONArray("occurrences"),valid=new JSONArray();if(members==null)continue;String refreshedTitle=e.optString("title"),refreshedBody=e.optString("body");
            for(int j=0;j<members.length();j++) {
                String key=members.getString(j);if(done.contains(key))continue;String[] parts=key.split("\\|");if(parts.length!=3)continue;
                JSONObject current=null;for(int k=0;k<routines.length();k++)if(routines.getJSONObject(k).optString("id").equals(parts[0]))current=routines.getJSONObject(k);
                if(current==null||current.optBoolean("archived")||!current.optBoolean("remind",true))continue;
                JSONObject r=snapshot(current,parts[1]);JSONArray times=r.optJSONArray("times");boolean timeValid=false;
                if(times!=null)for(int k=0;k<times.length();k++)if(times.getString(k).equals(parts[2]))timeValid=true;if(!timeValid)continue;
                boolean todo="todo".equals(r.optString("type"));
                if(todo){JSONArray currentTimes=current.optJSONArray("times");boolean currentTime=false;if(currentTimes!=null)for(int k=0;k<currentTimes.length();k++)if(currentTimes.getString(k).equals(parts[2]))currentTime=true;if(!"todo".equals(current.optString("type"))||!currentTime||(!current.optString("dueDate","").isEmpty()&&!parts[1].equals(current.optString("dueDate"))))continue;}
                Calendar day=date(parts[1]);boolean allowed=todo&&!current.optString("dueDate","").isEmpty();JSONArray days=r.optJSONArray("days");if(days!=null)for(int k=0;k<days.length();k++)if(days.getInt(k)==day.get(Calendar.DAY_OF_WEEK)-1)allowed=true;
                if(!allowed||(!r.optString("start","").isEmpty()&&r.optString("start").compareTo(parts[1])>0))continue;
                if(snoozes!=null&&snoozes.optLong(key,0)>at)continue;
                if(e.has("signatures")&&!e.getJSONObject("signatures").optString(key).equals(signature(r,parts[2])))continue;
                if(!e.getString("key").equals(route(s,parts[0],parts[1],parts[2])))continue;
                if(valid.length()==0){boolean grouped=!e.getString("key").equals(key);refreshedTitle=r.optString("name",current.optString("name","Reminder"));if(grouped){JSONArray groups=s.optJSONArray("groups");if(groups!=null)for(int gi=0;gi<groups.length();gi++){JSONObject g=groups.getJSONObject(gi);if(e.getString("key").startsWith(g.optString("id")+"|"))refreshedTitle=g.optString("name",refreshedTitle);}}refreshedBody=body(r.optString("type","custom"),parts[2],settings.optString("timeFormat","12"),grouped);}
                valid.put(key);
            }
            if(valid.length()>0){JSONObject keep=new JSONObject(e.toString()).put("occurrences",valid).put("title",refreshedTitle).put("body",refreshedBody);entries.add(keep);seen.add(token);}
        }
        entries.sort((a,b)->Long.compare(a.optLong("at"),b.optLong("at")));for(JSONObject e:entries)out.put(e);return out;
    }
    public static JSONArray build(JSONObject s, long now) throws Exception {
        JSONObject settings=s.getJSONObject("settings"); JSONArray out=new JSONArray();
        if(!settings.optBoolean("enabled") || (settings.has("zone") && !TimeZone.getTimeZone(settings.getString("zone")).hasSameRules(TimeZone.getDefault()))) return out;
        JSONArray routines=s.getJSONArray("routines"), groups=s.optJSONArray("groups");
        JSONObject snoozes=s.optJSONObject("snoozes"); Set<String> done=complete(s), visited=new HashSet<>();
        Map<String,JSONObject> grouped=new HashMap<>(); List<JSONObject> entries=new ArrayList<>();
        for(int ri=0;ri<routines.length();ri++) {
            JSONObject original=routines.getJSONObject(ri);
            if(original.optBoolean("archived") || !original.optBoolean("remind",true)) continue;
            boolean oneoff="todo".equals(original.optString("type")) && !original.optString("dueDate","").isEmpty();
            int first=oneoff?0:-1, last=oneoff?1:35;
            for(int n=first;n<last;n++) {
                Calendar cal=oneoff?date(original.getString("dueDate")):Calendar.getInstance();
                if(!oneoff) { cal.setTimeInMillis(now); cal.add(Calendar.DATE,n); }
                String d=day(cal); JSONObject r=snapshot(original,d);
                String start=r.optString("start", ""); if(!start.isEmpty() && start.compareTo(d)>0) continue;
                boolean allowed=oneoff; JSONArray days=r.optJSONArray("days");
                if(days!=null) for(int i=0;i<days.length();i++) if(days.getInt(i)==cal.get(Calendar.DAY_OF_WEEK)-1) allowed=true;
                if(!allowed) continue;
                JSONArray times=r.optJSONArray("times"); if(times==null) continue;
                for(int ti=0;ti<times.length();ti++) {
                    String time=times.getString(ti); if(!time.matches("([01]\\d|2[0-3]):[0-5]\\d")) continue;
                    String key=original.getString("id")+"|"+d+"|"+time;
                    if(done.contains(key) || !visited.add(key)) continue;
                    boolean todo="todo".equals(r.optString("type"));
                    if(todo){JSONArray liveTimes=original.optJSONArray("times");boolean live=false;if(liveTimes!=null)for(int lt=0;lt<liveTimes.length();lt++)if(time.equals(liveTimes.getString(lt)))live=true;if(!"todo".equals(original.optString("type"))||!live)continue;}
                    String[] hm=time.split(":"); Calendar at=(Calendar)cal.clone();
                    at.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hm[0])); at.set(Calendar.MINUTE,Integer.parseInt(hm[1])); at.set(Calendar.SECOND,0); at.set(Calendar.MILLISECOND,0);
                    long due=at.getTimeInMillis(); int before=todo&&"before".equals(r.optString("dueMode"))?Math.max(0,Math.min(1440,r.optInt("beforeMinutes",15))):0;
                    long epoch=due-before*60000L; boolean snoozed=snoozes!=null&&snoozes.has(key);
                    int repeat=todo?Math.max(0,Math.min(1440,r.optInt("repeatMinutes",0))):0;
                    if(snoozed) epoch=snoozes.getLong(key);
                    if(epoch<=now) {
                        if(repeat==0 || (n<0&&!oneoff)) continue;
                        long interval=repeat*60000L; epoch+=((now-epoch)/interval+1)*interval;
                    }
                    if(!oneoff && day(at).compareTo(d)!=0) continue;
                    if(todo&&!oneoff&&repeat>0) { Calendar fire=(Calendar)cal.clone();fire.setTimeInMillis(epoch);if(!day(fire).equals(d))continue; }
                    if(n<0&&!oneoff&&!snoozed)continue;
                    String title=r.optString("name",original.optString("name","Reminder")), notificationKey=key;
                    if(groups!=null) for(int gi=0;gi<groups.length();gi++) {
                        JSONObject g=groups.getJSONObject(gi);JSONArray members=g.optJSONArray("members");if(members==null)continue;
                        for(int mi=0;mi<members.length();mi++) {JSONObject m=members.getJSONObject(mi);if(m.optString("routineId").equals(original.getString("id"))&&m.optString("time").equals(time)){title=g.getString("name");notificationKey=g.getString("id")+"|"+d+"|"+time;}}
                    }
                    String unique=notificationKey+"|"+epoch;
                    JSONObject entry=grouped.get(unique);
                    if(entry!=null){entry.getJSONArray("occurrences").put(key);entry.getJSONObject("signatures").put(key,signature(r,time));continue;}
                    String type=r.optString("type","custom");String body=body(type,time,settings.optString("timeFormat","12"),!notificationKey.equals(key));
                    entry=new JSONObject().put("key",notificationKey).put("at",epoch).put("title",title).put("body",body).put("occurrences",new JSONArray().put(key)).put("repeatMinutes",repeat).put("type",type).put("signature",signature(r,time)).put("signatures",new JSONObject().put(key,signature(r,time)));
                    grouped.put(unique,entry);entries.add(entry);
                }
            }
        }
        entries.sort((a,b)->Long.compare(a.optLong("at"),b.optLong("at")));for(JSONObject e:entries)out.put(e);return out;
    }
}
