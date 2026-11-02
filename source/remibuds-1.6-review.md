# Remibuds 1.6 — review

Version name 1.6, version code 7, package `app.dailycare`. Built from the shipped Remibuds 1.5 source (`~/workspace/remibuds-1.5-build/remibuds-1.5-source.zip`, top dir `remibuds-source/`), copied to `~/workspace/remibuds-1.6-build/`. Storage keys, schema, package and all 1.5 behaviour are unchanged except where an item below says otherwise. No payment-gateway work. No 16px spacing rule. Fresh debug signature; uninstall the previous app first.

## What the user reported on 1.5 (29 Oct 2026, 00:58 IST, 6 screenshots)

1. Donate chooser now works — he likes it. (No change needed; the 1.5 donate gate is untouched.)
2. Ringing is backwards: the notification posts silently; ringing starts only after he taps into the app, then loops until he marks/skips. Required: ring starts immediately at the set time, with the reminder on screen offering mark/snooze/skip.
3. The app still stops, sometimes in use, sometimes on return from Settings/other apps ("Remibuds keeps stopping").
4. Permission confusion: every permission screen the app links to shows Allowed, yet Settings shows a ring warning about a blocked phone permission.

## Diagnosis (re-verified in the 1.5 source before changing anything)

- Item 2 is caused by the full-screen-intent permission genuinely being blocked (his screenshot 3: Settings → "Full-screen display: Blocked", via `NotificationManager.canUseFullScreenIntent()` on API 34+, which is the correct check). With it blocked, the full-screen intent is ignored at fire time: the notification posts, the activity never launches, `maybeStartRinging` never runs. Tapping launches the activity and only then does it ring. Exactly his report.
- Item 4 is our bug: `settings("fullscreen")` opened `ACTION_APPLICATION_DETAILS_SETTINGS` (generic App info). The user lands on pages where everything looks allowed while the one switch stays off. The correct target on API 34+ is `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` with `EXTRA_APP_PACKAGE`.
- Item 3 has no logcat. The 1.4/1.5 crash capture is the only evidence channel; it is kept, and the return-to-app paths were re-audited (see "Crash audit" below).

## Item A — alarm rings at the set time (P0)

New files:

