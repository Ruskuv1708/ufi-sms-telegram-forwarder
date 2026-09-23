#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
toolchain_dir="${UFI_ANDROID_TOOLCHAIN:-$HOME/.cache/ufi-sms-android/toolchain}"
key_dir="${UFI_PLATFORM_KEY_DIR:-$HOME/.cache/ufi-sms-android/aosp-platform}"
java_home="${JAVA_HOME:-$toolchain_dir/jdk}"
sdk_root="${ANDROID_SDK_ROOT:-$toolchain_dir/sdk}"
build_tools="$sdk_root/build-tools/${UFI_BUILD_TOOLS_VERSION:-35.0.0}"
platform_jar="$sdk_root/platforms/android-${UFI_PLATFORM_VERSION:-19}/android.jar"

export JAVA_HOME="$java_home"
export PATH="$java_home/bin:$PATH"

build_dir="$project_dir/build"
classes_dir="$build_dir/classes"
dex_dir="$build_dir/dex"
unsigned_apk="$build_dir/ufi-voice-audio-unsigned.apk"
aligned_apk="$build_dir/ufi-voice-audio-aligned.apk"
final_apk="$build_dir/ufi-voice-audio.apk"

rm -rf "$classes_dir" "$dex_dir"
rm -f "$unsigned_apk" "$aligned_apk" "$final_apk"
mkdir -p "$classes_dir" "$dex_dir"

mapfile -t java_sources < <(find "$project_dir/src" -name '*.java' -type f | sort)
"$java_home/bin/javac" -encoding UTF-8 -source 7 -target 7 \
    -bootclasspath "$platform_jar" -d "$classes_dir" "${java_sources[@]}"
mapfile -t class_files < <(find "$classes_dir" -name '*.class' -type f | sort)
"$build_tools/d8" --lib "$platform_jar" --min-api 19 \
    --output "$dex_dir" "${class_files[@]}"
"$build_tools/aapt" package -f -M "$project_dir/AndroidManifest.xml" \
    -I "$platform_jar" -F "$unsigned_apk"
(
    cd "$dex_dir"
    "$build_tools/aapt" add "$unsigned_apk" classes.dex >/dev/null
)
"$build_tools/zipalign" -f 4 "$unsigned_apk" "$aligned_apk"
"$build_tools/apksigner" sign \
    --key "$key_dir/platform.pk8" \
    --cert "$key_dir/platform.x509.pem" \
    --min-sdk-version 19 \
    --out "$final_apk" "$aligned_apk"
"$build_tools/apksigner" verify --verbose "$final_apk"
echo "$final_apk"
