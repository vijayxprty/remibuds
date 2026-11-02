package app.dailycare;
import org.junit.Test;import static org.junit.Assert.*;

public class DonateFlowTest {
    @Test public void payloadIsExactVerifiedStringWithPayeePrefilledAndNoAmount(){
        assertEquals("upi://pay?pa=jaynishad2010@okhdfcbank&pn=Vijay%20Kumar&aid=uGICAgIDQqveaLw",DonateFlow.PAYLOAD);
        assertEquals("jaynishad2010@okhdfcbank",DonateFlow.UPI_ID);
        assertTrue(DonateFlow.hasPayee(DonateFlow.PAYLOAD));
        assertFalse(DonateFlow.fixesAmount(DonateFlow.PAYLOAD));
    }
    @Test public void fixesAmountDetectsPlainAndEncodedAmounts(){
        assertTrue(DonateFlow.fixesAmount("upi://pay?pa=x@okhdfcbank&am=10"));
        assertTrue(DonateFlow.fixesAmount("upi://pay?pa=x@okhdfcbank&am%3D10"));
        assertTrue(DonateFlow.fixesAmount("upi://pay?pa=x@okhdfcbank&am"));
        assertFalse(DonateFlow.fixesAmount("upi://pay?pa=x@okhdfcbank&pn=Name"));
        assertFalse(DonateFlow.fixesAmount(null));
        assertFalse(DonateFlow.fixesAmount(""));
    }
    @Test public void chooserTitleNamesTheChoice(){
        assertEquals("Choose an app to pay with",DonateFlow.CHOOSER_TITLE);
    }
    @Test public void decideAlwaysAttemptsChooserOnlyAThrownAttemptMeansNoApp(){
        assertEquals(DonateFlow.Gate.FIRE,DonateFlow.decide(true,false));
        assertEquals(DonateFlow.Gate.FIRE,DonateFlow.decide(false,false));
        assertEquals(DonateFlow.Gate.NO_APP,DonateFlow.decide(false,true));
        assertEquals(DonateFlow.Gate.NO_APP,DonateFlow.decide(true,true));
    }
    @Test public void summarizeAttemptNamesCountAndOutcome(){
        assertEquals("Payment apps found: 3 · chooser opened",DonateFlow.summarizeAttempt(3,true,""));
        assertEquals("Payment apps found: 0 · chooser opened",DonateFlow.summarizeAttempt(0,true,null));
        assertEquals("Payment apps found: 0 · chooser was not attempted",DonateFlow.summarizeAttempt(0,false,""));
        assertEquals("Payment apps found: 2 · chooser failed: android.content.ActivityNotFoundException: No Activity found to handle Intent",DonateFlow.summarizeAttempt(2,true,"android.content.ActivityNotFoundException: No Activity found to handle Intent"));
        assertEquals("Payment apps found: 0 · chooser opened",DonateFlow.summarizeAttempt(-2,true,""));
    }
}
