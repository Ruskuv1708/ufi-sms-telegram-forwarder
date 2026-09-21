#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
toolchain_dir="${UFI_ANDROID_TOOLCHAIN:-$HOME/.cache/ufi-sms-android/toolchain}"
java_home="${JAVA_HOME:-$toolchain_dir/jdk}"
sdk_root="${ANDROID_SDK_ROOT:-$toolchain_dir/sdk}"
build_tools_version="${UFI_BUILD_TOOLS_VERSION:-35.0.0}"
platform_version="${UFI_PLATFORM_VERSION:-19}"

export JAVA_HOME="$java_home"
export PATH="$java_home/bin:$PATH"

javac_bin="$java_home/bin/javac"
keytool_bin="$java_home/bin/keytool"
platform_jar="$sdk_root/platforms/android-$platform_version/android.jar"
build_tools="$sdk_root/build-tools/$build_tools_version"
aapt_bin="$build_tools/aapt"
d8_bin="$build_tools/d8"
zipalign_bin="$build_tools/zipalign"
apksigner_bin="$build_tools/apksigner"

for required in "$javac_bin" "$keytool_bin" "$platform_jar" "$aapt_bin" "$d8_bin" "$zipalign_bin" "$apksigner_bin"; do
    if [[ ! -e "$required" ]]; then
        echo "Missing Android build dependency: $required" >&2
        exit 1
    fi
done

build_dir="$project_dir/build"
classes_dir="$build_dir/classes"
dex_dir="$build_dir/dex"
unsigned_apk="$build_dir/ufi-sms-forwarder-unsigned.apk"
aligned_apk="$build_dir/ufi-sms-forwarder-aligned.apk"
final_apk="$build_dir/ufi-sms-forwarder.apk"

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
    -A "$project_dir/assets" \
    -I "$platform_jar" \
    -F "$unsigned_apk"

(
    cd "$dex_dir"
    "$aapt_bin" add "$unsigned_apk" classes.dex >/dev/null
)

"$zipalign_bin" -f 4 "$unsigned_apk" "$aligned_apk"

signing_dir="${UFI_SIGNING_DIR:-$HOME/.config/ufi-sms}"
keystore="$signing_dir/android-forwarder-signing.jks"
password_file="$signing_dir/android-forwarder-signing.password"
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"

if [[ ! -f "$password_file" ]]; then
    umask 077
    openssl rand -hex 32 > "$password_file"
fi
chmod 600 "$password_file"
signing_password="$(<"$password_file")"

if [[ ! -f "$keystore" ]]; then
    "$keytool_bin" -genkeypair \
        -keystore "$keystore" \
        -storepass "$signing_password" \
        -keypass "$signing_password" \
        -alias ufi-sms-forwarder \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=UFI SMS Forwarder, O=Local"
fi
chmod 600 "$keystore"

"$apksigner_bin" sign \
    --ks "$keystore" \
    --ks-key-alias ufi-sms-forwarder \
    --ks-pass "pass:$signing_password" \
    --key-pass "pass:$signing_password" \
    --min-sdk-version 19 \
    --out "$final_apk" \
    "$aligned_apk"

"$apksigner_bin" verify --verbose "$final_apk"
echo "$final_apk"
