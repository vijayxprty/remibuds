package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.util.concurrent.atomic.AtomicBoolean;

public class CrashReportsTest {
    static void deleteRecursively(File f){
        if(f==null||!f.exists())return;
        if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)deleteRecursively(k);}
        f.delete();
    }
    File tmpDir(String tag){File d=new File(System.getProperty("java.io.tmpdir"),"remibuds-crash-"+tag+"-"+System.nanoTime());d.mkdirs();return d;}

    @Test public void writeReportKeepsLastThreeAndFirstLineNamesTheError()throws Exception{
        File dir=tmpDir("prune");
        try{
            for(int i=0;i<5;i++){
                CrashReports.writeReport(dir,Thread.currentThread(),new IllegalStateException("boom-"+i),1000L+i);
            }
            File[] files=dir.listFiles((d,n)->n.startsWith("crash-"));
            assertEquals(3,files.length);
            File latest=CrashReports.latest(dir);
            assertNotNull(latest);
            assertTrue(latest.getName().contains("1004"));
            String body=CrashReports.readText(latest,10000);
            assertTrue(body.contains("boom-4"));
            assertTrue(body.contains("IllegalStateException"));
            assertTrue(CrashReports.firstLine(latest).contains("boom-4"));
            assertTrue(CrashReports.firstLine(latest).contains("IllegalStateException"));
        }finally{deleteRecursively(dir);}
    }

    @Test public void readTextCapsAndHandlesMissingFiles(){
        assertEquals("",CrashReports.readText(null,100));
        assertEquals("",CrashReports.readText(new File("/nonexistent-dir-xyz/crash-1.txt"),100));
        assertNull(CrashReports.latest(new File("/nonexistent-dir-xyz")));
    }

    @Test public void uncaughtHandlerWritesReportAndChainsToPrevious()throws Exception{
        File dir=tmpDir("chain");
        Thread.UncaughtExceptionHandler original=Thread.getDefaultUncaughtExceptionHandler();
        AtomicBoolean chained=new AtomicBoolean(false);
        Thread.setDefaultUncaughtExceptionHandler((t,e)->chained.set(true));
        CrashReports.resetForTests();
        try{
            CrashReports.installIn(dir);
            Thread worker=new Thread(()->{throw new IllegalStateException("boom-test");});
            worker.start();worker.join();
            File latest=CrashReports.latest(dir);
            assertNotNull("a report must be written for the crashing thread",latest);
            String body=CrashReports.readText(latest,10000);
            assertTrue(body.contains("boom-test"));
            assertTrue(body.contains("IllegalStateException"));
            assertTrue(chained.get());
        }finally{
            Thread.setDefaultUncaughtExceptionHandler(original);
            CrashReports.resetForTests();
            deleteRecursively(dir);
        }
    }

    @Test public void writeReportNeverThrowsOnBadDirectory(){
        // A null directory or an unwritable target must not throw out of the handler path.
        try{CrashReports.writeReport(null,Thread.currentThread(),new RuntimeException("x"),1L);}
        catch(Throwable t){fail("writeReport must swallow its own failures: "+t);}
    }
}
