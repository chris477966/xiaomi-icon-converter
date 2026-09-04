#!/bin/sh
set -eu

apk="app/src/main/assets/providers/Lawnicons.2.18.0.apk"
aapt2_bin="${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}/build-tools/35.0.0/aapt2"
expected="e830b37e1cd7cd66487492f4a1084ba086254b2b09541d729da0ec2169a73bfe"
actual="$(shasum -a 256 "$apk" | awk '{print $1}')"
[ "$actual" = "$expected" ]
"$aapt2_bin" dump resources "$apk" | grep -q 'xml/grayscale_icon_map'
xml_file="$("$aapt2_bin" dump resources "$apk" | awk '/xml\/grayscale_icon_map/{getline; if ($0 ~ /res\//) { sub(/^.*res\//, "res/"); sub(/ .*$/, ""); print; exit }}')"
[ -n "$xml_file" ]
tree="$("$aapt2_bin" dump xmltree --file "$xml_file" "$apk")"
printf '%s\n' "$tree" | grep -q 'package="com.android.settings"'
printf '%s\n' "$tree" | grep -q 'package="com.miui.gallery"'
printf '%s\n' "$tree" | grep -q 'package="com.miui.calculator"'
count="$(printf '%s\n' "$tree" | sed -n 's/.*package="\([^"]*\)".*/\1/p' | sort -u | wc -l | tr -d ' ')"
printf 'LAWNICONS_SHA=%s\nGRAYSCALE_MAP_ENTRIES=%s\n' "$actual" "$count"
