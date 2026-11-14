# Cat nudge

See [README.md](README.md) for setup, daily quiz timing and ADB commands, and [AGENTS.md](AGENTS.md) for reusable-widget integration and file ownership.

The widget is in `:catnudge`; daily quiz policy and destinations are in `:app`. Default daily slots are 10:00 and 21:00 local time, gated by 15 continuous unlocked seconds. Pending visits and their message stage survive screen-off. Usage Access is optional. Manual ADB previews can bypass the initial wait with the default command; add `--ez wait_ready true` to exercise the daily readiness gate.

The final plea waits for a third outside tap. Yes/card/parcel opens the registered host destination. No message-stage timer dismisses the visit.

## Android references

- [Application overlays and touch handling](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Foreground starts and exemptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Inexact alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Optional usage events](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
