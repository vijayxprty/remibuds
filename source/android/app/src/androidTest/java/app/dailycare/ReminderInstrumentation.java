package app.dailycare;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.util.Base64;
import android.webkit.WebView;
import org.json.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;

/** Dependency-free device acceptance harness, packaged only in the separate instrumentation APK. */
public class ReminderInstrumentation extends Instrumentation {
    Bundle arguments;
    @Override public void onCreate(Bundle args){arguments=args==null?new Bundle():args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            String scenario=arguments.getString("scenario","inspect");
            if("seed".equals(scenario)) {
                String encoded=arguments.getString("stateBase64","");
                if(encoded.isEmpty())throw new IllegalArgumentException("stateBase64 is required");
                String state=new String(Base64.decode(encoded,Base64.DEFAULT),"UTF-8");new JSONObject(state).getJSONObject("settings");
                MainActivity activity=open(null);WebView web=web(activity);awaitPage(web);
                String expression="localStorage.setItem('daily-care-personal-v1',"+JSONObject.quote(state)+"); true";
                evaluate(web,expression);
                evaluate(web,"window.__acceptanceOld = true; true");
                runOnMainSync(()->web.loadUrl("https://care.local/?acceptance="+System.nanoTime()));awaitPage(web);
                runOnMainSync(()->Reminders.replace(getTargetContext(),state));
                result.putString("readiness",activity.new Bridge().status());
                result.putString("page",evaluate(web,"document.body.innerText"));
            }else if("openroute".equals(scenario)) {
                String key=arguments.getString("key","");if(key.isEmpty())throw new IllegalArgumentException("key is required");
                MainActivity activity=open(key);WebView web=web(activity);awaitPage(web);
                result.putString("page",evaluate(web,"document.body.innerText"));
                result.putString("route",evaluate(web,"JSON.stringify(Array.from(document.querySelectorAll('[data-occurrence]')).map(e=>({key:e.dataset.occurrence,outline:e.style.outline})))"));
                result.putString("readiness",activity.new Bridge().status());
            }else if("cancel".equals(scenario)) {
                JSONObject state=new JSONObject(getTargetContext().getSharedPreferences("reminders",0).getString("state","{}"));state.getJSONObject("settings").put("enabled",false);
                runOnMainSync(()->Reminders.replace(getTargetContext(),state.toString()));
            }else if(!"inspect".equals(scenario))throw new IllegalArgumentException("Unknown scenario");
            android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("reminders",0);
            result.putString("plan",prefs.getString("plan","[]"));result.putString("pending",prefs.getString("pending","[]"));result.putString("delivered",prefs.getString("delivered","{}"));result.putString("error",prefs.getString("error",""));
            JSONArray notifications=new JSONArray();
            for(StatusBarNotification n:getTargetContext().getSystemService(NotificationManager.class).getActiveNotifications()){
                Notification note=n.getNotification();notifications.put(new JSONObject().put("tag",n.getTag()).put("title",String.valueOf(note.extras.getCharSequence(Notification.EXTRA_TITLE))).put("body",String.valueOf(note.extras.getCharSequence(Notification.EXTRA_TEXT))).put("visibility",note.visibility).put("category",note.category).put("postTime",n.getPostTime()));
            }
            result.putString("notifications",notifications.toString());result.putString("outcome","passed");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("outcome","failed");result.putString("reason",e.getClass().getSimpleName()+": "+e.getMessage());finish(Activity.RESULT_CANCELED,result);}
    }
    MainActivity open(String key){Intent i=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(key!=null)i.putExtra("careKey",key);return (MainActivity)startActivitySync(i);}
    WebView web(MainActivity activity)throws Exception{Field field=MainActivity.class.getDeclaredField("web");field.setAccessible(true);return (WebView)field.get(activity);}
    String evaluate(WebView web,String expression)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};runOnMainSync(()->web.evaluateJavascript(expression,value->{result[0]=value;done.countDown();}));if(!done.await(8,TimeUnit.SECONDS))throw new IllegalStateException("WebView script timed out");return result[0];}
    void awaitPage(WebView web)throws Exception{for(int i=0;i<100;i++){if("true".equals(evaluate(web,"!window.__acceptanceOld && document.readyState==='complete' && !!document.querySelector('#app button')")))return;Thread.sleep(100);}throw new IllegalStateException("App did not render");}
}
