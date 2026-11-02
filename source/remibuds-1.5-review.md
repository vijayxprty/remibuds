# Remibuds 1.5 — review

Version name 1.5, version code 6, package `app.dailycare`, min SDK 26, target SDK 36. Fresh debug signature (uninstall the previous app first; no records migrate). Built with the manual aapt2/d8 route (Gradle daemon does not run in the Linux build VM). No logcat, emulator or Android phone was available in this environment, so everything below is a code audit plus unit tests — not a reproduction on a device. That limit is stated plainly wherever it matters. The user confirmed he tested 1.4 only, with Ring + notification on and all permissions allowed, so the three reported failures are treated as real 1.4 bugs, not a wrong-version or wrong-settings explanation.

## Why 1.4 failed, as far as code can show

1. **Tap did nothing.** The tap PendingIntent used request code 0 for every notification (`Reminders.show`), and the launch key was consumed on first read (`MainActivity.launchKey` removed the `careKey` extra). If the page was not ready to find the card at that one moment — cold start, WebView still loading, an event-driven render in between — the key was gone and no later render could retry. The tap then opened nothing useful and the notification auto-cancelled.
2. **No ringing.** Ringing depended on the `careRing` extra surviving to exactly one `maybeStartRinging` call, and on `AlarmPlayer` preparing the chosen tone: a tone that failed at prepare time (unreadable/corrupt file) hit an error listener that only released the player and stayed silent. There was also no in-app signal when ring mode was on but a needed permission was blocked.
3. **Donate fallback.** The gate was `resolveActivity() == null → "no-app"`. On Android 11+ package visibility can hide resolvers, and any `Throwable` anywhere in the bridge also returned `"no-app"` — so the fallback sheet appeared even with payment apps installed, with no record of why.

## Item 1 — notification tap opens the app and lands on the reminder; ringing actually sounds

### (a) Unique tap request codes

- `Reminders.java:37` — `tapRequestCode(key)` returns a stable non-negative hash of the key (`hashCode() & 0x7fffffff`, 0 for null). `Reminders.java:152` — `show()` passes it as the `PendingIntent.getActivity` request code, keeping the explicit `MainActivity` component, the `careKey` extra, and `SINGLE_TOP|CLEAR_TOP`. Distinct keys can no longer share one PendingIntent slot under `FLAG_UPDATE_CURRENT`.
- Test: `RemindersTapTest` (3 tests) — distinct keys get distinct codes, the same key always gets the same code, null maps to 0.

### (b) Launch-key delivery that cannot lose the key — end-to-end chain

1. `Reminders.java:148-152` — `show()` builds the tap intent with `care://open/<key>` data, `careKey` extra, and `careRing` when ring mode is on; the notification carries it as both content intent and full-screen intent.
2. `MainActivity.java:59` (`onCreate`) and `:91` (`onNewIntent`) — both funnel through `captureLaunchIntent` (`MainActivity.java:32-43`), which stores the key in the `pendingKey` field (`:31`) via `LaunchKey.capture` and sets the `pendingRing` flag. The intent extras are left in place as well; nothing consumes them on read.
3. `MainActivity.java:147` — bridge `launchKey()` returns `LaunchKey.peek(pendingKey)` **without consuming it**. `LaunchKey.java:19-21` — `peek` is null-safe and non-destructive. `LaunchKey.java:13-17` — `capture` replaces only on a non-empty incoming key, so ordinary launches and permission returns never clear a pending key.
4. `web/app.js:57` — `careOpenNotification` reads the key, splits it, sets the date/page, re-renders only if needed, then searches `[data-occurrence]` for the full key, scrolls it into view, focuses `.card-main`, and only then calls `keyHandled()`. `MainActivity.java:148` — `keyHandled()` clears `pendingKey` and marks the launch delivered for diagnostics. `LaunchKey.java:24-26` — `handled()` returns empty.
5. Retry: `web/app.js:30` — `render()` calls `careOpenNotification()` after every render (guarded by `typeof` and by the `openingNotification` re-entry flag declared at `app.js:7`), so a tap that arrives before the page is ready is retried on every event-driven render until the card is found and focused. No timers or loops are used. The guard prevents the `render()`-inside-`careOpenNotification` recursion: the inner render's hook returns early while the outer call is in flight, and the outer call then searches the freshly built DOM.
6. Cases: **cold start** — `onCreate` captures the key before the WebView loads; the first script evaluation runs `render()` then `careOpenNotification()` (`app.js:58`), which focuses the card. **Warm resume** — `onNewIntent` captures, starts ringing, and evaluates `careOpenNotification()`; if the page is mid-render, the render hook retries. **Already foreground** — same `onNewIntent` path; the key survives because nothing consumed it.
7. Ringing entry points: `maybeStartRinging` (`MainActivity.java:44-55`) reads the `pendingRing` flag plus the intent extra, consumes both, and starts `AlarmPlayer` once when ring mode is on. It is called from `onCreate` (`:59`), `onNewIntent` (`:91`) and `onResume` (`:92`) — idempotent, so whichever runs first rings exactly once and later calls are no-ops.
8. Tests: `LaunchKeyTest` (6 tests) — capture keeps/replaces/ignores-empty, peek never consumes, handled clears, `isNewLaunch` and `matchesLaunch` rules. JS `ring-warning.test.js` asserts the `keyHandled` wiring, the re-entry guard, and the render hook by source inspection.

