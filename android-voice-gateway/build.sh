#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
toolchain_dir="${UFI_ANDROID_TOOLCHAIN:-$HOME/.cache/ufi-sms-android/toolchain}"
key_dir="${UFI_PLATFORM_KEY_DIR:-$HOME/.cache/ufi-sms-android/aosp-platform}"
java_home="${JAVA_HOME:-$toolchain_dir/jdk}"
sdk_root="${ANDROID_SDK_ROOT:-$toolchain_dir/sdk}"
build_tools_version="${UFI_BUILD_TOOLS_VERSION:-35.0.0}"
platform_version="${UFI_PLATFORM_VERSION:-19}"

export JAVA_HOME="$java_home"
export PATH="$java_home/bin:$PATH"

platform_jar="$sdk_root/platforms/android-$platform_version/android.jar"
build_tools="$sdk_root/build-tools/$build_tools_version"
javac_bin="$java_home/bin/javac"
aapt_bin="$build_tools/aapt"
d8_bin="$build_tools/d8"
zipalign_bin="$build_tools/zipalign"
apksigner_bin="$build_tools/apksigner"
platform_key="$key_dir/platform.pk8"
platform_cert="$key_dir/platform.x509.pem"

for required in "$javac_bin" "$platform_jar" "$aapt_bin" "$d8_bin" \
        "$zipalign_bin" "$apksigner_bin" "$platform_key" "$platform_cert"; do
    if [[ ! -e "$required" ]]; then
        echo "Missing Android build dependency: $required" >&2
        exit 1
    fi
done

expected_fingerprint="C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8"
actual_fingerprint="$(openssl x509 -in "$platform_cert" -noout -fingerprint -sha256 | cut -d= -f2)"
if [[ "$actual_fingerprint" != "$expected_fingerprint" ]]; then
    echo "The platform certificate does not match this modem firmware." >&2
    exit 1
fi

build_dir="$project_dir/build"
classes_dir="$build_dir/classes"
dex_dir="$build_dir/dex"
unsigned_apk="$build_dir/ufi-voice-gateway-unsigned.apk"
aligned_apk="$build_dir/ufi-voice-gateway-aligned.apk"
final_apk="$build_dir/ufi-voice-gateway.apk"

rm -rf "$classes_dir" "$dex_dir"
rm -f "$unsigned_apk" "$aligned_apk" "$final_apk"
mkdir -p "$classes_dir" "$dex_dir"

mapfile -t java_sources < <(find "$project_dir/src" -name '*.java' -type f | sort)
"$javac_bin" \
    -encoding UTF-8 \
    -source 7 \
    -target 7 \
    -bootclasspath "$platform_jar" \
    -d "$classes_dir" \
    "${java_sources[@]}"

mapfile -t class_files < <(find "$classes_dir" -name '*.class' -type f | sort)
"$d8_bin" \
    --lib "$platform_jar" \
    --min-api 19 \
    --output "$dex_dir" \
    "${class_files[@]}"

"$aapt_bin" package \
    -f \
    -M "$project_dir/AndroidManifest.xml" \
    -I "$platform_jar" \
    -F "$unsigned_apk"

(
    cd "$dex_dir"
    "$aapt_bin" add "$unsigned_apk" classes.dex >/dev/null
)

"$zipalign_bin" -f 4 "$unsigned_apk" "$aligned_apk"
"$apksigner_bin" sign \
    --key "$platform_key" \
    --cert "$platform_cert" \
    --min-sdk-version 19 \
    --out "$final_apk" \
    "$aligned_apk"
"$apksigner_bin" verify --verbose --print-certs "$final_apk"
echo "$final_apk"
