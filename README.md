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

Gemini summaries/reflections, account sign-in, clinician sharing, device/music data and scheduled reminders are visible placeholders or saved preferences. No simulated calls, fake recordings, generated clinical insights or pretend Telegram delivery. The Support call button opens the dialer for an owner-configured phone number.

The existing **`:catnudge`** widget is preserved. Its manual ADB trigger still works; automatic visits are paused by `FrontendFeatures.CAT_DAILY_INTEGRATION = false`. Connecting the native questionnaire and reminder preferences is the **next integration step**. The widget’s contract remains morning/evening daily quizzes at 10:00/21:00, after 15 continuous unlocked seconds, with the invitation preserved through screen-off. See [NUDGE.md](NUDGE.md) and [AGENTS.md](AGENTS.md).

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
