package app.dailycare;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.*;
import java.util.*;

/** Android owns reminder delivery; no WebView timer or permanent process is required. */
public final class Reminders {
    static final String PREF="reminders", CHANNEL="care", CHANNEL_EARLY="care-early";
    /** 2.0: dedicated channel for early alerts + gentle nudges. A fresh channel
     *  ID guarantees the phone's default notification sound (not a ring, not
     *  silent), even if the older 'care' channel was created with no sound. */
    static void earlyChannel(Context c) {
        NotificationChannel channel=new NotificationChannel(CHANNEL_EARLY,"Early alerts & nudges",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Quiet before-reminder alerts and gentle nudges");
        channel.enableVibration(true);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build());
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static final String MAINTENANCE="__maintenance__";
    static void channel(Context c) {
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Routine reminders",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Your private scheduled routines and to-dos");
        channel.enableVibration(true);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build());
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static boolean exact(Context c) {return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static volatile String runtimeError="";
    static String getError(Context c){return runtimeError.isEmpty()?c.getSharedPreferences(PREF,0).getString("error",""):runtimeError;}
    static void write(Context c,android.content.SharedPreferences.Editor edit)throws java.io.IOException {if(!edit.commit()){runtimeError="Reminder changes could not be saved. Free device storage and reopen the app.";throw new java.io.IOException(runtimeError);}}
    static void error(Context c,String message) {runtimeError=message;if(!c.getSharedPreferences(PREF,0).edit().putString("error",message).commit())runtimeError="Reminder changes could not be saved. Free device storage and reopen the app.";}
    static boolean deliveryAllowed(Context c,JSONObject state)throws JSONException {NotificationManager n=c.getSystemService(NotificationManager.class);NotificationChannel channel=n.getNotificationChannel(CHANNEL);return state.getJSONObject("settings").optBoolean("enabled")&&n.areNotificationsEnabled()&&channel!=null&&channel.getImportance()>NotificationManager.IMPORTANCE_NONE;}
    static void cancelKnown(Context c){try{JSONArray p=new JSONArray(c.getSharedPreferences(PREF,0).getString("plan","[]"));AlarmManager a=c.getSystemService(AlarmManager.class);for(int i=0;i<p.length();i++)a.cancel(alarm(c,p.getJSONObject(i).getString("key")));a.cancel(alarm(c,MAINTENANCE));}catch(Exception ignored){}try{Nudges.cancel(c);}catch(Throwable ignored){}try{EarlyAlerts.cancel(c);}catch(Throwable ignored){}}

    static PendingIntent alarm(Context c,String key) {
        Intent intent=new Intent(c,ReminderReceiver.class).setAction("app.dailycare.REMIND").setData(Uri.parse("care://alarm/"+Uri.encode(key))).putExtra("key",key);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    /** Stable per-notification request code for the tap intent, so FLAG_UPDATE_CURRENT can
     *  never clobber one reminder's tap with another's. Pure: unit-tested without a runtime. */
    static int tapRequestCode(String key){ return key==null ? 0 : (key.hashCode() & 0x7fffffff); }

    /** Diagnostics: the ring service could not start or run. Records the failure's
     *  class/message (no stack trace) so Settings → About can say why ringing did
     *  not sound at fire time. Never throws. */
    static void recordRingServiceFailure(Context c, Throwable e) {
        try {
            String msg = e == null ? "" : e.getClass().getName() + ": " + String.valueOf(e.getMessage());
            c.getSharedPreferences(PREF, 0).edit()
                    .putString("diagRingServiceError", msg)
                    .putLong("diagRingServiceAt", System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {}
    }
    static String getRingServiceError(Context c) {
        try { return c.getSharedPreferences(PREF, 0).getString("diagRingServiceError", ""); }
        catch (Throwable ignored) { return ""; }
    }
    static long getRingServiceErrorAt(Context c) {
        try { return c.getSharedPreferences(PREF, 0).getLong("diagRingServiceAt", 0); }
        catch (Throwable ignored) { return 0; }
    }
    /** Stable per-action request code, distinct from the tap code for the same key, so
     *  FLAG_UPDATE_CURRENT can never merge the tap and the action PendingIntents.
     *  Pure: unit-tested without a runtime. */
    static int actionRequestCode(String key, String auto) {
        return tapRequestCode(key == null ? auto : key + "#" + auto);
    }
    /** The intent a notification action fires: opens MainActivity on the reminder
     *  card and asks the page to apply {@code auto} once ("record"/"snooze").
     *  No careRing extra: the service keeps ringing until the action is applied
     *  and the page stops it. */
    static Intent actionIntent(Context c, String key, String auto) {
        return new Intent(c, MainActivity.class).setData(Uri.parse("care://open/" + Uri.encode(key)))
                .putExtra("careKey", key).putExtra("careAuto", auto)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
    /** The intent fired when the user dismisses the reminder notification: stops the
     *  ring service without removing anything else (the notification is already gone). */
    static Intent deleteIntent(Context c, String key) {
        return new Intent(c, ReminderReceiver.class).setAction("app.dailycare.RING_STOP")
                .setData(Uri.parse("care://ringstop/" + Uri.encode(key == null ? "" : key)));
    }
    /** The one notification for a reminder, built identically whether it is posted
     *  directly (notification-only mode, or the ring fallback) or owned by the ring
     *  service as its foreground notification. Ring-mode notifications carry
     *  "Mark taken" and "Snooze" actions that open the app and apply once. */
    static Notification buildNotification(Context c, JSONObject entry, boolean ring) {
        String key = entry.optString("key");
        Intent open = new Intent(c, MainActivity.class).setData(Uri.parse("care://open/" + Uri.encode(key))).putExtra("careKey", key).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (ring) open.putExtra("careRing", true);
        PendingIntent tap = PendingIntent.getActivity(c, tapRequestCode(key), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = entry.optString("title", "Reminder"), body = entry.optString("body", "Open to review your routine.");
        Notification.Builder b = new Notification.Builder(c, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(tap).setFullScreenIntent(tap, true).setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PUBLIC).setShowWhen(true).setWhen(entry.optLong("at", System.currentTimeMillis()));
        if (ring) {
            PendingIntent record = PendingIntent.getActivity(c, actionRequestCode(key, "record"), actionIntent(c, key, "record"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent snooze = PendingIntent.getActivity(c, actionRequestCode(key, "snooze"), actionIntent(c, key, "snooze"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(0, "Mark taken", record).build());
            b.addAction(new Notification.Action.Builder(0, "Snooze", snooze).build());
            PendingIntent gone = PendingIntent.getBroadcast(c, actionRequestCode(key, "delete"), deleteIntent(c, key), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setDeleteIntent(gone);
        }
        return b.build();
    }
    /** Diagnostics (Settings → About): what the last posted notification was. Never throws. */
    static void recordShown(Context c,String key,long at,boolean ring){
        try{c.getSharedPreferences(PREF,0).edit().putString("diagShownKey",key==null?"":key).putLong("diagShownAt",at).putBoolean("diagShownRing",ring).apply();}
        catch(Throwable ignored){}
    }
    /** Diagnostics: the activity was launched with a notification-tap intent. A repeat
     *  launch with the same key (recreation, repeat tap) keeps the "card reached" flag;
     *  only a different key starts a fresh, unreached launch. Never throws. */
    static void recordNotificationLaunch(Context c,String key,boolean ring,long at){
        try{
            android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);
            String stored=p.getString("diagLaunchKey","");
            android.content.SharedPreferences.Editor e=p.edit();
            e.putString("diagLaunchKey",key==null?"":key).putLong("diagLaunchAt",at).putBoolean("diagLaunchRing",ring);
            if(LaunchKey.isNewLaunch(stored,key))e.putBoolean("diagLaunchDelivered",false);
            e.apply();
        }catch(Throwable ignored){}
    }
    /** Diagnostics: the page reports the reminder card focused after a notification tap.
     *  Only marks a launch that was actually recorded. Never throws. */
    static void markLaunchDelivered(Context c){
        try{
            android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);
            if(!p.getString("diagLaunchKey","").isEmpty())p.edit().putBoolean("diagLaunchDelivered",true).apply();
        }catch(Throwable ignored){}
    }
    public static synchronized void replace(Context c,String json) {
        try {JSONObject saved=new JSONObject(json);saved.getJSONObject("settings");write(c,c.getSharedPreferences(PREF,0).edit().putString("state",json));refresh(c,true);if(saved.optJSONArray("routines")!=null&&saved.getJSONArray("routines").length()==0&&saved.optJSONArray("logs")!=null&&saved.getJSONArray("logs").length()==0)write(c,c.getSharedPreferences(PREF,0).edit().remove("delivered").remove("shown"));}
        catch(Throwable e) {try{cancelKnown(c);}catch(Throwable ignored){}try{error(c,"Saved reminders could not be read. Review your routines before enabling reminders again.");}catch(Throwable ignored){}}
    }
    static JSONObject parseState(String raw)throws JSONException{
        if(raw==null||raw.trim().isEmpty())return new JSONObject("{\"settings\":{\"enabled\":false},\"routines\":[],\"groups\":[],\"logs\":[]}");
        JSONObject parsed=new JSONObject(raw);
        // A parseable but partial stored state must never crash scheduling: tolerate
        // missing sections instead of throwing at every call site. Unparseable JSON
        // still throws and takes the existing "could not be read" error path.
        if(!(parsed.opt("settings") instanceof JSONObject))parsed.put("settings",new JSONObject().put("enabled",false));
        if(!(parsed.opt("routines") instanceof JSONArray))parsed.put("routines",new JSONArray());
        if(!(parsed.opt("groups") instanceof JSONArray))parsed.put("groups",new JSONArray());
        if(!(parsed.opt("logs") instanceof JSONArray))parsed.put("logs",new JSONArray());
        return parsed;
    }
    static JSONObject state(Context c)throws JSONException{return parseState(c.getSharedPreferences(PREF,0).getString("state",null));}
    static synchronized void refresh(Context c,boolean reconcileNotifications) {
        try {
            JSONObject s=state(c);AlarmManager manager=c.getSystemService(AlarmManager.class);
            JSONArray previous=new JSONArray(c.getSharedPreferences(PREF,0).getString("plan","[]"));
            for(int i=0;i<previous.length();i++)manager.cancel(alarm(c,previous.getJSONObject(i).getString("key")));
            manager.cancel(alarm(c,MAINTENANCE));
            write(c,c.getSharedPreferences(PREF,0).edit().putInt("scheduledCount",0));
            if(reconcileNotifications)cancelResolved(c,s);
            long now=System.currentTimeMillis();JSONArray oldPending=new JSONArray(c.getSharedPreferences(PREF,0).getString("pending","[]"));JSONObject ledger=new JSONObject(c.getSharedPreferences(PREF,0).getString("delivered","{}"));
            boolean allowed=deliveryAllowed(c,s);oldPending=Plan.afterPermissionTransition(oldPending,c.getSharedPreferences(PREF,0).getBoolean("deliveryAllowed",false),allowed,now);
            write(c,c.getSharedPreferences(PREF,0).edit().putBoolean("deliveryAllowed",allowed));
            JSONArray plan=Plan.mergePending(oldPending,Plan.build(s,now),s,ledger,now),armed=new JSONArray();
            long first=plan.length()>0?plan.getJSONObject(0).getLong("at"):Long.MAX_VALUE;
            for(int i=0;i<plan.length();i++) {
                JSONObject e=plan.getJSONObject(i);if(e.getLong("at")!=first)break;armed.put(e);
            }
            // Persist before arming, so a receiver can always verify the current occurrence.
            write(c,c.getSharedPreferences(PREF,0).edit().putString("plan",armed.toString()).putString("pending",plan.toString()).putString("error",""));
            for(int i=0;i<armed.length();i++) {
                JSONObject e=armed.getJSONObject(i);
                long fire=Math.max(first,System.currentTimeMillis()+250);
                if(exact(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fire,alarm(c,e.getString("key")));
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fire,alarm(c,e.getString("key")));
                write(c,c.getSharedPreferences(PREF,0).edit().putInt("scheduledCount",i+1));
            }
            if(s.getJSONObject("settings").optBoolean("enabled"))manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+86400000L,alarm(c,MAINTENANCE));
            try{Nudges.reschedule(c);}catch(Throwable ignored){}try{EarlyAlerts.reschedule(c);}catch(Throwable ignored){}
            runtimeError="";
        } catch(Throwable e) {try{cancelKnown(c);}catch(Throwable ignored){}try{Nudges.cancel(c);}catch(Throwable ignored){}try{error(c,"Android could not schedule reminders. Check notification and alarm access, then return to the app.");}catch(Throwable ignored){}}
    }
    static void cancelResolved(Context c,JSONObject s)throws Exception {
        JSONObject shown=new JSONObject(c.getSharedPreferences(PREF,0).getString("shown","{}"));
        Set<String> done=Plan.complete(s);boolean enabled=s.getJSONObject("settings").optBoolean("enabled");
        Set<String> archived=new HashSet<>();JSONArray routines=s.getJSONArray("routines");for(int i=0;i<routines.length();i++){JSONObject r=routines.getJSONObject(i);if(r.optBoolean("archived"))archived.add(r.getString("id"));}
        Iterator<String> keys=shown.keys();List<String> remove=new ArrayList<>();
        while(keys.hasNext()) {
            String key=keys.next();JSONObject entry=shown.getJSONObject(key);JSONArray members=entry.optJSONArray("occurrences");boolean resolved=!enabled;
            if(!resolved&&members!=null){resolved=true;for(int i=0;i<members.length();i++){String occurrence=members.getString(i);if(!done.contains(occurrence)&&!archived.contains(occurrence.split("\\|")[0]))resolved=false;}}
            if(resolved){c.getSystemService(NotificationManager.class).cancel(key,0);try{RingService.stopIfShowing(c,key);}catch(Throwable ignored){}remove.add(key);}
        }
        for(String key:remove)shown.remove(key);write(c,c.getSharedPreferences(PREF,0).edit().putString("shown",shown.toString()));
    }
    static void restore(Context c){refresh(c,true);}
    static synchronized void receive(Context c,String key,long now) {
        try {
            JSONArray armed=new JSONArray(c.getSharedPreferences(PREF,0).getString("plan","[]"));boolean current=MAINTENANCE.equals(key);
            for(int i=0;i<armed.length();i++)if(armed.getJSONObject(i).getString("key").equals(key)&&armed.getJSONObject(i).getLong("at")<=now+1000)current=true;
            if(!current)return;
            JSONArray pending=new JSONArray(c.getSharedPreferences(PREF,0).getString("pending","[]"));
            JSONObject ledger=new JSONObject(c.getSharedPreferences(PREF,0).getString("delivered","{}"));
            JSONObject currentState=state(c);boolean allowed=deliveryAllowed(c,currentState);pending=Plan.afterPermissionTransition(pending,c.getSharedPreferences(PREF,0).getBoolean("deliveryAllowed",false),allowed,now);write(c,c.getSharedPreferences(PREF,0).edit().putBoolean("deliveryAllowed",allowed));
            JSONArray due=Plan.due(pending,ledger,now);
            for(int i=0;i<due.length();i++){JSONObject e=due.getJSONObject(i);String token=e.getString("key")+"|"+e.getLong("at");ledger.put(token,now);}
            // Commit the attempt ledger before notifying; duplicate intents never re-alert a dose.
            java.util.Iterator<String> entries=ledger.keys();java.util.List<String> expired=new java.util.ArrayList<>();
            while(entries.hasNext()){String token=entries.next();if(now-ledger.optLong(token)>172800000L)expired.add(token);}
            for(String token:expired)ledger.remove(token);
            write(c,c.getSharedPreferences(PREF,0).edit().putString("delivered",ledger.toString()));
            for(int i=0;i<due.length();i++)show(c,due.getJSONObject(i));
            String deliveryError=getError(c);refresh(c,false);
            if(!deliveryError.isEmpty())error(c,deliveryError);
        }catch(Throwable e){try{error(c,"Android could not read the scheduled reminder. Reopen the app to refresh it.");}catch(Throwable ignored){}}
    }
    static void show(Context c,JSONObject entry) {
        channel(c);String key=entry.optString("key");
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
        if(!manager.areNotificationsEnabled()||channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE){error(c,"Notifications are blocked. Enable the app and its reminder channel in Android settings.");return;}
        boolean ring=false;try{ring=Plan.ringMode(state(c));}catch(Exception ignored){}
        try {
            // Ring mode: the foreground ring service owns the notification and the
            // sound, so ringing starts at fire time even when the full-screen intent
            // is blocked and the activity never launches. If the service cannot
            // start, fall back to posting the notification directly.
            boolean owned=false;
            if(ring){try{owned=RingService.start(c,entry);}catch(Throwable ignored){owned=false;}}
            if(!owned)manager.notify(key,0,buildNotification(c,entry,ring));
            recordShown(c,key,System.currentTimeMillis(),ring);
            JSONObject shown=new JSONObject(c.getSharedPreferences(PREF,0).getString("shown","{}"));shown.put(key,entry);write(c,c.getSharedPreferences(PREF,0).edit().putString("shown",shown.toString()));
        }catch(Throwable e){try{error(c,"Android did not display the reminder. Check notification permissions and channel settings.");}catch(Throwable ignored){}}
    }
    // Kept for compatibility with older bundled code; the product UI has no test button.
    static void show(Context c,int code,String title,String body){try{show(c,new JSONObject().put("key","compat-"+code).put("title",title).put("body",body));}catch(JSONException e){}}
}
