#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
toolchain_dir="${UFI_ANDROID_TOOLCHAIN:-$HOME/.cache/ufi-sms-android/toolchain}"
java_home="${JAVA_HOME:-$toolchain_dir/jdk}"
sdk_root="${ANDROID_SDK_ROOT:-$toolchain_dir/sdk}"
build_tools="$sdk_root/build-tools/${UFI_BUILD_TOOLS_VERSION:-36.0.0}"
tablet_platform_version="${UFI_TABLET_PLATFORM_VERSION:-36}"
platform_jar="$sdk_root/platforms/android-$tablet_platform_version/android.jar"

export JAVA_HOME="$java_home"
export PATH="$java_home/bin:$PATH"

for required in "$java_home/bin/javac" "$java_home/bin/keytool" "$platform_jar" \
        "$build_tools/aapt" "$build_tools/d8" "$build_tools/zipalign" \
        "$build_tools/apksigner"; do
    if [[ ! -e "$required" ]]; then
        echo "Missing Android build dependency: $required" >&2
        exit 1
    fi
done

# Keep the fast standalone APK separate from Gradle's build/ tree so an AAB
# build and a sideload APK can coexist.
build_dir="$project_dir/standalone-build"
generated_dir="$build_dir/generated"
classes_dir="$build_dir/classes"
dex_dir="$build_dir/dex"
unsigned_apk="$build_dir/ufi-call-client-unsigned.apk"
aligned_apk="$build_dir/ufi-call-client-aligned.apk"
final_apk="$build_dir/ufi-call-client.apk"

rm -rf "$generated_dir" "$classes_dir" "$dex_dir"
rm -f "$unsigned_apk" "$aligned_apk" "$final_apk"
mkdir -p "$generated_dir" "$classes_dir" "$dex_dir"

"$build_tools/aapt" package -f -m \
    --min-sdk-version 26 \
    --target-sdk-version "$tablet_platform_version" \
    --version-code 5 \
    --version-name 0.5.0 \
    -M "$project_dir/AndroidManifest.xml" \
    -S "$project_dir/res" \
    -I "$platform_jar" \
    -J "$generated_dir"

mapfile -t java_sources < <(find "$project_dir/src" "$generated_dir" \
    -name '*.java' -type f | sort)
"$java_home/bin/javac" \
    -encoding UTF-8 \
    -source 8 \
    -target 8 \
    -bootclasspath "$platform_jar" \
    -d "$classes_dir" \
    "${java_sources[@]}"

mapfile -t class_files < <(find "$classes_dir" -name '*.class' -type f | sort)
"$build_tools/d8" \
    --lib "$platform_jar" \
    --min-api 26 \
    --output "$dex_dir" \
    "${class_files[@]}"

"$build_tools/aapt" package -f \
    --min-sdk-version 26 \
    --target-sdk-version "$tablet_platform_version" \
    --version-code 5 \
    --version-name 0.5.0 \
    -M "$project_dir/AndroidManifest.xml" \
    -S "$project_dir/res" \
    -I "$platform_jar" \
    -F "$unsigned_apk"
(
    cd "$dex_dir"
    "$build_tools/aapt" add "$unsigned_apk" classes.dex >/dev/null
)
"$build_tools/zipalign" -f 4 "$unsigned_apk" "$aligned_apk"

signing_dir="${UFI_SIGNING_DIR:-$HOME/.config/ufi-sms}"
keystore="$signing_dir/android-voice-client-signing.jks"
password_file="$signing_dir/android-voice-client-signing.password"
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"
if [[ ! -f "$password_file" ]]; then
    umask 077
    openssl rand -hex 32 > "$password_file"
fi
chmod 600 "$password_file"
signing_password="$(<"$password_file")"
if [[ ! -f "$keystore" ]]; then
    "$java_home/bin/keytool" -genkeypair \
        -keystore "$keystore" \
        -storepass "$signing_password" \
        -keypass "$signing_password" \
        -alias ufi-call-client \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=UFI Call Client, O=Local"
fi
chmod 600 "$keystore"

"$build_tools/apksigner" sign \
    --ks "$keystore" \
    --ks-key-alias ufi-call-client \
    --ks-pass "pass:$signing_password" \
    --key-pass "pass:$signing_password" \
    --min-sdk-version 26 \
    --out "$final_apk" \
    "$aligned_apk"
"$build_tools/apksigner" verify --verbose "$final_apk"
echo "$final_apk"
