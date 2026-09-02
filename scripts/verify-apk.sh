#!/usr/bin/env bash
# Required release gate: validates the generated APK rather than relying on Gradle success alone.
set -euo pipefail

apk_path=${1:?Usage: scripts/verify-apk.sh path/to/app.apk}
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$sdk_root" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must point to an Android SDK." >&2
  exit 2
fi

aapt2_path=$(find "$sdk_root/build-tools" -type f -name aapt2 -perm -111 | sort | tail -n 1)
apksigner_path=$(find "$sdk_root/build-tools" -type f -name apksigner -perm -111 | sort | tail -n 1)
if [[ -z "$aapt2_path" || -z "$apksigner_path" ]]; then
  echo "Android build-tools with aapt2 and apksigner are required." >&2
  exit 2
fi

file "$apk_path"
unzip -t "$apk_path"
badging=$($aapt2_path dump badging "$apk_path")
printf '%s\n' "$badging"
grep -Fq "package: name='com.wikiglobal.iconconverter'" <<<"$badging"
grep -q '^launchable-activity:' <<<"$badging"
grep -Eq "^application:.*icon='[^']+'" <<<"$badging"
"$apksigner_path" verify --verbose --print-certs "$apk_path"
/usr/bin/stat -f '%z bytes' "$apk_path"
shasum -a 256 "$apk_path"
