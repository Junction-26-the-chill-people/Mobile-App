# Junction · Sola for Android

Native Kotlin / Jetpack Compose port of the supplied Sola AI Studio frontend. Includes seven onboarding pages, Today and activity ideas, both mood check-ins, a growing memory garden, photo/text/voice diary, statistics, a six-part monthly recap, Support and Settings. Profiles, entries, goals, people and drafts persist on the phone. **Sample garden** uses separate storage.

## Run

Use JDK 17+ and Android SDK 36:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. The installed app is **Junction** (`com.example.junction`). Begin your own profile or choose **Explore the sample garden**. Photos use Android’s picker; voice uses the phone’s speech-to-text service when available.

## Telegram over USB

The small Node 20+ bridge is in [telegram-bridge/](telegram-bridge/README.md). Copy its `.env.example` to `.env`, add the bot token locally, then:

```sh
node telegram-bridge/bridge.mjs
# In another terminal:
adb reverse tcp:3001 tcp:3001
adb shell am start -n com.example.junction/.MainActivity --es bridge_url http://127.0.0.1:3001
```

Alternatively run `scripts/start-telegram.ps1`. In **Settings → Telegram connection**, check the bridge and open the bot chat. Both people must press **Start** first. Save your numeric Telegram ID in your profile and your friend’s in their contact. Sending requires an explicit tap; delivery errors remain errors. Only one process may poll a Telegram bot’s updates. The bridge binds to localhost; away from USB it needs a deployed HTTPS service with authentication and per-user storage.

## Still reserved

Gemini summaries/reflections, account sign-in, clinician sharing, and device/music data are visible placeholders or saved preferences. No simulated calls, fake recordings, generated clinical insights or pretend Telegram delivery. The Support call button opens the dialer for an owner-configured phone number.

## Daily cat check-ins

In onboarding’s timing page or **Settings → Check-in preferences**, pick times/cadence and enable **Cat check-in visits**. Allow display over other apps when prompted. New profiles start at 10:00 and 21:00 local time; existing saved choices are preserved. Morning, afternoon and evening can each be off or repeat up to three times, hourly from the selected time within that day. Overlapping times share one visit. Weekdays and every-two-days are supported.

The cat waits for 15 continuous unlocked seconds. Screen-off hides her while preserving the invitation and message stage. Yes opens **How are you?** in your own journal; **Rest here** saves the mood and takes you to the garden. Accepting alone is not a completed log. Logging directly in the app also satisfies the current due visit. Unfinished accepted sessions survive relaunch, and completed session IDs prevent duplicate logging. Visit timing edits apply to future slots; already-pending visits stay intact. The native check-in screen temporarily defers other overlays while you are answering. Stop/disable remains available in Settings and the service notification. Android may delay inexact alarms; this never wakes a sleeping phone.

```sh
adb shell am start -n com.example.junction/.nudge.NudgeTriggerActivity
# Exercise the real readiness gate:
adb shell am start -n com.example.junction/.nudge.NudgeTriggerActivity --ez wait_ready true
```

The reusable animation remains in **`:catnudge`**; host scheduling and mood records remain in **`:app`**. See [NUDGE.md](NUDGE.md) and [AGENTS.md](AGENTS.md).
## Checks and screen previews

```sh
./gradlew :app:testDebugUnitTest :catnudge:testDebugUnitTest :app:assembleDebugAndroidTest lintDebug
node --test telegram-bridge/bridge.test.mjs
```

Native previews render into `app/build/sola-preview/`; widget previews remain in `catnudge/build/preview/`. Debug builds support isolated, non-persistent screen previews:

```sh
adb shell am start -n com.example.junction/.MainActivity --es preview_screen garden --ez preview_demo true
```

All 15 routes are listed in `SolaScreen`. These extras are ignored in release builds. Keep `.env`, local bridge data, APKs, photos and build output out of Git.
