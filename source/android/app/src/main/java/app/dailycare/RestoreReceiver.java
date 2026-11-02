package app.dailycare;
import android.content.*;
public class RestoreReceiver extends BroadcastReceiver {public void onReceive(Context c,Intent i){try{CrashReports.install(c);}catch(Throwable ignored){}try{Reminders.restore(c);}catch(Throwable ignored){}try{EarlyAlerts.reschedule(c);}catch(Throwable ignored){}}}
