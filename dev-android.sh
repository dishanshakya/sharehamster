#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
GRADLE="${GRADLE_BIN:-$HOME/.local/share/android-dev/gradle-8.9/bin/gradle}"
ADB="${ADB_BIN:-$SDK/platform-tools/adb}"
APP_ID="dev.sharehub.nepse"

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

if [[ ! -x "$GRADLE" ]]; then
  echo "Gradle not found: $GRADLE" >&2
  exit 1
fi
if [[ ! -x "$ADB" ]]; then
  echo "adb not found: $ADB" >&2
  exit 1
fi

device="${ADB_SERIAL:-$($ADB devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"
if [[ -z "$device" ]]; then
  echo "No authorized Android device found. Connect your phone, enable USB debugging, and accept its RSA authorization prompt." >&2
  "$ADB" devices
  exit 1
fi

build_install_launch() {
  echo "Building and installing on $device..."
  (cd "$ROOT" && "$GRADLE" --no-daemon installDebug)
  "$ADB" -s "$device" shell am force-stop "$APP_ID"
  "$ADB" -s "$device" shell am start -n "$APP_ID/.MainActivity"
}

fingerprint() {
  find "$ROOT/app/src" -type f -printf '%T@ %p\n' | sort | sha256sum | cut -d' ' -f1
}

build_install_launch
previous="$(fingerprint)"
echo "Watching app/src for changes (Ctrl+C to stop)."

while sleep 1; do
  current="$(fingerprint)"
  if [[ "$current" != "$previous" ]]; then
    previous="$current"
    build_install_launch || echo "Build/install failed; fix the error and save again to retry." >&2
  fi
done
