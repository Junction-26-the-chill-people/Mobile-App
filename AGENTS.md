# Native Sola frontend — current work

The native Kotlin/Compose frontend is connected to the cat widget. `daily_quiz` and the existing ADB component open the real `CHECK_IN` screen in the personal journal. The user enables visits beside the rhythm controls in onboarding or Settings. Keep scheduled delivery, overlay acceptance and successful mood saving separate.

## Feature map

- `app/.../sola/SolaApp.kt`: feature root, saved navigation, bottom tabs, native photo/speech launchers, Telegram confirmations and foreground polling.
- `sola/data/SolaModels.kt`: screens, feature models, mood choices, idea pools, local-date helpers and explicit sample data.
- `sola/data/SolaStore.kt`: versioned private local JSON, durable drafts and separate personal/sample records. New fields need encode/decode defaults and a round-trip test. Debug previews must remain ephemeral.
- `sola/data/SolaMedia.kt`: imports only a selected photo into private files, normalizes orientation and removes EXIF. Never persist a temporary picker URI as the only copy.
- `sola/data/TelegramGateway.kt`: supplied Telegram server contract; confirmed delivery only, credentials never in Android code. HTTPS in release, localhost HTTP in debug only.
- `sola/ui/SolaComponents.kt`: typography, ambient backgrounds, frosted cards, icons and shared controls. Keep real system insets; do not copy the source’s desktop workbench or fake phone frame.
- `sola/ui/OnboardingScreens.kt`: welcome, local profile, interests, intentions, future source preferences, rhythm and trusted friend. `ReminderControls.kt` shares owner opt-in/permission handling with Settings; sample and debug previews cannot change real monitoring.
- `sola/ui/DailyScreens.kt`: Today, ideas, mood meter/scale, diary and garden interactions.
- `sola/ui/GardenArtwork.kt` + `assets/sola/branches.json`: original progressive tree geometry rendered on Canvas; one memory node per local day, selectable day memories and flowers. Keep drawing and feature state separate.
- `sola/ui/InsightsScreens.kt`: descriptive statistics, support/dialer and six recap stories. `SettingsScreen.kt`: owner preferences, people and connection setup.
- `telegram-bridge/bridge.mjs`: localhost single-owner Node bridge. `.env` and `.data/` are ignored. Its tests inject a fake Telegram transport and must never send real messages.

## Extend the frontend

Add a route to `SolaScreen` and the root `when` in `SolaApp`. Keep page content in a screen composable and data in `SolaStore` models; pass actions rather than reaching into the cat library or hardcoding Activity navigation in artwork. Preserve full diary text and several check-ins per day while growing only one daily branch. Mark sample data and keep it isolated from the personal journal. Calls must open `ACTION_DIAL`; speech must use actual recognition or report unavailability. Do not claim a watch, clinic, account, Gemini service or Telegram delivery is connected when it is not.

Gemini text summaries/reflections, account authentication, clinician sharing, device/music APIs are deliberately reserved. Rhythm controls drive real cat visits after owner opt-in. No shared database has been integrated. Before adding one, follow the parent `E:\Junction26\AGENTS.md` and read/check the current DB-Data schema contract.

`DailyQuizSchedule.slotsForRhythm` honors cadence and all three saved windows. Additional counts repeat hourly from the chosen time without crossing midnight; overlapping times merge. New profiles default to 10:00/21:00; existing stored times remain intact. Every-two-days uses a durable calendar-date anchor. Initial enable and timing edits affect future slots, retain already-pending visits and never retroactively create all of today’s earlier visits.

`SolaStore.openCheckIn` persists an accepted session. `saveMood` writes the mood plus its original nudge ID, period/date/scheduled time and clears the active session. The host uses `DailyQuizScheduler.logMood` through the injected `onSaveMood` action. A direct in-app log can satisfy the latest due slot, cancelling only that invitation through `CatNudge.dismiss(context, id)`. Journal instances observe private preference changes and refresh before updates, so a warm Activity launch cannot overwrite a pending session with stale data. The log timestamp is when the person actually answered. Re-delivery of a completed ID does not create another entry. Unfinished sessions reopen after process recreation; real nudges leave sample mode and never write into sample data. `CheckInRoute.kt` parses host extras; MainActivity handles cold and warm intents. The library’s optional `canPresent` callback defers queued overlays while the native check-in screen is resumed; it never consumes the request or resets tap stages.

## Verify

Run the root README checks. `SolaStoreTest` covers persistence, sample isolation, daily grouping, drafts and release URL rules. `SolaUiTest` exercises real flows and renders every native screen to `app/build/sola-preview/`. `MainScreenTest` tests the actual Activity on a phone using debug-only ephemeral preview extras; it must not clear or edit a real journal. Set `preview_motion=false` for deterministic tests; custom artwork still animates with Android animator scale zero. Check small screens, scrollable forms, large fonts, keyboard/insets, lock/relaunch and photos before changing layouts. Keep the existing cat module and its tests passing.

---
# Android integration guide

## Product contract

- This widget invites the user to a **daily quiz in the morning and evening**, default **10:00 and 21:00 in the phone's local time**. Times can be changed in the app.
- Show daily invitations only while the phone is **awake, unlocked and has stayed that way for at least 15 continuous seconds**. App-open detection is an optional extra gate; it requires owner-granted Usage Access and is off by default.
- **Screen-off pauses presentation; it must not dismiss, consume or reset a pending invitation.** Keep its ID, destination, payload, chosen copy and outside-tap stage. Restore it after the next readiness period.
- Invitation → first outside tap gives a gentle plea → second gives a final plea → third outside tap dismisses. The final plea has **no timer**. Yes/card/parcel accepts from all active message stages. Close and explicit Stop remain available.
- Keep the walk/carry/paw/open/rest animation and planted walking feet. It runs even when global Android animation scales are zero. Never change the phone's global animation settings.
- A nudge acceptance means navigation was requested, **not that the questionnaire was completed**. The host check-in/questionnaire owns answer persistence and completion.

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
- `MainActivity.kt`: native Sola launcher, permission-safe monitoring restoration and real check-in intent routing. `sola/ui/ReminderControls.kt` owns the onboarding/Settings opt-in; legacy `ui/nudge/` demo controls are unused.
- `sola/ui/DailyScreens.kt`: real mood logging destination. Keep future questionnaire expansion in this host feature.

## Add a destination or questionnaire

1. Register a destination key in `JunctionApplication`'s `destinations` map. Return an explicit host-app `Intent` from `(context, request)`; the library launches it after acceptance while the overlay is still visible.
2. Call `CatNudge.show(activity, NudgeRequest(...))` from a visible Activity. Use a stable ID for a scheduled event. Set a destination key and string payload, not a hardcoded activity inside the library.
3. For a due event from background, use `CatNudge.enqueue(context, request)`. It persists and alerts an already-running host; it does **not** illegally start a foreground service from an ordinary alarm receiver. Notification recovery requires a user tap if the host is unavailable.
4. Daily requests use destination `daily_quiz`; payload keys are `period` (`morning`/`evening`), `date` (`yyyy-MM-dd`) and `scheduled_at` (epoch millis). `JunctionApplication` forwards these plus `nudge_id` into the personal native check-in session. Put further questionnaire navigation/content/data work in this host route.
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
