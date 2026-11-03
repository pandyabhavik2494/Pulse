# Pulse for iPhone and Apple Watch

A native SwiftUI port of the Android Pulse app: a calm weight-loss buddy that reads Apple Health and shows how today is going, where your weight is heading and when you'll reach each milestone. Dark, glowing, encouraging. No red, no "over budget" and no "needs work".

## What it does

- **Today**: a greeting, a verdict card (a headline such as "Mostly on track", one win, "One small thing: …", and a count per status), then tiles grouped Food / Move / Body / Recovery. The 12 default tiles are Burned, Eaten, Calories (balance), Left to eat, Steps, Distance, Weight, Weight trend, Resting heart, Heart, Sleep and Deep sleep. Each tile has a status: On track (green), Steady (soft blue), Room to grow (soft pink) or Filling in (grey). Tap a tile to open its section (Calories, Activity, Body, Heart and vitals, Sleep). **Customize** picks and orders the tiles.
- **Journey**: "Goal 85 kg on …" at your 7-day pace, a "Try a different pace" slider with 500 / My pace / 1,000 / 1,250 chips that moves every milestone date, and a milestone timeline drawn as one continuous line.
- **History**: past days with the balance, steps and weight for each. Tap a day for its details.
- **Settings**: profile and plan, Health access, start over.
- **Widgets**: Balance (small and medium home screen, plus Lock Screen) and Left to eat (Lock Screen).
- **Apple Watch**: three vertical pages (balance with the verdict headline, left to eat, steps) and complications for balance and left to eat. The watch reads Health itself. The iPhone sends your profile, and fills in food the watch hasn't synced yet.

**Data** (read-only from Apple Health): weight (VeSync's samples win when it wrote any; the latest weigh-in of each day), food energy, macros, fibre, sugar, sodium and water (MyFitnessPal), active and resting energy, steps, distance, flights, heart rate (latest, average and range), resting heart rate, HRV, blood oxygen, breathing rate, VO2 max, and sleep stages. Daily totals use `HKStatisticsCollectionQuery`. Observer queries with background delivery refresh it. Days are stored on the phone with SwiftData: 90 days on the first read, then the last 3 days on each refresh.

All the maths (forecast, budget, two-week outlook, moods, streaks, recap) lives in `Shared/Core` and is a line-by-line port of the Android `core` module. Its unit tests are ported to `PulseTests` (117 tests).

## Layout

| Folder | What |
|---|---|
| `Shared/Core` | Pure logic shared by iPhone, watch and widgets, plus `HomeState`, `Snapshot` and `DemoData` |
| `Shared/Health` | HealthKit reader, phone↔watch link, and a debug-only simulator seeder |
| `Shared/UI` | Colours and wording |
| `Shared/Widgets` | Widgets and complications (built into both widget extensions) |
| `Pulse` | iPhone app |
| `PulseWatch` | Apple Watch app |
| `PulseTests` | Unit tests for the core |
| `PulseUITests` | Simulator screenshot run (scheme `Screenshots`) |

Targets: `Pulse` (iOS 26), `PulseWidget`, `PulseWatch` (watchOS 26, embedded in Pulse), `PulseWatchWidget`, `PulseTests` and `PulseUITests`. Schemes: `Pulse` (build and unit tests), `PulseWatch` and `Screenshots`.

## Build

The Xcode project is generated, not committed:

```bash
brew install xcodegen
```

```bash
cd ios && xcodegen generate && open Pulse.xcodeproj
```

Tests: `xcodebuild test -project Pulse.xcodeproj -scheme Pulse -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max'`

Launch arguments: `-PulseDemoData YES` skips onboarding and Health and fills the app with a month of sample days (used by CI screenshots). `-seedHealth` is debug-only: it writes the same sample month into the simulator's Health store, so the app reads real Health data.

## Install on your iPhone Air and iPhone 18 Pro Max

1. Open `Pulse.xcodeproj` in Xcode. Sign in with your Apple ID under **Xcode → Settings → Accounts**.
2. Your Personal Team (`BPPU7JV4Q4`) is already set in `project.yml` with automatic signing. If Xcode shows a signing error, select each target (**Pulse**, **PulseWidget**, **PulseWatch**, **PulseWatchWidget**), open **Signing & Capabilities**, and pick "Bhavik Pandya (Personal Team)".
3. Plug in the iPhone and unlock it. Tap **Trust** if it asks.
4. On the iPhone, turn on **Settings → Privacy & Security → Developer Mode**. It restarts, then confirm.
5. In Xcode, pick the iPhone as the run destination with the **Pulse** scheme, then press Run. The watch app is embedded and installs on the paired Apple Watch Ultra automatically. If it doesn't, open the Watch app on the iPhone and tap **Install** next to Pulse. The watch also needs Developer Mode, under **Settings → Privacy & Security** on the watch.
6. The first time, iOS may block the app. Go to **Settings → General → VPN & Device Management**, tap your Apple ID and choose **Trust**.
7. Open Pulse, enter your details (the goal defaults to 85 kg), and tap **Connect Apple Health**. On the Health sheet, choose **Select All** and then **All Recorded Data** (with "Past 30 days", Pulse only sees a month of history). Then tap **Allow**. Accept the Health prompt on the watch the first time you open it there.
8. Repeat steps 3 to 6 for the second iPhone.

### Free Personal Team caveats

- Apps signed with a free team **expire after 7 days**. Plug in and press Run again to renew. Your data stays: it lives in Apple Health and in the app's storage.
- A free team can register only about **10 App IDs a week**. Pulse uses 4 (app, widget, watch app, watch widget), so avoid changing bundle IDs.
- Free provisioning allows only **3 sideloaded apps per device** at a time. If Xcode refuses to install, delete an older sideloaded app first.
- HealthKit, background delivery and App Groups work with a free team. Push notifications, iCloud and similar capabilities don't, and Pulse doesn't use them.
