package app.dailycare;
import android.content.*;
/** Executes without an activity or WebView; stale/duplicate intents are checked against native state.
 *  Also handles the ring notification's dismiss intent: swiping the reminder away stops
 *  the ring service (the notification is already gone). */
public class ReminderReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent intent){
        try{CrashReports.install(c);}catch(Throwable ignored){}
        try{
            if(intent!=null&&"app.dailycare.RING_STOP".equals(intent.getAction())){
                try{RingService.stop(c,false);}catch(Throwable ignored){}
                return;
            }
            Reminders.receive(c,intent==null?null:intent.getStringExtra("key"),System.currentTimeMillis());
        }catch(Throwable ignored){}
    }
}