- `android/app/src/main/java/app/dailycare/RingService.java` (line 21) — foreground service. `NOTIF_ID` 1001 (line 23), 10-minute cap `CAP_MS` (line 24). `start(Context, JSONObject)` (line 45): refuses to start when the activity already owns the sound (falls back to posting the notification directly and returns true, so the reminder is always visible); starts via `startForegroundService`/`startService` inside try/catch and records any refusal via `Reminders.recordRingServiceFailure`. `stop(Context, boolean keep)` (line 71) and `stopIfShowing` (line 81). `onStartCommand` (line 107): builds the notification through the shared `Reminders.buildNotification`, calls `startForeground` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` on API 29+ (line 117, 2-arg form below), falls back to a direct notify + recorded failure + `stopSelf` if foregrounding is refused; plays through `RingSound` guarded by `shouldPlay`; arms the 10-minute cap. `onDestroy` (line 132): cancels the cap, stops the service-owned sound, detaches foreground (`DETACH` when the stopper asked to keep the notification, `REMOVE` otherwise). `START_NOT_STICKY`; `onBind` returns null.
- `android/app/src/main/java/app/dailycare/RingSound.java` (line 24) — the single looping alarm-sound engine both owners share. Owner tracking `OWNER_NONE/ACTIVITY/SERVICE` (lines 26–28). `shouldPlay` (line 46): the activity always takes over; the service starts only when nothing plays or it already owns the sound — this is the never-two-sounds guard. `play` (line 58): stops any previous sound first, `prepareAsync` with a default-tone fallback at set-data-source time and one retry with a fresh player on the default tone at prepare-error time (`shouldRetry`, line 38, same contract as the old `AlarmPlayer`). `stop`/`stopIfOwned` (lines 88–94). Vibration via `Vibrator` with the same pattern as before.
- `android/app/src/main/java/app/dailycare/AlarmPlayer.java` (rewritten, line 11) — now a thin delegate to `RingSound` keeping the 1.3–1.5 API. `start` (line 24) stops the service first with `keep=true` (the notification stays for the activity to own), then plays as `OWNER_ACTIVITY`. The ordering hazard was analysed: `RingService.stop` is asynchronous, but its later `onDestroy` calls `stopIfOwned(OWNER_SERVICE)`, which is a no-op once ownership moved to the activity — the new sound survives.

Edited files:

- `Reminders.java`: `recordRingServiceFailure`/`getRingServiceError`/`getRingServiceErrorAt` (line 42) persist `diagRingServiceError`/`diagRingServiceAt` in the existing `PREF` store. `actionRequestCode` (line 61) derives distinct request codes per key+action (tap code unchanged). `actionIntent` (line 68) carries `careKey` + `careAuto` ("record"/"snooze") and NO `careRing` — the service keeps ringing until the action is applied and the page stops it. `deleteIntent` (line 75) broadcasts `app.dailycare.RING_STOP`. `buildNotification` (line 83) is the single shared builder: tap + full-screen intent + category/autoCancel/visibility/when as before; ring mode adds the "Mark taken"/"Snooze" actions (lines 91–94) and the delete intent (line 95). `show` (line 205): in ring mode it starts `RingService` first (line 217) and only posts directly when the service did not take ownership (line 218) — one notification, never two. `cancelResolved` (line 172): for each resolved entry it also calls `RingService.stopIfShowing` (line 180), so a resolved reminder's ringing stops with its notification.
- `ReminderReceiver.java` (line 10): a `RING_STOP` broadcast stops the service and returns; everything else follows the existing guarded `Reminders.receive` path.
- `MainActivity.java`: `stopRing` also stops the service with `keep=true`; `launchReport` (line 202) adds `ringServiceError`/`ringServiceAt`.
- `AndroidManifest.xml` (lines 7–8, 24): `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions; `<service android:name=".RingService" android:exported="false" android:foregroundServiceType="mediaPlayback"/>`.
- `web/core.js`: `resolveCareAuto` (line 173), pure: a key matching one occurrence applies to it; a key matching a shared reminder's card (`groupId|date|time`) reports `{group:true}` and applies to nothing (members stay explicitly selected, per the original brief); anything else returns null and nothing may be applied.
- `web/app.js`: `applyCareAuto` (line 57) — record goes through the single existing `log()` path; snooze reuses the user's snooze setting and the existing snooze code path with its toast. `careOpenNotification` (line 65) now also reads `launchAuto()`, validates it to "record"/"snooze"/"", applies via `applyCareAuto` only when the card is found and focused (wrapped so `keyHandled()` still runs even if applying throws), and when the card is not found clears only the action (`autoHandled()`) with a toast saying nothing was recorded. The plain tap path is unchanged (opens the card focused, no auto-apply). Double-apply across re-renders is guarded by the existing `openingNotification` flag plus `keyHandled` clearing both key and auto, and by `record()`'s active-entry dedupe. The About launch-diagnostics block renders the ring-service failure line when present (line 39).
- `android/app/build.gradle`: versionCode 6 → 7, versionName '1.5' → '1.6'. `web/app.js` line 4: `VERSION='1.6',CODE=7`.

Design decisions recorded during the build:

- Single-notification design: in ring mode the service owns the reminder notification as its foreground notification (fixed id 1001, untagged); `show()` does not also post the tagged notification. `cancelResolved` removes it via `stopIfShowing`. When the service stops with keep=true, `stopForeground(DETACH)` leaves the notification posted for the activity/action to own; keep=false removes it. Residual uncertainty: whether `stopForeground(DETACH)` called in `onDestroy` (the `stopService` path) reliably detaches on all builds — the explicit cap/STOP paths call it before `stopSelf`, which is deterministic; worst case the notification vanishes when the activity takes over, which is cosmetic since the activity is open.
- Starting a foreground service from an exact-alarm trigger is a documented exemption from the Android 12+ background foreground-service start restriction (Android developer documentation, confirmed via search 29 Oct 2026). The start happens synchronously in the receiver path with try/catch + fallback to a plain posted notification if refused, and failures are recorded and surfaced in Settings → About.
- The Android 17 while-in-use hardening for background audio does not apply: the app holds `SCHEDULE_EXACT_ALARM` and uses `USAGE_ALARM`, which is the documented waiver.

