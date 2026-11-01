#!/usr/bin/env bash
# Runs inside the emulator job: smoke-tests the release APK the way a person would (install, open, no
# crash), then runs one device-test suite at one of the Galaxy Z Fold 8's screen sizes.
set -uo pipefail
apk="$1"
results="$2"
screen="$3"     # cover-screen or inner-screen
suite="${4:-app-walk}"   # app-walk, actions or widgets: each runs on its own emulator in parallel
pkg=com.pandyabhavik.pulse
case "$suite" in
  # FlexModeTest only does anything on the inner screen (it skips itself on the cover screen).
  app-walk)  tests=$pkg.ScreensTest,$pkg.FlexModeTest ;;
  actions)   tests=$pkg.ActionsTest ;;
  widgets)   tests=$pkg.WidgetsTest ;;
  *) echo "Unknown suite $suite"; exit 2 ;;
esac
mkdir -p "$results"
status=0

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
sleep 5

if [ "$screen" = cover-screen ]; then
  adb shell wm size 1080x2520
  adb shell wm density 420
  # Larger text, as many people use on the small screen: nothing should be cut off.
  adb shell settings put system font_scale 1.15
else
  adb shell wm size 1968x2184
  adb shell wm density 374
  adb shell settings put system font_scale 1.0
fi
sleep 3

if [ "$suite" = app-walk ]; then
echo "== Smoke test of the release APK =="
adb logcat -c
adb install -r "$apk"
for p in POST_NOTIFICATIONS ACTIVITY_RECOGNITION; do adb shell pm grant $pkg android.permission.$p || true; done
adb shell am start -W -n $pkg/.MainActivity
sleep 8
adb exec-out screencap -p > "$results/release-1-first-run.png"
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell am start -W -n $pkg/.MainActivity
sleep 4
adb exec-out screencap -p > "$results/release-2-reopened.png"
adb logcat -d -b crash > "$results/crash-release.txt"
if grep -q "Process: $pkg" "$results/crash-release.txt"; then
  echo "::error::The release APK crashed"
  cat "$results/crash-release.txt"
  status=1
elif ! adb shell pidof $pkg >/dev/null; then
  echo "::error::The release APK isn't running after launch"
  status=1
else
  echo "Release APK: opens and stays open"
fi
adb logcat -d > "$results/logcat-release.txt"
adb uninstall $pkg >/dev/null || true
fi

# ScreensTest walks the app (01-39) and FlexModeTest shows flex mode (70-79); ActionsTest does every action
# end to end and checks what was saved (40-69); WidgetsTest hosts the four home-screen widgets itself and
# screenshots them and their picker previews (80-99). All of them run over a fake Health Connect and a
# pinned clock (Tue 24 Nov 2026, 7:42 PM). The widget bind grant WidgetsTest needs is given from inside the
# test: Gradle installs the app afresh here (it was uninstalled above), so a grant from this script would
# have nothing to stick to.
echo "== $suite tests ($screen) =="
adb logcat -c
adb shell rm -rf /sdcard/Download/pulse-screens
if ! ./gradlew --no-daemon connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$tests"; then status=1; fi
mkdir -p "$results/$screen"
adb pull /sdcard/Download/pulse-screens/. "$results/$screen/" >/dev/null || true
cp -r app/build/reports/androidTests/connected "$results/$screen/report" 2>/dev/null || true
adb logcat -d > "$results/$screen/logcat.txt"
adb logcat -d -b crash > "$results/$screen/crash.txt"
if grep -q "Process: $pkg" "$results/$screen/crash.txt"; then
  echo "::error::The app crashed during the $suite tests"
  status=1
fi

adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0
exit $status
