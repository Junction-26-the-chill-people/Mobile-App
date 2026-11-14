# Android integration guide

## Product contract

- This widget invites the user to a **daily quiz in the morning and evening**, default **10:00 and 21:00 in the phone's local time**. Times can be changed in the app.
- Show daily invitations only while the phone is **awake, unlocked and has stayed that way for at least 15 continuous seconds**. App-open detection is an optional extra gate; it requires owner-granted Usage Access and is off by default.
- **Screen-off pauses presentation; it must not dismiss, consume or reset a pending invitation.** Keep its ID, destination, payload, chosen copy and outside-tap stage. Restore it after the next readiness period.
- Invitation → first outside tap gives a gentle plea → second gives a final plea → third outside tap dismisses. The final plea has **no timer**. Yes/card/parcel accepts from all active message stages. Close and explicit Stop remain available.
- Keep the walk/carry/paw/open/rest animation and planted walking feet. It runs even when global Android animation scales are zero. Never change the phone's global animation settings.
- A nudge acceptance means navigation was requested, **not that the questionnaire was completed**. The future questionnaire owns answer persistence and completion.

## Ownership and file map

### Reusable widget: `catnudge/`

- `src/main/kotlin/com/junction/catnudge/NudgeModels.kt`: public request/copy/configuration/outcome models; default message pools.
- `CatNudge.kt`: public API for showing, durably enqueueing, monitoring, readiness options and dismissal.
- `NudgeStore.kt`: versioned durable queue, selected copy, payload, shown flag and outside-tap count.
- `CatNudgeService.kt`, `ScreenReadiness.kt`: one foreground host, screen/unlock readiness, optional app-open detection, and view pause/resume.
- `visual/NudgeOverlayView.kt`: interaction state machine and message transitions.
- `visual/CatSceneView.kt`: native kitten illustration and distance-based gait.
- `CatNudgeResumeActivity.kt`, `CatNudgeNotifications.kt`: user-initiated recovery and owner controls.

This module must not import `com.example.junction`, reference `MainActivity`, know quiz times, implement questionnaire pages, or access the database/network. Keep host policy in `:app`.

### Host integration: `app/`

- `JunctionApplication.kt`: installs `NudgeConfiguration`; register new destination keys here.
- `dailyquiz/DailyQuizSchedule.kt`: pure local-calendar slot calculation; defaults and DST behavior.
- `dailyquiz/DailyQuizScheduler.kt`: alarm scheduling, due-slot reconciliation, stable per-date/period IDs, host copy rotation and deduplication.
- `dailyquiz/DailyQuizReceiver.kt`: boot/package/time-zone/time-change/alarm entry points.
- `nudge/NudgeController.kt`, `nudge/NudgeTriggerActivity.kt`: local ADB/deep-link preview and permission bridge. Keep the existing ADB component compatible.
- `MainActivity.kt`, `ui/nudge/DailyQuizControls.kt`: owner setup, daily opt-in, time editing and optional Usage Access.
- `ui/nudge/NudgeHomeScreen.kt`: host home and reserved quiz screen. Replace the reserved content with the real questionnaire.

## Add a destination or questionnaire

1. Register a destination key in `JunctionApplication`'s `destinations` map. Return an explicit host-app `Intent` from `(context, request)`; the library launches it after acceptance while the overlay is still visible.
2. Call `CatNudge.show(activity, NudgeRequest(...))` from a visible Activity. Use a stable ID for a scheduled event. Set a destination key and string payload, not a hardcoded activity inside the library.
3. For a due event from background, use `CatNudge.enqueue(context, request)`. It persists and alerts an already-running host; it does **not** illegally start a foreground service from an ordinary alarm receiver. Notification recovery requires a user tap if the host is unavailable.
4. Daily requests use destination `daily_quiz`; payload keys are `period` (`morning`/`evening`), `date` (`yyyy-MM-dd`) and `scheduled_at` (epoch millis). `JunctionApplication` forwards these plus `nudge_id` into the current reserved screen. Put quiz navigation/content/data work in this host route.
5. Use `NudgeConfiguration.onOutcome` for host acceptance/dismissal events. Persist questionnaire answers/completion in the questionnaire feature, separately.

Custom copy is a `NudgeCopy(invitation, gentlePlea, finalPlea, acceptLabel)`. Choose it **once per request**, so a screen-off/restart never changes the message unexpectedly. Edit `NudgeMessages` to add default lines; edit `DailyQuizScheduler.nextCopy` only if host rotation policy must change. Do not add message conditionals to the renderer for individual features.

## Android lifecycle rules

- Owner enables monitoring from a resumed Activity after overlay permission. Monitoring uses a visible `specialUse` foreground-service notification. `stopMonitoring` disables monitoring; the host's disable callback cancels alarms and clears pending visits.
- `restoreIfNeeded` may start a host only from a visible Activity or a documented exemption such as `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`. Ordinary inexact alarms must enqueue/refresh only. An existing foreground service alone does not grant a background-start exemption for another service.
- Register `SCREEN_ON`, `SCREEN_OFF`, `USER_PRESENT` and minute ticks at runtime. On screen-off, remove only the view and cancel readiness timers; retain the request. Check awake + unlocked again immediately before showing.
- Alarms use one-shot **non-wakeup, inexact RTC** scheduling. Reconcile due slots on host startup/screen readiness/minute tick and after clock/time-zone changes; never require exact-alarm permission for this feature.
- Keep pending state through process recreation; queue IDs deduplicate retries. Owner force-stop and OEM battery policies can stop automatic recovery, so the setup screen/notification provide a resume path. Never silently grant permissions or disable battery protections.
- Optional Usage Access is queried only for app-resume eligibility while a request is pending and the screen is active. Do not save app names, usage history, screen contents, or accessibility events. The default screen-time path needs no Usage Access or accessibility service.

## Verification and repository hygiene

Run from the repository root with JDK 17+ and SDK 36:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :catnudge:testDebugUnitTest :app:testDebugUnitTest lintDebug
```

The first Robolectric run downloads Android runtimes. Tests include native rendering, gait ground contact, tap stages, configurable copy, durable queue/readiness, and local-time scheduling. Inspect rendered previews in `catnudge/build/preview` when changing the artwork or layout. Check portrait, landscape, small phones and large fonts.

On a phone, verify ADB entry, permission denial, 15-second readiness, screen-off at each message stage, final-plea persistence, Yes navigation, Stop, and service cleanup. Test with Android animator scale zero; Xiaomi may also require its separate background-window setting for opening a page.

Do not commit `local.properties`, signing material, API keys, device captures, APKs, `.gradle`, `.kotlin` or build output. Keep the library independent and docs short/current. Do not add a backend, database contract, questionnaire questions or analytics unless requested. Preserve existing user changes; no force pushes.