## Item B — fullscreen permission: the exact switch (P0)

- `MainActivity.java` line 161: `fullscreenSettingsAction(int sdkInt)` — pure helper, API 34+ → `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, below 34 → `ACTION_APPLICATION_DETAILS_SETTINGS`. `settings("fullscreen")` (line 172) uses it and always attaches `Settings.EXTRA_APP_PACKAGE` with the app package (harmless on the fallback path). The existing fallback chain (app details, then toast) is unchanged.
- `web/app.js` line 36: the ring warning copy now names the switch — "Turn on 'Full-screen notifications' for Remibuds on the screen that opens." The warning still shows only when ring mode is on AND fullscreen is blocked. `nativeStatus` fullscreen logic is unchanged (`canUseFullScreenIntent` on 34+, "allowed" below).

## Item C — crash, round 2 (P0)

Audit of every return-to-app path (lifecycle, WebView callbacks, file chooser, permission flows, all 1.5-new code). Real defects found and fixed:

1. `MainActivity.java` line 92 — `shouldInterceptRequest` was not guarded: an unexpected `Throwable` (e.g. a null from `req.getUrl()` on an exotic request, or an asset-open failure outside the inner try) propagated to the framework. Now wrapped: outer catch records via `CrashReports.record(..., "shouldInterceptRequest", ...)` and returns a 403 response. The inner 404 path is unchanged.
2. `MainActivity.java` lines 116–121 — `onActivityResult` left a stale `pendingBackup`/`pendingCsv` on every outcome where the guarded write did not run (cancelled picker, null data, non-OK result): the next backup/CSV action would have silently reused the stale payload. Now `pendingBackup`/`pendingCsv` (+ name) are cleared on every outcome for codes 8/11, after the write attempt.
3. `MainActivity.java` line 65–66, 106, 113–115 — `onDestroy`/`onPause`/`onPageFinished`/`onNewIntent`/`onResume`/`onRequestPermissionsResult` were not uniformly guarded; a throw from `AlarmPlayer.stop`, `TonePreview.stop`, `Reminders.restore` or `resumeWeb` on a return-to-app path could stop the app. All are now wrapped in `guard()` (line 38), which records via `CrashReports.record` instead of crashing. `super.*` calls stay outside the guard (framework calls that do not throw in practice).
4. `MainActivity.java` line 70 — `onCreate`'s tail (WebView setup, bridge registration) was wrapped in a hand-rolled try/catch that recorded only as "onCreate"; the structure is now the same `guard("onCreate", …)` used by the other entries, and `CrashReports.install` kept its call-site guard (line 69).
5. `MainActivity.java` line 100 — `shouldOverrideUrlLoading` could throw on a null `WebResourceRequest` or a null URL host chain; now null-safe throughout, records on the unexpected path, and returns `true` (stay in app) on error instead of crashing.
6. `MainActivity.java` line 81 — `onShowFileChooser` is hand-wrapped (not `guard()`, because it must return `true` and null the callback); re-checked: it records, nulls the callback on every failure path, and always returns `true`. No change needed — recorded here so the audit is complete.
7. `MainActivity.java` line 183–185 — 1.5-new bridge methods (`launchAuto`, `autoHandled`, `keyHandled`) run through `onMain` (already guarded, 5s timeout, FutureTask cancelled on timeout — line 136) or are trivial assignments; `keyHandled` now also clears `pendingAuto`. `captureLaunchIntent` (line 40) records `pendingAuto` via the pure `LaunchKey.captureAuto` (line 38), which ignores anything but "record"/"snooze" and keeps a pending action when the incoming extra is absent — a stale action can never fire on a later launch because `keyHandled`/`autoHandled` clear it and the page validates the key against the current day's occurrences.
8. `MainActivity.java` line 228 — `copyUpiId` body is wrapped in try/catch with a null `ClipboardManager` check (1.5 code, re-checked, no change).
9. `CrashReports.java` lines 53–62 — new `record(Context, String where, Throwable)` + `record(Context, Throwable)` writing the same crash-file format via a `writeReport` overload that prints a `where=` line; the existing 4-arg `writeReport` delegates to it. `describe()`/`firstLine()` are unaffected (they scan for the `error=` line). This is the recording channel the guards use.

What was NOT changed and why: `Reminders.receive`/`refresh`/`replace` were already guarded in 1.4/1.5 and re-checked; `DonateFlow.record` is already try/catch-guarded and re-checked; the `setOnApplyWindowInsetsListener` lambda is a one-line padding call on a non-null insets object by framework contract — left unwrapped as negligible risk.

What remains unknown without the user's problem report: no logcat exists for any of the crashes, so no single root cause is provable. The four defects above are real defects on return-to-app paths, but the actual crash on his phone could be any of them, a WebView/ROM-specific fault, or something else entirely. If 1.6 still stops, the "Last problem report" in Settings → About (View/Copy) is the evidence channel — ask him to copy it. The review states this plainly: this build hardens the paths and records the evidence; it does not claim the crash is fixed.

## Item D — kept

Donate chooser + diagnostics (1.5 gate, `<queries>`, single `DonateFlow`, no amount, payee pre-filled), tones UI (5-row collapsed list, Show more/less, per-row preview, custom audio below), archive/delete (history forever, delete never touches logs), CSV export, the 1.5 tap/`pendingKey`/`keyHandled` chain, and the 1.4 crash hardening are all unchanged. No gateway. No default/spacing/illustration/copy changes beyond Item B's warning wording.

## Tests

- JavaScript: `node --test tests/*.test.js` — 70/70 pass (64 carried, 6 new in `tests/careauto.test.js`: `resolveCareAuto` occurrence/group/unknown cases, apply-once + card-found guard wiring, warning copy, ring-service wiring). Re-run from the extracted shipped zip: 70/70.
- Native JVM (plain javac/java, JUnit 4.13.2, org.json jar before android.jar on the classpath): 73/73 pass (56 carried, 17 new: `RingSoundTest` 6 — shouldPlay matrix + retry contract + shared custom-file name; `RingServiceTest` 3 — 10-minute cap boundaries + distinct action request codes; `LaunchKeyAutoTest` 3 — captureAuto/handledAuto; `FullscreenSettingsTest` 2 — action strings for SDK 33 vs 34+; `CrashReportsRecordTest` 3 — record writes the where= line, the no-where overload omits it, record tolerates nulls).
- One test-side fix during the build: `LaunchKeyAutoTest.captureAutoKeepsKnownActionsOnly` first asserted a stale action wins over a newer valid one; the correct contract (matching `capture`) is that the newest valid action replaces the pending one — the test was corrected, no app code changed. One date-sensitive archive test in the 1.5 suite was already fixed in the 1.5 re-zip and stays fixed here.

## Verification (all six checks, run on the shipped APK)

1. `aapt2 dump badging`: package `app.dailycare`, versionCode 7, versionName 1.6, label Remibuds; permissions include `USE_FULL_SCREEN_INTENT`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
2. `apksigner verify`: v2 + v3 signatures valid.
3. sha256 compare of every `assets/web/*` entry against the shipped source: byte-identical.
4. `unzip -l`: `assets/web/*` present, zero root `web/*` entries, `classes.dex` at root.
5. grep over the shipped source + APK assets: zero user-visible "Daykind"/"Routibuds"/"leaf" (the invisible `daykind-qa-v2` QA storage key remains, as in 1.5).
6. Screenshots read back visually (Playwright/Chromium, stubbed bridge, seeded state): Today, Settings permission list, Settings ring-warning with the new copy (stubbed blocked-fullscreen + ring mode, stated on the shot).

## What remains unverified (real phone only)

- Ringing sounds at fire time over other apps and on the lock screen with the full-screen permission still blocked (the service path has never run on a real phone; the background-start exemption is documented, but Samsung/OneUI behaviour is unverified).
- Tap and notification actions land on the right card and apply exactly once; the fullscreen toggle is reachable via the new deep link.
- No more stops on return from Settings/other apps — only the user's problem report can prove or disprove this.
- Donate diagnostics if needed (chooser already confirmed working on 1.5; untouched here).
- The Daykind originals were not modified (hashes re-verified: APK `f0837ba85f03afc5dd924afbb1a9ceff8fc9bd6b346af53e76a4d8e3c0a62f57`, source zip `3db95f376a1f6bb0df0e9787ae60452e692693c3ccaaf830c220e36fba3343af`).
