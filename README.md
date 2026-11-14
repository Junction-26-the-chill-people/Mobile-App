# Junction mobile app

An Android host app plus a reusable animated cat nudge in **`:catnudge`**.

The cat invites the user to a **daily quiz**, once in the morning (default **10:00**) and once in the evening (**21:00**, phone-local time). Daily visits wait for **15 continuous seconds of unlocked screen time**. Optional Usage Access can also require another app to open. Locking the phone hides the cat but **keeps the pending invitation and its message stage** for the next eligible session.

## Run

Use JDK 17+ and Android SDK 36, then:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. Open Junction, allow **Display over other apps**, and enable **Daily quiz visits**. The times are editable in the app. Visit notifications are recommended; Usage Access is optional. Daily mode keeps a foreground notification while enabled. Android/OEM battery rules and force-stop can delay delivery; alarms are deliberately inexact and never wake the phone.

## Trigger with ADB

```sh
adb shell am start -n com.example.junction/.nudge.NudgeTriggerActivity
# Exercise the real 15-second gate:
adb shell am start -n com.example.junction/.nudge.NudgeTriggerActivity --ez wait_ready true
# Stop pending visits:
adb shell am start -n com.example.junction/.nudge.NudgeTriggerActivity --ez dismiss true
```

Manual previews bypass the initial 15-second wait by default. Screen-off restoration still waits. `scripts/nudge.ps1 random` sends requests every 2–5 minutes while the computer/ADB connection remains active; Ctrl+C stops it. Stable request IDs prevent duplicate queued invitations.

## Integrate another destination

Register a destination in `JunctionApplication`, then call from a visible Activity:

```kotlin
CatNudge.show(this, NudgeRequest(
    id = "my-feature:unique-visit",
    destination = "my_feature",
    payload = mapOf("item_id" to "123"),
))
```

The library owns animation, overlay lifecycle and durable requests. **Pages and questionnaires belong in `:app`**. `daily_quiz` currently opens the reserved screen with `period`, `date`, `scheduled_at` and `nudge_id` extras. Copy is configurable per request; defaults rotate invitation → gentle plea → final plea. The final plea waits for another outside tap; Yes opens the destination.

See [AGENTS.md](AGENTS.md) for file locations, extension rules and tests.

```sh
./gradlew :catnudge:testDebugUnitTest :app:testDebugUnitTest lintDebug
```