### (c) AlarmPlayer prepare-error retry

- `AlarmPlayer.java:31-33` — pure `shouldRetry(triedDefault, alreadyRetried)`: retry only when the failed source was not already the default tone and this is not already the retry.
- `AlarmPlayer.java:42-77` — `start()` delegates to `play(state, isRetry=false)`; `play()` resolves the tone exactly as before (custom file → chosen URI → default fallback at set-data-source time, `triedDefault` tracked). `prepareAsync()` with `OnPreparedListener` and `OnErrorListener` is kept, plus stop-race safety. On prepare error (`:66-73`) the player is released and, when `shouldRetry` says so, `play(state, true)` runs once with a fresh player forced to `"default"`; the retry's own error listener cannot retry again (`isRetry=true`). If the default also fails, ringing stays silent — never a crash.
- Test: `AlarmPlayerRetryTest` (3 tests) — retry matrix for all four combinations.

### (d) Ring-readiness warning

- `web/core.js:167` — pure `ringBlockReason(st)`: first blocking permission in fix order (`notifications` → `exact` → `fullscreen`), null when ringing can sound.
- `web/app.js:36` — `soundCard(st)` (now takes the status object; called as `soundCard(st)` from `settings()`) renders a prominent `.notice.ring-warning` block when mode is `ring` and a reason exists, in plain words per reason, with an "Open phone settings" button carrying `data-phone="<reason>"`, handled by the existing generic settings opener in `bind()`. The default mode stays "Notification only". `web/style.css` adds `.ring-warning` / `.diag-report` rules; no spacing, illustration or copy changes elsewhere.
- Test: `ring-warning.test.js` — all `ringBlockReason` cases including first-failure-wins, plus the warning markup/button wiring.

### (e) No regressions

The 1.4 crash capture (`CrashReports`, `Throwable` guards at every entry point), the `<queries>` manifest fix (re-verified present in the compiled binary manifest of this build), the tones UI, archive/delete and CSV export are untouched except for the additions above. All 60 carried JS tests and all 42 carried JVM tests still pass.

## Item 2 — donate chooser must appear

- `MainActivity.java:174-188` — `donate()` now gates on `queryIntentActivities(..., MATCH_DEFAULT_ONLY)` (count recorded), then **always attempts** `startActivity(Intent.createChooser(intent, DonateFlow.CHOOSER_TITLE))` inside try/catch. Only a thrown attempt returns `"no-app"`. No `setPackage`, the exact `DonateFlow.PAYLOAD` verbatim (no amount parameter, payee pre-filled), the same chooser title, the same `<queries>` declarations, and the same no-app fallback sheet + Copy UPI ID.
- `DonateFlow.java:37-40` — pure `decide(hasResolvers, chooserThrew)`: `NO_APP` only when the attempt threw; `FIRE` otherwise, including resolvers-empty-but-chooser-succeeded. `hasResolvers` is accepted so the decision is explicit; it deliberately never vetoes the attempt.
- Entry points verified by source inspection: header heart (`#donate-top`), donate rows (`[data-donate]` on all tabs), QR tap and "Click to pay" (both `data-donate` in `donateSection`) all call the single JS `donate()` (`web/app.js:20`), which calls the single native `donate()` bridge and falls back only on `"no-app"`. The Copy buttons (`#copy-upi`, `#copy-upi-settings`) are explicit copy actions, not donate paths.
- Tests: `DonateFlowTest` gains `decide` (4 combinations) and `summarizeAttempt` (5 cases) tests; the existing payload/chooser-title/no-`setPackage`/`<queries>` tests still pass.

## Diagnostics (added per the confirmed 1.4-only report)

### Donate diagnosis

