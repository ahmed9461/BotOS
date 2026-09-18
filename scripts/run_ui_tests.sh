#!/usr/bin/env bash
# Disposable CI emulator. Keep the real device gate; bound setup and cleanup separately.
set -euo pipefail
root="${ANDROID_HOME:?Android SDK must be configured first}"
mkdir -p diagnostics/ui
manager="$root/cmdline-tools/latest/bin/sdkmanager"
image='system-images;android-35;google_apis;x86_64'
export PATH="$root/platform-tools:$root/emulator:$PATH"
# Old avdmanager and new emulator must resolve precisely the same user/AVD directories.
device_home="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/botos-device-home"
export ANDROID_SDK_HOME="$device_home"
export ANDROID_USER_HOME="$device_home/.android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export ANDROID_SERIAL='emulator-5554'
mkdir -p "$ANDROID_AVD_HOME"
emulator_pid=''
device_ready=0
cleanup() {
  status=$?
  trap - EXIT INT TERM
  set +e
  if [[ "$device_ready" == '1' ]]; then
    timeout -k 2s 8s adb -s "$ANDROID_SERIAL" pull /sdcard/Android/data/com.ahmed9461.botos/files/ui-evidence diagnostics/ui/ > diagnostics/ui/evidence-pull.txt 2>&1
    timeout -k 2s 5s adb -s "$ANDROID_SERIAL" logcat -d -s AndroidRuntime BotOSUiTest > diagnostics/ui/runtime.txt 2>&1
    if [[ "$status" != '0' ]]; then
      timeout -k 2s 5s adb -s "$ANDROID_SERIAL" shell dumpsys window > diagnostics/ui/failure-window.txt 2>&1
      timeout -k 2s 5s adb -s "$ANDROID_SERIAL" shell dumpsys power > diagnostics/ui/failure-power.txt 2>&1
      timeout -k 2s 5s adb -s "$ANDROID_SERIAL" exec-out screencap -p > diagnostics/ui/failure-screen.png
    fi
    timeout -k 2s 5s adb -s "$ANDROID_SERIAL" emu kill >/dev/null 2>&1
  fi
  if [[ -n "$emulator_pid" ]]; then kill "$emulator_pid" >/dev/null 2>&1; fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM
printf 'Installing device packages\n'
timeout -k 10s 8m "$manager" --sdk_root="$root" 'platform-tools' 'emulator' "$image" 2>&1 | tee diagnostics/ui/sdk-install.txt
if [[ -e /dev/kvm ]]; then sudo chmod a+rw /dev/kvm; fi
printf 'Creating AVD in %s\n' "$ANDROID_AVD_HOME"
printf 'no\n' | timeout -k 5s 60s "$root/cmdline-tools/latest/bin/avdmanager" create avd --force --name botos-ui --package "$image" --device pixel_6 --path "$ANDROID_AVD_HOME/botos-ui.avd" 2>&1 | tee diagnostics/ui/avd-create.txt
# Verify the actual discovery path, not just avdmanager's exit status.
timeout -k 2s 10s emulator -list-avds > diagnostics/ui/avd-list.txt 2>&1
cat diagnostics/ui/avd-list.txt
if ! grep -Fxq 'botos-ui' diagnostics/ui/avd-list.txt; then
  find "$device_home" -maxdepth 4 -type f -name '*.ini' -print
  echo 'Created AVD is not discoverable by the emulator.' >&2
  exit 1
fi
timeout -k 2s 10s adb start-server
emulator -avd botos-ui -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -camera-back none -camera-front none > diagnostics/ui/emulator.txt 2>&1 &
emulator_pid=$!
deadline=$((SECONDS + 240))
while (( SECONDS < deadline )); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then cat diagnostics/ui/emulator.txt; exit 1; fi
  boot="$(timeout -k 1s 4s adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot" == '1' ]]; then device_ready=1; break; fi
  sleep 2
done
if [[ "$device_ready" != '1' ]]; then echo 'Emulator did not boot within 240 seconds'; tail -n 80 diagnostics/ui/emulator.txt; exit 1; fi
printf 'Device booted. Preparing a stable foreground before actual UI regression tests.\n'
# These settings belong only to this disposable, non-secure emulator.
if [[ "$(timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != '1' ]]; then
  echo 'Refusing to change screen settings on a non-emulator device.' >&2
  exit 1
fi
timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell settings put system screen_off_timeout 1800000
timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell svc power stayon true
timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_WAKEUP
timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell wm dismiss-keyguard

# KEYCODE_MENU used to make HOME part of the critical path. On fresh Google APIs images
# Pixel Launcher can ANR before instrumentation starts and hold the system ANR dialog over
# BotOS. Resolve HOME dynamically, stop it only on this disposable emulator, and park on
# Settings so an ActivityScenario recreation never falls through to the launcher.
home_component="$(timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
if [[ "$home_component" != */* ]]; then
  printf 'home_component=%s\n' "$home_component" > diagnostics/ui/preflight.txt
  echo 'Could not resolve the emulator HOME activity.' >&2
  exit 1
fi
home_package="${home_component%%/*}"
if [[ ! "$home_package" =~ ^[A-Za-z0-9._]+$ ]]; then
  printf 'home_component=%s\n' "$home_component" > diagnostics/ui/preflight.txt
  echo 'Resolved HOME package has an unexpected shape.' >&2
  exit 1
fi
printf 'home_package=%s\n' "$home_package" > diagnostics/ui/preflight.txt
timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell am force-stop "$home_package"
timeout -k 2s 20s adb -s "$ANDROID_SERIAL" shell am start -W -a android.settings.SETTINGS >> diagnostics/ui/preflight.txt 2>&1

preflight_ready=0
preflight_deadline=$((SECONDS + 20))
while (( SECONDS < preflight_deadline )); do
  activity_state="$(timeout -k 2s 6s adb -s "$ANDROID_SERIAL" shell dumpsys activity activities 2>/dev/null || true)"
  resumed="$(grep -m1 'mResumedActivity' <<<"$activity_state" || true)"
  if [[ "$resumed" == *'com.android.settings'* ]]; then
    printf 'resumed=%s\n' "$resumed" >> diagnostics/ui/preflight.txt
    preflight_ready=1
    break
  fi
  sleep 1
done
if [[ "$preflight_ready" != '1' ]]; then
  timeout -k 2s 5s adb -s "$ANDROID_SERIAL" shell dumpsys activity activities > diagnostics/ui/preflight-activity.txt 2>&1 || true
  timeout -k 2s 5s adb -s "$ANDROID_SERIAL" shell dumpsys window > diagnostics/ui/preflight-window.txt 2>&1 || true
  timeout -k 2s 5s adb -s "$ANDROID_SERIAL" exec-out screencap -p > diagnostics/ui/preflight-screen.png || true
  echo 'System Settings did not become the resumed activity before UI tests.' >&2
  exit 1
fi

timeout -k 2s 10s adb -s "$ANDROID_SERIAL" shell settings put secure show_ime_with_hard_keyboard 1
timeout -k 15s 8m ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest "$@" 2>&1 | tee diagnostics/ui/tests.txt
