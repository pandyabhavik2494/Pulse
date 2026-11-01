# Pulse

A weight-loss buddy for Android (Kotlin, Jetpack Compose), made for the Galaxy Z Fold 8: the cover screen,
the inner screen and flex mode each get their own layout.

Every day Pulse answers three things: how far under or over you are (calories burned minus eaten),
how much you've moved (live steps), and when you'll reach your next weight (trend weight and milestone dates
that update every morning from your real data).

- **Steps and calories burned** (resting + active) from the Fitbit, through Health Connect; the phone's step
  sensor ticks the count live between Fitbit syncs.
- **Food** from MyFitnessPal, through Health Connect.
- **Weight** from VeSync, through Health Connect. **Sleep** from the Fitbit, shown in History only.
- Milestones every 5 kg and 5/10/15/20/25% down, small wins every 1 kg, a forgiving streak, calm words on
  over days, surprise ring styles, a Monday recap with one plan for the week.

Nothing leaves the phone. Data syncs when the app opens, on pull-to-refresh and every 15 minutes in the background.

## Layout

- `core/`: the maths and words (trend weight, forecast, milestones, streak, nudges, recap) as plain Kotlin with unit tests.
- `app/`: Health Connect reader, live steps, Room storage, screens, widgets, notifications, background work.
- `.github/workflows/android.yml`: unit tests, the release build, and emulator tests with screenshots at both
  Fold screen sizes. Each build is published as a release `build-N`; the file installed on the phone is
  re-signed with Pulse's own key.
