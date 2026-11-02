package app.dailycare;

/** Pure launch-key transitions for the notification-tap handoff. The key must survive
 *  until the web page has actually found and focused the reminder card: reading it
 *  must never consume it, an empty incoming extra must never clear a pending key, and
 *  only an explicit "handled" from the page clears it. No Android types, so this is
 *  unit-tested with plain javac/java; MainActivity holds the fields and calls these. */
public final class LaunchKey {
    private LaunchKey() {}

    /** A new launch extra replaces the pending key only when it is non-empty; a missing
     *  or empty extra (ordinary launches, permission returns) keeps whatever is pending. */
    public static String capture(String current, String incoming) {
        if (incoming != null && !incoming.isEmpty()) return incoming;
        return current == null ? "" : current;
    }

    /** Reading the pending key never consumes it; null-safe. */
    public static String peek(String current) {
        return current == null ? "" : current;
    }

    /** The page calls this after it has focused the reminder card. */
    public static String handled() {
        return "";
    }

    /** True when an incoming launch key starts a new notification tap: a different key
     *  from the one already recorded. The same key (activity recreation, repeat tap)
     *  must not reset the "card reached" flag. Pure so it is unit-tested. */
    public static boolean isNewLaunch(String storedKey, String incomingKey) {
        return incomingKey != null && !incomingKey.isEmpty() && !incomingKey.equals(storedKey);
    }

    /** A notification-action request ("record"/"snooze") replaces the pending action
     *  only when it is a known value; anything else (plain taps carry no careAuto
     *  extra) keeps whatever is pending. Pure so it is unit-tested. */
    public static String captureAuto(String current, String incoming) {
        if ("record".equals(incoming) || "snooze".equals(incoming)) return incoming;
        return current == null ? "" : current;
    }

    /** The page calls this after it has decided a pending action cannot be applied
     *  (card not found): the action is cleared without clearing the pending key,
     *  so a later render can still focus the card but never apply a stale action. */
    public static String handledAuto() {
        return "";
    }

    /** True when the recorded app launch came from the recorded notification: the keys
     *  must match, so a normal launcher open never counts as opening via notification.
     *  Pure so it is unit-tested. */
    public static boolean matchesLaunch(String shownKey, String launchKey) {
        return shownKey != null && !shownKey.isEmpty() && shownKey.equals(launchKey);
    }
}
