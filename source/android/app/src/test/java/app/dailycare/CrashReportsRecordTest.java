package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;

public class CrashReportsRecordTest {
    static void deleteRecursively(File f){
        if(f==null||!f.exists())return;
        if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)deleteRecursively(k);}
        f.delete();
    }
    File tmpDir(String tag){File d=new File(System.getProperty("java.io.tmpdir"),"remibuds-record-"+tag+"-"+System.nanoTime());d.mkdirs();return d;}

    @Test public void recordWritesAReportWithTheWhereLine(){
        File dir=tmpDir("where");
        try{
            CrashReports.writeReport(dir,Thread.currentThread(),new IllegalStateException("guarded-boom"),2000L,"onResume");
            File latest=CrashReports.latest(dir);
            assertNotNull(latest);
            String body=CrashReports.readText(latest,10000);
            assertTrue(body.contains("where=onResume"));
            assertTrue(body.contains("guarded-boom"));
            assertTrue(CrashReports.firstLine(latest).contains("guarded-boom"));
        }finally{deleteRecursively(dir);}
    }

    @Test public void writeReportWithoutWhereOmitsTheLine(){
        File dir=tmpDir("nowhere");
        try{
            CrashReports.writeReport(dir,Thread.currentThread(),new RuntimeException("plain"),3000L);
            String body=CrashReports.readText(CrashReports.latest(dir),10000);
            assertFalse(body.contains("where="));
            assertTrue(body.contains("plain"));
        }finally{deleteRecursively(dir);}
    }

    @Test public void recordToleratesNullsAndNeverThrows(){
        CrashReports.record(null,"onResume",new RuntimeException("x"));
        CrashReports.record(null,null,null);
    }
}
