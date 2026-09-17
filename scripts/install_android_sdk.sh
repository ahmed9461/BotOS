#!/usr/bin/env bash
# Resolve the SDK's own command-line tools; never rely on the runner's PATH.
set -euo pipefail

fail() { printf 'Android SDK setup: %s\n' "$1" >&2; exit "${2:-2}"; }
[[ $# -gt 0 ]] || fail 'Pass explicit SDK package versions.'
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk_root" && -d "$sdk_root" ]] || fail 'ANDROID_HOME or ANDROID_SDK_ROOT must point to an installed SDK.'
sdk_root="$(cd "$sdk_root" && pwd -P)"
if [[ -n "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
    [[ -d "$ANDROID_SDK_ROOT" ]] || fail 'ANDROID_SDK_ROOT does not exist.'
    other_root="$(cd "$ANDROID_SDK_ROOT" && pwd -P)"
    [[ "$sdk_root" == "$other_root" ]] || fail 'ANDROID_HOME and ANDROID_SDK_ROOT refer to different SDKs.'
fi

sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
    sdkmanager=''
    if [[ -d "$sdk_root/cmdline-tools" ]]; then
        mapfile -d '' candidates < <(find "$sdk_root/cmdline-tools" -mindepth 3 -maxdepth 3 -type f -name sdkmanager -print0 | sort -zV)
        for candidate in "${candidates[@]}"; do
            [[ ! -x "$candidate" ]] || sdkmanager="$candidate"
        done
    fi
fi
[[ -n "$sdkmanager" && -x "$sdkmanager" ]] || fail 'No executable sdkmanager in this SDK. Install Android SDK Command-Line Tools; see docs/BUILD.md.' 127
export ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root"
export PATH="$(dirname "$sdkmanager"):$sdk_root/platform-tools:$PATH"
if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$sdk_root" "$sdk_root" >> "$GITHUB_ENV"
fi
if [[ -n "${GITHUB_PATH:-}" ]]; then
    printf '%s\n%s\n' "$(dirname "$sdkmanager")" "$sdk_root/platform-tools" >> "$GITHUB_PATH"
fi
printf 'Using SDK command-line tools: %s\n' "$sdkmanager"
"$sdkmanager" --sdk_root="$sdk_root" --version
# Process substitution avoids treating yes's expected SIGPIPE as a failed install.
"$sdkmanager" --sdk_root="$sdk_root" --licenses < <(yes)
"$sdkmanager" --sdk_root="$sdk_root" "$@"
