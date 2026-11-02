package app.dailycare;

import android.content.Context;
import android.content.SharedPreferences;

/** The donate payment contract. The payload, chooser title, gate decision and attempt
 *  summary are pure and unit-tested without an Android runtime. Only {@link #record}
 *  touches Android: it stores the last attempt's outcome so a failed donate on a real
 *  phone is diagnosable from Settings → About instead of another guessing round.
 *  The UPI intent carries the payee pre-filled and NO amount parameter: the chosen UPI app
 *  asks the donor for the amount, then the UPI PIN. A payment-gateway checkout (cards /
 *  net-banking) is a different flow and is deliberately not part of this class. */
public final class DonateFlow {
    private DonateFlow() {}

    /** SharedPreferences file holding the last donate attempt. Diagnostics only. */
    static final String PREF = "donate_diag";

    /** Exact verified payload from the developer's own QR code. Do not add parameters. */
    public static final String PAYLOAD =
            "upi://pay?pa=jaynishad2010@okhdfcbank&pn=Vijay%20Kumar&aid=uGICAgIDQqveaLw";

    public static final String UPI_ID = "jaynishad2010@okhdfcbank";

    /** Chooser title shown above every installed payment app. */
    public static final String CHOOSER_TITLE = "Choose an app to pay with";

    /** Outcome of the donate gate: fire the chooser, or fall back to the Copy UPI ID sheet. */
    public enum Gate { FIRE, NO_APP }

    /** Chooser policy, kept pure so it is unit-tested without an Android runtime.
     *  The chooser is always attempted: an empty resolver query must never veto it,
     *  because package visibility can hide handlers that are really installed. Only
     *  a thrown chooser attempt proves there is no app to pay with. {@code hasResolvers}
     *  is accepted so the decision is explicit at the call site; it never blocks the
     *  attempt — that is the whole point of the hardening. */
    public static Gate decide(boolean hasResolvers, boolean chooserThrew) {
        if (chooserThrew) return Gate.NO_APP;
        return Gate.FIRE;
    }

    /** Records one donate attempt for Settings → About diagnostics. Never throws.
     *  Stores the resolver count, whether the chooser was attempted, and the thrown
     *  exception's class/message (if any). No stack trace is stored. */
    public static void record(Context c, int resolverCount, boolean attempted, Throwable error) {
        try {
            SharedPreferences.Editor e = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit();
            e.putInt("resolvers", Math.max(0, resolverCount));
            e.putBoolean("attempted", attempted);
            e.putString("error", error == null ? "" : error.getClass().getName() + ": " + String.valueOf(error.getMessage()));
            e.putLong("at", System.currentTimeMillis());
            e.apply();
        } catch (Throwable ignored) {}
    }

    /** One-line summary for Settings → About. Pure so it is unit-tested. */
    public static String summarizeAttempt(int resolverCount, boolean attempted, String error) {
        StringBuilder s = new StringBuilder("Payment apps found: ").append(Math.max(0, resolverCount));
        if (!attempted) s.append(" · chooser was not attempted");
        else if (error != null && !error.isEmpty()) s.append(" · chooser failed: ").append(error);
        else s.append(" · chooser opened");
        return s.toString();
    }

    /** True when the payload fixes an amount — it must never do so. */
    public static boolean fixesAmount(String payload) {
        if (payload == null) return false;
        String q = payload.contains("?") ? payload.substring(payload.indexOf('?') + 1) : payload;
        for (String part : q.split("&")) {
            if (part.equals("am") || part.startsWith("am=") || part.startsWith("am%3D")) return true;
        }
        return false;
    }

    /** True when the payload names the payee account to be pre-filled by the UPI app. */
    public static boolean hasPayee(String payload) {
        return payload != null && payload.contains("pa=" + UPI_ID);
    }
}