- `DonateFlow.java:17` — prefs file `donate_diag`. `DonateFlow.java:45-55` — `record()` stores resolver count, whether the chooser was attempted, and the thrown exception's class/message (never a stack trace), plus a timestamp; it never throws. `MainActivity.java:185,187` — recorded on every attempt, including the outer-catch path (`attempted=false`).
- `DonateFlow.java:57-64` — pure `summarizeAttempt()` builds the one-line summary. `MainActivity.java:149-157` — bridge `donateReport()` returns the stored record as JSON with the summary.
- `web/app.js:39` — `settings()` reads it; the About card shows a "Last donate check" block (`Payment apps found: N` + outcome) with View/Copy buttons reusing the crash-report modal/clipboard pattern (`bind()` handlers in `app.js:56`). Bodies are kept in `lastDonateBody` (`app.js:7`); nothing diagnostic appears on the main screens.

### Reminder-launch diagnosis

- `Reminders.java:40-45` — `recordShown()` stores the posted notification's key, timestamp and ring flag in the `reminders` prefs, called from `show()` at `:156` right after `notify()`.
- `Reminders.java:47-58` — `recordNotificationLaunch()` stores the launch key/time/ring flag; `LaunchKey.isNewLaunch` (`LaunchKey.java:31-35`) means a repeat launch with the same key never resets the delivered flag. Called from `captureLaunchIntent` (`MainActivity.java:42`), i.e. from both `onCreate` and `onNewIntent`.
- `Reminders.java:59-64` — `markLaunchDelivered()` flips the flag only when a launch was recorded; called from `keyHandled()` (`MainActivity.java:148`). If `keyHandled` is never called after a launch, the flag stays false and the About line reports the card was NOT reached.
- `MainActivity.java:158-167` — bridge `launchReport()` returns the shown record, the launch record, `openedViaNotification` (`LaunchKey.matchesLaunch`, `LaunchKey.java:38-40`: the launch key must equal the shown key, so a normal launcher open never counts) and `cardReached` (opened AND delivered).
- `web/app.js:38` — `launchLine()` formats the line: notification posted at date/time (+ ringing), app opened via notification yes/no, reminder card reached yes/no. The About card shows a "Last reminder launch" block with View/Copy, same pattern as above.
- Tests: `LaunchKeyTest` covers `isNewLaunch`/`matchesLaunch`; `ring-warning.test.js` asserts the recording calls and both bridges by source inspection. The prefs writes themselves are thin `apply()` calls and are not unit-tested (no Android runtime here); their content is surfaced verbatim in the diagnostics UI for the real-phone test to confirm.

## Files changed

- New: `android/.../LaunchKey.java`; `tests/ring-warning.test.js`; JVM `LaunchKeyTest.java`, `RemindersTapTest.java`, `AlarmPlayerRetryTest.java`; this review doc.
- Edited: `MainActivity.java` (pending-key fields, capture, idempotent ringing, `onResume`, `launchKey`/`keyHandled`, donate gate + recording, two diagnostic bridges), `Reminders.java` (tap request code, three diagnostic recorders, `recordShown` call), `AlarmPlayer.java` (retry-once restructure), `DonateFlow.java` (gate decision, recording, summary), `Plan.java` (unchanged), `AndroidManifest.xml` (unchanged), `build.gradle` (versionCode 6 / versionName 1.5), `core.js` (`ringBlockReason`), `app.js` (VERSION/CODE, notification-open retry, ring warning, diagnostics UI), `style.css` (warning/diagnostic rules), `README.md`, `tests/art.test.js` (version identity), `tests/donate.test.js` (unchanged, still passing), `DonateFlowTest.java` (decide + summary tests).

## Tests — run, not faked

- JS: `node --test tests/*.test.js` — **64/64 pass** (60 carried from 1.4, 4 new in `ring-warning.test.js`), run in the 1.5 tree before building.
- JVM: plain `javac`/`java` against JUnit 4.13.2 + org.json (Gradle daemon does not run here; the real org.json jar must precede `android.jar` on the classpath or the platform stubs shadow it) — **56/56 pass** (42 carried from 1.4; 14 new: LaunchKeyTest 6, RemindersTapTest 3, AlarmPlayerRetryTest 3, DonateFlowTest 2). Re-run from the extracted shipped zip before delivery (see below).
- Two harness notes: the first JVM run failed 29 tests with `Stub!` because `android.jar` preceded the org.json jar; reordering fixed it with no app-code change. No test bugs were fixed by changing app code.

## Still unverified — real phone only

No Android phone or emulator run was performed here. The decisive checks are the user's: (1) tap a reminder notification — the app must open on that reminder's card, able to Mark taken, from cold start, warm resume and foreground; (2) ring mode must sound audibly over other apps and on the lock screen, including with a corrupt/unreadable custom tone (falls back to the default tone); (3) donate must show the chooser listing installed UPI apps with the payee pre-filled. If any of them still fails, Settings → About now carries the decisive record: "Last donate check" (payment apps found + any chooser error), "Last reminder launch" (posted/opened/card-reached), and "Last problem report" — copy all three and send them. The 1.4 crash fix likewise still awaits a real-phone verdict across permission flows.
