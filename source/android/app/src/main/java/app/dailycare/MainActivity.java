package app.dailycare;

import android.app.*;
import android.os.*;
import android.webkit.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.media.RingtoneManager;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.ContentUris;
import android.provider.MediaStore;
import android.widget.Toast;
import android.net.Uri;
import android.view.WindowManager;
import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    static final String DONATE_URI=DonateFlow.PAYLOAD;
    static final String DONATE_UPI_ID=DonateFlow.UPI_ID;
    WebView web; String pendingBackup; String pendingCsv; String pendingCsvName; ValueCallback<Uri[]> fileCallback;
    /** Payloads that arrived before the page was ready (e.g. after an activity
     *  recreation while a picker was open). Delivered in onPageFinished, never dropped. */
    String pendingBackupPayload=null; String pendingAudioPayload=null;
    /** Notification-tap handoff state. pendingKey survives until the page reports the
     *  reminder card focused (keyHandled); it is never consumed by merely reading it,
     *  so a tap that arrives before the page is ready is retried after every render.
     *  pendingRing is consumed once by maybeStartRinging, so ringing starts exactly once
     *  no matter which lifecycle entry point runs first. */
    volatile String pendingKey=""; volatile boolean pendingRing=false;
    /** Set by the page once its script has loaded, so the back button never goes
     *  dead when the page is not ready: before that, back behaves normally. */
    volatile boolean pageReady=false;
    /** Pending notification-action request ("record"/"snooze") from a notification
     *  action button. Cleared when the page applies it (keyHandled) or decides it
     *  cannot be applied (autoHandled), so a stale action can never fire later. */
    volatile String pendingAuto="";
    /** Run a lifecycle/callback body so an unexpected Throwable is recorded via
     *  CrashReports instead of crashing the app. Used on every return-to-app path. */
    void guard(String where,Guarded body){try{body.run();}catch(Throwable e){try{CrashReports.record(this,where,e);}catch(Throwable ignored){}}}
    interface Guarded{void run() throws Throwable;}
    void captureLaunchIntent(Intent i){
        if(i==null)return;
        String key=null,auto=null;boolean ring=false;
        try{key=i.getStringExtra("careKey");}catch(Throwable ignored){}
        try{auto=i.getStringExtra("careAuto");}catch(Throwable ignored){}
        try{ring=i.getBooleanExtra("careRing",false);}catch(Throwable ignored){}
        pendingKey=LaunchKey.capture(pendingKey,key);
        pendingAuto=LaunchKey.captureAuto(pendingAuto,auto);
        if(ring)pendingRing=true;
        // Diagnostics: a launch carrying a notification key is recorded here (onCreate
        // and onNewIntent both funnel through this), so Settings → About can say
        // whether tapping the notification opened the app at all.
        if(key!=null&&!key.isEmpty()){try{Reminders.recordNotificationLaunch(this,key,ring,System.currentTimeMillis());}catch(Throwable ignored){}}
    }
    void maybeStartRinging(){
        try{
            Intent i=getIntent();
            boolean ring=pendingRing;
            if(i!=null&&i.getBooleanExtra("careRing",false)){ring=true;i.removeExtra("careRing");}
            if(!ring)return;
            pendingRing=false;
            JSONObject s=Reminders.state(this);
            if(Plan.ringMode(s))AlarmPlayer.start(this,s);
        }catch(Throwable ignored){}
    }
    @Override protected void onDestroy(){guard("onDestroy",()->{if(fileCallback!=null){try{fileCallback.onReceiveValue(null);}catch(Throwable ignored){}fileCallback=null;}if(isFinishing())AlarmPlayer.stop();TonePreview.stop();if(web!=null){try{web.stopLoading();}catch(Throwable ignored){}try{web.loadUrl("about:blank");}catch(Throwable ignored){}try{web.destroy();}catch(Throwable ignored){}web=null;pageReady=false;}});super.onDestroy();}
    @Override protected void onPause(){guard("onPause",()->TonePreview.stop());super.onPause();}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try{CrashReports.install(this);}catch(Throwable ignored){}
        guard("onCreate",()->{
        captureLaunchIntent(getIntent()); Reminders.channel(this); maybeStartRinging();
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}else{getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);}
        getWindow().setStatusBarColor(0xffedf7f2);getWindow().setNavigationBarColor(0xffedf7f2);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        web=new WebView(this);
        web.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.addJavascriptInterface(new Bridge(),"CareAndroid");
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                try{
                    if(fileCallback!=null)try{fileCallback.onReceiveValue(null);}catch(Throwable ignored){}
                    fileCallback=callback;
                    try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),9);}
                    catch(Throwable e){try{callback.onReceiveValue(null);}catch(Throwable ignored){}fileCallback=null;}
                }catch(Throwable e){try{CrashReports.record(MainActivity.this,"onShowFileChooser",e);}catch(Throwable ignored){}try{if(callback!=null)callback.onReceiveValue(null);}catch(Throwable ignored){}}
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
                try{
                    if(req==null)return response(403,"Forbidden");
                    Uri u=req.getUrl();if(u==null||!"https".equals(u.getScheme())||!"care.local".equals(u.getHost()))return response(403,"Forbidden");
                    String p=u.getPath();if(p==null||p.equals("/"))p="/index.html";if(p.contains(".."))return response(403,"Forbidden");
                    try {String ext=p.substring(p.lastIndexOf('.')+1);String mime=ext.equals("js")?"text/javascript":ext.equals("css")?"text/css":ext.equals("svg")?"image/svg+xml":ext.equals("json")?"application/json":ext.equals("png")?"image/png":ext.equals("jpg")||ext.equals("jpeg")?"image/jpeg":"text/html";return new WebResourceResponse(mime,"UTF-8",getAssets().open("web"+p));}
                    catch(Throwable e){return response(404,"Not Found");}
                }catch(Throwable e){try{CrashReports.record(MainActivity.this,"shouldInterceptRequest",e);}catch(Throwable ignored){}return response(403,"Forbidden");}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
                try{
                    Uri u=req==null?null:req.getUrl();if("https".equals(u==null?null:u.getScheme())&&u.getHost()!=null&&u.getHost().endsWith("instagram.com")){try{MainActivity.this.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW,u),"Open in"));}catch(Throwable ignored){}return true;}return !"care.local".equals(u==null?null:u.getHost());
                }catch(Throwable e){try{CrashReports.record(MainActivity.this,"shouldOverrideUrlLoading",e);}catch(Throwable ignored){}return true;}
            }
            @Override public void onPageFinished(WebView v,String url){guard("onPageFinished",()->{resumeWeb();deliverPendingPayloads();});}
        });
        web.loadUrl("https://care.local/");
        });
    }
    WebResourceResponse response(int code,String label){return new WebResourceResponse("text/plain","UTF-8",code,label,null,new ByteArrayInputStream(new byte[0]));}
    void resumeWeb(){if(web!=null){try{web.evaluateJavascript("window.careResume && window.careResume()",null);}catch(Throwable ignored){}}}
    void deliverPendingPayloads(){if(web==null)return;try{if(pendingBackupPayload!=null){String s=pendingBackupPayload;pendingBackupPayload=null;deliverBackupText(s);}}catch(Throwable ignored){}try{if(pendingAudioPayload!=null){String s=pendingAudioPayload;pendingAudioPayload=null;web.evaluateJavascript("window.careAudioPicked && window.careAudioPicked("+JSONObject.quote(s)+")",null);}}catch(Throwable ignored){}}
    /** Deliver a backup to JS in small chunks (2.0). A single evaluateJavascript
     *  call over ~1 MB can fail at the Binder limit, which made large real
     *  backups silently fail — and, on some phones, killed the restore flow.
     *  JS reassembles the chunks and only then calls careBackupPicked. */
    void deliverBackupText(String text){if(web==null||text==null)return;try{
        if(text.length()<=100000){web.evaluateJavascript("window.careBackupPicked && window.careBackupPicked("+JSONObject.quote(text)+")",null);return;}
        int chunk=100000;
        for(int i=0;i<text.length();i+=chunk){
            String part=text.substring(i,Math.min(text.length(),i+chunk));
            boolean last=(i+chunk>=text.length());
            web.evaluateJavascript("window.careBackupChunk && window.careBackupChunk("+JSONObject.quote(part)+","+last+")",null);
        }
    }catch(Throwable ignored){}}
    void toastUi(String msg){runOnUiThread(()->{try{Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();}catch(Throwable ignored){}});}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);guard("onNewIntent",()->{setIntent(i);captureLaunchIntent(i);maybeStartRinging();if(web!=null){try{web.evaluateJavascript("window.careOpenNotification && window.careOpenNotification()",null);}catch(Throwable ignored){}}});}
    @Override public void onBackPressed(){
        if(!pageReady||web==null){super.onBackPressed();return;}
        try{web.evaluateJavascript("window.careBackPressed && window.careBackPressed()",null);}
        catch(Throwable e){try{CrashReports.record(this,"onBackPressed",e);}catch(Throwable ignored){}super.onBackPressed();}
    }
    @Override protected void onResume(){super.onResume();guard("onResume",()->{maybeStartRinging();Reminders.restore(this);resumeWeb();});}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);guard("onRequestPermissionsResult",()->{Reminders.restore(this);resumeWeb();});}
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        guard("onActivityResult",()->{
        if(code==9&&fileCallback!=null){try{fileCallback.onReceiveValue(result==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);}catch(Throwable ignored){}fileCallback=null;}
        if(code==8&&pendingBackup!=null){try{if(result==RESULT_OK&&data!=null&&data.getData()!=null)try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(pendingBackup.getBytes("UTF-8"));}catch(Throwable e){Reminders.error(this,"The backup could not be saved. Check the selected location and try again.");}}catch(Throwable ignored){}pendingBackup=null;}
        if(code==11&&pendingCsv!=null){try{if(result==RESULT_OK&&data!=null&&data.getData()!=null)try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(pendingCsv.getBytes("UTF-8"));}catch(Throwable e){Reminders.error(this,"The CSV export could not be saved. Check the selected location and try again.");}}catch(Throwable ignored){}pendingCsv=null;pendingCsvName=null;}
        if(code==12&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            // Restore (2.0): read on a background thread, never the main thread.
            // 1.9.x read on the main thread (ANR risk on slow/cloud files) and
            // decoded UTF-8 per 8 KB chunk, which splits multi-byte characters
            // (Hindi names, emoji) and corrupts the JSON. Now: whole bytes first,
            // one UTF-8 decode, chunked JS delivery. The whole thread body is
            // guarded, so no failure here can ever kill the app — it only shows
            // a Toast and leaves the current records untouched.
            final Uri uri=data.getData();
            try{
                new Thread(()->{
                    try{
                        byte[] bytes;
                        try(ByteArrayOutputStream bos=new ByteArrayOutputStream();InputStream in=getContentResolver().openInputStream(uri)){
                            if(in==null)throw new IOException("no stream");
                            byte[] buf=new byte[8192];int n;long total=0;
                            while((n=in.read(buf))>0){total+=n;if(total>5*1024*1024)throw new IOException("backup too large");bos.write(buf,0,n);}
                            bytes=bos.toByteArray();
                        }
                        final String text=new String(bytes,"UTF-8");
                        runOnUiThread(()->{try{if(pageReady&&web!=null)deliverBackupText(text);else pendingBackupPayload=text;}catch(Throwable ignored){}});
                    }catch(Throwable e){toastUi("That backup file could not be read. Check the file and try again.");}
                }).start();
            }catch(Throwable e){toastUi("That backup file could not be read. Check the file and try again.");}
        }
        if(code==10&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            // Custom audio (1.9.1): copy on a background thread to a temp file,
            // then rename atomically, so a failed/interrupted copy never leaves a
            // truncated tone behind, and a large song never blocks the UI thread.
            final Uri uri=data.getData();
            try{
            new Thread(()->{
                String name="Custom audio";
                // 2.0.2: no takePersistableUriPermission — the file is copied
                // immediately, so a temporary read grant is all that is needed.
                // Persisting failed on some Samsung providers and added nothing.
                try{
                    String[] proj={OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};Cursor cur=null;
                    try{cur=getContentResolver().query(uri,proj,null,null,null);if(cur!=null&&cur.moveToFirst()){try{int idx=cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(idx>=0&&cur.getString(idx)!=null)name=cur.getString(idx);int sidx=cur.getColumnIndex(OpenableColumns.SIZE);if(sidx>=0&&cur.getLong(sidx)>50L*1024L*1024L)throw new IOException("audio too large");}catch(IOException e){throw e;}catch(Throwable ignored){}}}finally{try{if(cur!=null)cur.close();}catch(Throwable ignored){}}
                    File tmp=new File(getFilesDir(),AlarmPlayer.CUSTOM_FILE+".tmp");File dest=new File(getFilesDir(),AlarmPlayer.CUSTOM_FILE);
                    try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){
                        if(in==null)throw new IOException("no stream");
                        byte[] buf=new byte[8192];int n;long total=0;while((n=in.read(buf))>0){total+=n;if(total>50L*1024L*1024L)throw new IOException("audio too large");out.write(buf,0,n);}
                    }
                    if(!tmp.exists()||tmp.length()==0)throw new IOException("empty audio");
                    if(!tmp.renameTo(dest)){try(InputStream src=new FileInputStream(tmp);OutputStream out=new FileOutputStream(dest)){byte[] buf=new byte[8192];int n;while((n=src.read(buf))>0)out.write(buf,0,n);}tmp.delete();}
                    if(!dest.exists()||dest.length()==0)throw new IOException("audio not saved");
                    final String finalName=name;
                    runOnUiThread(()->{try{JSONObject payload=new JSONObject().put("file",AlarmPlayer.CUSTOM_FILE).put("name",finalName);if(pageReady&&web!=null)web.evaluateJavascript("window.careAudioPicked && window.careAudioPicked("+JSONObject.quote(payload.toString())+")",null);else pendingAudioPayload=payload.toString();}catch(Throwable ignored){}});
                }catch(Throwable e){try{tmpCleanup();}catch(Throwable ignored){}runOnUiThread(()->{try{Toast.makeText(MainActivity.this,"That audio file could not be saved. Check storage and try again.",Toast.LENGTH_LONG).show();}catch(Throwable ignored){}});}
            }).start();
            }catch(Throwable e){toastUi("That audio file could not be saved. Check storage and try again.");}
        }
        });
    }
    void tmpCleanup(){try{File tmp=new File(getFilesDir(),AlarmPlayer.CUSTOM_FILE+".tmp");if(tmp.exists())tmp.delete();}catch(Throwable ignored){}}
    String onMain(Callable<String> operation){
        FutureTask<String> task=null;
        try {if(Looper.myLooper()==Looper.getMainLooper())return operation.call();task=new FutureTask<>(operation);runOnUiThread(task);return task.get(5,TimeUnit.SECONDS);}
        catch(java.util.concurrent.TimeoutException e){try{if(task!=null)task.cancel(false);}catch(Throwable ignored){}return "";}
        catch(Throwable e){return "";}
    }
    /** Same as onMain, for a boolean result. 2.0.2: tone previews used to run
     *  on the WebView thread, but Ringtone/MediaPlayer work belongs on the main
     *  thread — off-main-thread playback is a known Samsung crash source, and it
     *  can kill the app with no Java stack trace in Logs & errors. */
    boolean onMainBool(Callable<Boolean> operation){
        FutureTask<Boolean> task=null;
        try {if(Looper.myLooper()==Looper.getMainLooper())return operation.call();task=new FutureTask<>(operation);runOnUiThread(task);Boolean r=task.get(5,TimeUnit.SECONDS);return r!=null&&r;}
        catch(Throwable e){try{if(task!=null)task.cancel(false);}catch(Throwable ignored){}return false;}
    }
    String permissionState(){
        boolean asked=getPreferences(0).getBoolean("requested",false);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return asked?"denied":"default";
        return getSystemService(NotificationManager.class).areNotificationsEnabled()?"granted":"denied";
    }
    JSONObject nativeStatus()throws Exception {
        NotificationManager manager=getSystemService(NotificationManager.class);NotificationChannel channel=manager.getNotificationChannel(Reminders.CHANNEL);
        String permission=permissionState();int importance=channel==null?0:channel.getImportance();boolean allowed=importance>NotificationManager.IMPORTANCE_NONE;
        boolean exact=Reminders.exact(this),unrestricted=getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        JSONObject state=Reminders.state(this);boolean enabled=state.getJSONObject("settings").optBoolean("enabled");
        boolean zoneChanged=state.getJSONObject("settings").has("zone")&&!java.util.TimeZone.getTimeZone(state.getJSONObject("settings").getString("zone")).hasSameRules(java.util.TimeZone.getDefault());
        String error=Reminders.getError(this);int scheduled=error.isEmpty()?getSharedPreferences(Reminders.PREF,0).getInt("scheduledCount",0):0;
        String fullscreen=Build.VERSION.SDK_INT>=34?(manager.canUseFullScreenIntent()?"allowed":"blocked"):"allowed";
        android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
        return new JSONObject().put("permission",permission).put("channel",!allowed?"blocked":importance<NotificationManager.IMPORTANCE_HIGH?"quiet":"allowed").put("headsUp",importance>=NotificationManager.IMPORTANCE_HIGH).put("sound",channel!=null&&channel.getSound()!=null).put("exact",exact?"allowed":"blocked").put("battery",unrestricted?"unrestricted":"optimized").put("lockscreen",channel!=null&&channel.getLockscreenVisibility()==Notification.VISIBILITY_SECRET?"hidden":"unknown").put("fullscreen",fullscreen).put("enabled",enabled).put("scheduled",scheduled).put("ready",enabled&&permission.equals("granted")&&importance>=NotificationManager.IMPORTANCE_HIGH&&channel!=null&&channel.getSound()!=null&&exact&&error.isEmpty()&&!zoneChanged).put("error",error).put("timezoneChanged",zoneChanged).put("version",info.versionName).put("versionCode",Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode);
    }
    /** The settings screen that owns the full-screen-intent switch. Pure so it is
     *  unit-tested: on API 34+ the switch lives on its own
     *  ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT page (the generic App info page
     *  never shows it, which is why users saw "everything allowed" while ringing
     *  stayed blocked); below 34 there is no such page, so App info is the only
     *  target. */
    static String fullscreenSettingsAction(int sdkInt){
        return sdkInt>=34?Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT:Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
    }
    void settings(String kind){
        Intent intent;
        switch(kind==null?"notifications":kind){
            case "channel": intent=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,Reminders.CHANNEL);break;
            case "exact":intent=Build.VERSION.SDK_INT>=31?new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())):new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));break;
            case "battery":intent=new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);break;
            case "background":intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));break;
            case "lockscreen":intent=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,Reminders.CHANNEL);break;
            case "fullscreen":intent=new Intent(fullscreenSettingsAction(Build.VERSION.SDK_INT)).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());break;
            default:intent=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
        }
        try{startActivity(intent);}catch(Throwable e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Throwable ignored){try{Toast.makeText(MainActivity.this,"Phone settings could not be opened from here.",Toast.LENGTH_SHORT).show();}catch(Throwable ignored2){}}}
    }
    public class Bridge {
        @JavascriptInterface public String status(){return onMain(()->nativeStatus().toString());}
        @JavascriptInterface public String version(){return onMain(()->nativeStatus().optString("version"));}
        @JavascriptInterface public String scheduleStatus(){return onMain(()->Reminders.getError(MainActivity.this));}
        @JavascriptInterface public String permission(){return onMain(()->permissionState());}
        @JavascriptInterface public String launchKey(){return onMain(()->LaunchKey.peek(pendingKey));}
        @JavascriptInterface public String launchAuto(){return onMain(()->LaunchKey.peek(pendingAuto));}
        @JavascriptInterface public void autoHandled(){pendingAuto=LaunchKey.handledAuto();}
        @JavascriptInterface public void keyHandled(){pendingKey=LaunchKey.handled();pendingAuto=LaunchKey.handledAuto();try{Reminders.markLaunchDelivered(MainActivity.this);}catch(Throwable ignored){}}
        @JavascriptInterface public String donateReport(){return onMain(()->{
            try{
                SharedPreferences p=getSharedPreferences(DonateFlow.PREF,0);
                if(!p.contains("at"))return new JSONObject().put("exists",false).toString();
                int resolvers=p.getInt("resolvers",0);boolean attempted=p.getBoolean("attempted",false);String error=p.getString("error","");
                if(error==null)error="";
                return new JSONObject().put("exists",true).put("resolvers",resolvers).put("attempted",attempted).put("error",error).put("at",p.getLong("at",0)).put("summary",DonateFlow.summarizeAttempt(resolvers,attempted,error)).toString();
            }catch(Throwable e){return "{\"exists\":false}";}
        });}
        @JavascriptInterface public String launchReport(){return onMain(()->{
            try{
                SharedPreferences p=getSharedPreferences(Reminders.PREF,0);
                String shownKey=p.getString("diagShownKey","");long shownAt=p.getLong("diagShownAt",0);boolean shownRing=p.getBoolean("diagShownRing",false);
                String launchKey=p.getString("diagLaunchKey","");long launchedAt=p.getLong("diagLaunchAt",0);boolean delivered=p.getBoolean("diagLaunchDelivered",false);
                boolean opened=LaunchKey.matchesLaunch(shownKey,launchKey);
                boolean exists=!shownKey.isEmpty()||!launchKey.isEmpty();
                String ringServiceError=Reminders.getRingServiceError(MainActivity.this);
                long ringServiceAt=Reminders.getRingServiceErrorAt(MainActivity.this);
                return new JSONObject().put("exists",exists).put("shownKey",shownKey).put("shownAt",shownAt).put("shownRing",shownRing).put("launchKey",launchKey).put("launchedAt",launchedAt).put("openedViaNotification",opened).put("cardReached",opened&&delivered).put("ringServiceError",ringServiceError).put("ringServiceAt",ringServiceAt).toString();
            }catch(Throwable e){return "{\"exists\":false}";}
        });}
        @JavascriptInterface public void requestPermission(){runOnUiThread(()->{try{getPreferences(0).edit().putBoolean("requested",true).apply();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);else resumeWeb();}catch(Throwable ignored){try{resumeWeb();}catch(Throwable ignored2){}}});}
        @JavascriptInterface public void openSettings(String kind){runOnUiThread(()->settings(kind));}
        /** Test button in the Early alerts block: posts one quiet notification
         *  through the exact early-alert channel and sound, so the user can
         *  confirm early alerts will reach them without waiting for a real
         *  reminder. Returns false (and posts nothing) when notifications are
         *  blocked, so the page can say so instead of claiming success. */
        @JavascriptInterface public boolean testEarlyAlert(){return onMainBool(()->{
            try{
                Reminders.earlyChannel(MainActivity.this);
                NotificationManager m=getSystemService(NotificationManager.class);
                if(m==null||!m.areNotificationsEnabled())return false;
                NotificationChannel ch=m.getNotificationChannel(Reminders.CHANNEL_EARLY);
                if(ch==null||ch.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
                JSONObject e=new JSONObject().put("kind","early").put("key","test-early-alert")
                        .put("title","Test early alert")
                        .put("body","This is how an early alert will look before your reminder \u2014 for example, Evening walk in 1 hour \u00b7 Due at 6:00 PM.")
                        .put("at",System.currentTimeMillis()).put("due",System.currentTimeMillis()+3600000L).put("lead",60);
                EarlyAlertReceiver.show(MainActivity.this,e);
                return true;
            }catch(Throwable ignored){return false;}
        });}
        @JavascriptInterface public void schedule(String json){runOnUiThread(()->{try{Reminders.replace(MainActivity.this,json);}catch(Throwable ignored){}});}
        @JavascriptInterface public void pageReady(){pageReady=true;}
        @JavascriptInterface public void exitApp(){runOnUiThread(()->{try{finish();}catch(Throwable ignored){}});}
        @JavascriptInterface public void exportBackup(String json){runOnUiThread(()->{try{pendingBackup=json;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"remibuds-private-backup.json"),8);}catch(Throwable e){try{Toast.makeText(MainActivity.this,"The backup export could not be started.",Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}}});}
        @JavascriptInterface public void exportCsv(String csv,String filename){runOnUiThread(()->{try{pendingCsv=csv;pendingCsvName=filename==null?"remibuds-history.csv":filename.replaceAll("[^A-Za-z0-9._-]","_");startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE,pendingCsvName),11);}catch(Throwable e){try{Toast.makeText(MainActivity.this,"The CSV export could not be started.",Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}}});}
        @JavascriptInterface public String donate(){return onMain(()->{
            try{
                Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(DONATE_URI));
                // Package visibility can hide real handlers on Android 11+, so an empty
                // query result must never veto the chooser: attempt it anyway and treat
                // only a thrown attempt as "no payment app". See DonateFlow.decide.
                int resolverCount=0;
                try{List<ResolveInfo> resolvers=getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY);resolverCount=resolvers==null?0:resolvers.size();}
                catch(Throwable ignored){resolverCount=0;}
                boolean chooserThrew=false;Throwable chooserError=null;
                try{startActivity(Intent.createChooser(intent,DonateFlow.CHOOSER_TITLE));}
                catch(Throwable e){chooserThrew=true;chooserError=e;}
                try{DonateFlow.record(MainActivity.this,resolverCount,true,chooserError);}catch(Throwable ignored){}
                return DonateFlow.decide(resolverCount>0,chooserThrew)==DonateFlow.Gate.FIRE?"ok":"no-app";
            }catch(Throwable e){try{DonateFlow.record(MainActivity.this,0,false,e);}catch(Throwable ignored){}return "no-app";}
        });}
        @JavascriptInterface public void copyUpiId(){runOnUiThread(()->{try{
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("UPI ID",DONATE_UPI_ID));
            Toast.makeText(MainActivity.this,"UPI ID copied.",Toast.LENGTH_SHORT).show();
        }catch(Throwable ignored){}});}
        @JavascriptInterface public String alarmTones(){return onMain(()->{
            // 1.9.2: full system alarm list again (1.9.1's MediaStore-only list
            // dropped most tones on Samsung, leaving only Default + no Show more).
            // RingtoneManager first (system order), then MediaStore IS_ALARM
            // tones not already seen by title. Every cursor access is per-row
            // guarded and closed in finally, so a stale row can never kill the
            // list or crash the app — it is simply skipped.
            JSONArray out=new JSONArray();java.util.Set<String> seenTitles=new java.util.HashSet<>();java.util.Set<String> seenUris=new java.util.HashSet<>();
            try{
                // 2.0.3: NEVER use the Activity as the RingtoneManager context.
                // With an Activity, RingtoneManager registers its cursor as a
                // managed cursor, and our finally-close() then leaves a closed
                // managed cursor behind. When the activity restarts — exactly
                // what happens returning from the audio/restore picker, or
                // from system settings — Activity.performRestart() re-queries
                // every managed cursor and throws StaleDataException on the
                // closed one, killing the app with no chance to catch it
                // (it happens in system lifecycle code, not in our code).
                // That is the 2026-11-03T01:02:11 crash, and it is why BOTH
                // pick-audio and restore crashed while Settings alone did not.
                // With the application context the cursor is unmanaged, so
                // closing it in finally is safe and performRestart never
                // touches it. MediaStore cursors below were never managed,
                // so closing them was always correct.
                RingtoneManager rm=new RingtoneManager(getApplicationContext());rm.setType(RingtoneManager.TYPE_ALARM);Cursor cur=null;
                try{
                    cur=rm.getCursor();
                    if(cur!=null){
                        int titleIdx=-1;try{titleIdx=cur.getColumnIndex("title");}catch(Throwable ignored){}
                        int count=0;
                        while(count<200){
                            boolean moved=false;try{moved=!cur.isClosed()&&cur.moveToNext();}catch(Throwable ignored){break;}
                            if(!moved)break;
                            try{
                                Uri uri=null;try{uri=rm.getRingtoneUri(cur.getPosition());}catch(Throwable ignored){}
                                String title=null;try{title=titleIdx>=0?cur.getString(titleIdx):null;}catch(Throwable ignored){}
                                if(uri!=null&&seenUris.add(uri.toString())){
                                    String name=title!=null&&!title.isEmpty()?title:uri.getLastPathSegment();
                                    if(name!=null)seenTitles.add(name.toLowerCase());
                                    out.put(new JSONObject().put("name",name).put("uri",uri.toString()));
                                }
                            }catch(Throwable ignored){}
                            count++;
                        }
                    }
                }catch(Throwable ignored){}
                finally{try{if(cur!=null)cur.close();}catch(Throwable ignored){}}
            }catch(Throwable ignored){}
            Uri[] bases={MediaStore.Audio.Media.INTERNAL_CONTENT_URI,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI};
            String[] proj={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE};
            for(Uri base:bases){
                Cursor cur=null;
                try{
                    cur=getContentResolver().query(base,proj,MediaStore.Audio.Media.IS_ALARM+"=1",null,MediaStore.Audio.Media.TITLE+" ASC");
                    if(cur==null)continue;
                    int idIdx=cur.getColumnIndex(MediaStore.Audio.Media._ID);int titleIdx=cur.getColumnIndex(MediaStore.Audio.Media.TITLE);
                    while(out.length()<200){
                        boolean moved=false;try{moved=cur.moveToNext();}catch(Throwable ignored){break;}
                        if(!moved)break;
                        try{
                            long id=idIdx>=0?cur.getLong(idIdx):-1;if(id<0)continue;
                            Uri uri=ContentUris.withAppendedId(base,id);if(!seenUris.add(uri.toString()))continue;
                            String title=null;try{title=titleIdx>=0?cur.getString(titleIdx):null;}catch(Throwable ignored){}
                            String name=title!=null&&!title.isEmpty()?title:uri.getLastPathSegment();
                            if(name!=null&&!seenTitles.add(name.toLowerCase()))continue;
                            out.put(new JSONObject().put("name",name).put("uri",uri.toString()));
                        }catch(Throwable ignored){}
                    }
                }catch(Throwable ignored){}
                finally{try{if(cur!=null)cur.close();}catch(Throwable ignored){}}
            }
            return out.toString();
        });}
        @JavascriptInterface public boolean previewTone(String uri){return onMainBool(()->TonePreview.preview(MainActivity.this,uri));}
        @JavascriptInterface public void stopPreview(){runOnUiThread(()->{try{TonePreview.stop();}catch(Throwable ignored){}});}
        @JavascriptInterface public String crashReport(){return onMain(()->CrashReports.describe(MainActivity.this));}
        @JavascriptInterface public void pickAudio(){runOnUiThread(()->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),10);}catch(Throwable e){try{Toast.makeText(MainActivity.this,"No file picker is available on this phone.",Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}}});}
        @JavascriptInterface public void restoreBackup(){runOnUiThread(()->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),12);}catch(Throwable e){try{Toast.makeText(MainActivity.this,"No file picker is available on this phone.",Toast.LENGTH_SHORT).show();}catch(Throwable ignored){}}});}
        @JavascriptInterface public void stopRing(){runOnUiThread(()->{try{AlarmPlayer.stop();}catch(Throwable ignored){}try{RingService.stop(MainActivity.this,true);}catch(Throwable ignored){}});}
    }
}
