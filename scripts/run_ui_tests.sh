#!/usr/bin/env bash
# Disposable CI emulator. Test failures are propagated; evidence collection is best-effort only.
set -euo pipefail
root="${ANDROID_HOME:?Android SDK must be configured first}"
mkdir -p diagnostics/ui
manager="$root/cmdline-tools/latest/bin/sdkmanager"
image='system-images;android-35;google_apis;x86_64'
"$manager" --sdk_root="$root" 'platform-tools' 'emulator' "$image" > diagnostics/ui/sdk-install.txt 2>&1
export PATH="$root/platform-tools:$root/emulator:$PATH"
if [[ -e /dev/kvm ]]; then sudo chmod a+rw /dev/kvm; fi
printf 'no\n' | "$root/cmdline-tools/latest/bin/avdmanager" create avd --force --name botos-ui --package "$image" --device pixel_6
emulator -avd botos-ui -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -camera-back none -camera-front none > diagnostics/ui/emulator.txt 2>&1 &
emulator_pid=$!
cleanup() {
  adb pull /sdcard/Android/data/com.ahmed9461.botos/files/ui-evidence diagnostics/ui/ >/dev/null 2>&1 || true
  adb logcat -d -s AndroidRuntime > diagnostics/ui/runtime.txt 2>&1 || true
  adb emu kill >/dev/null 2>&1 || true
  kill "$emulator_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT
booted=0
for attempt in $(seq 1 120); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]]; then booted=1; break; fi
  if ! kill -0 "$emulator_pid" 2>/dev/null; then cat diagnostics/ui/emulator.txt; exit 1; fi
  sleep 2
done
if [[ "$booted" != '1' ]]; then echo 'Emulator did not boot in time'; exit 1; fi
adb shell input keyevent 82
adb shell settings put secure show_ime_with_hard_keyboard 1
./gradlew --no-daemon :app:connectedDebugAndroidTest
