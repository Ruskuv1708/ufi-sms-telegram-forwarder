#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(cd "$script_dir/../.." && pwd)"
binary="$project_dir/dist/UFI-Phone"
output_dir="$project_dir/dist"
version=""
minimum_glibc="2.35"

usage() {
    cat <<'EOF'
Usage: build-packages.sh [OPTIONS]

Build UFI Phone Linux packages from a PyInstaller executable.

Options:
  --binary FILE     PyInstaller executable (default: dist/UFI-Phone)
  --output DIR      Destination directory (default: dist)
  --version VERSION Package version (default: desktop installer version)
  --min-glibc VER   Minimum glibc for the input binary (default: 2.35)
  -h, --help        Show this help

Outputs a portable tarball, an interactive .run installer, a Debian package,
and a SHA-256 checksum file. Set SOURCE_DATE_EPOCH for reproducible timestamps.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --binary)
            [[ $# -ge 2 ]] || { echo "--binary needs a file" >&2; exit 2; }
            binary="$2"
            shift 2
            ;;
        --output)
            [[ $# -ge 2 ]] || { echo "--output needs a directory" >&2; exit 2; }
            output_dir="$2"
            shift 2
            ;;
        --version)
            [[ $# -ge 2 ]] || { echo "--version needs a value" >&2; exit 2; }
            version="$2"
            shift 2
            ;;
        --min-glibc)
            [[ $# -ge 2 ]] || { echo "--min-glibc needs a version" >&2; exit 2; }
            minimum_glibc="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ -z "$version" ]]; then
    version="$(sed -n 's/^#define MyAppVersion "\([^"]*\)"/\1/p' \
        "$project_dir/desktop/windows/UFI-Phone.iss" | head -n 1)"
fi
if [[ ! "$version" =~ ^[0-9][0-9A-Za-z.+:~-]*$ ]]; then
    echo "Invalid package version: $version" >&2
    exit 2
fi
if [[ ! "$minimum_glibc" =~ ^[0-9]+\.[0-9]+$ ]]; then
    echo "Invalid minimum glibc version: $minimum_glibc" >&2
    exit 2
fi

if [[ ! -f "$binary" || ! -x "$binary" ]]; then
    echo "UFI Phone executable not found or not executable: $binary" >&2
    echo "Build it with PyInstaller first, or pass --binary FILE." >&2
    exit 1
fi
if ! file "$binary" | grep -q 'ELF'; then
    echo "The package input is not a Linux ELF executable: $binary" >&2
    exit 1
fi

binary_description="$(file -b "$binary")"
case "$binary_description" in
    *x86-64*)
        portable_arch="x86_64"
        debian_arch="amd64"
        ;;
    *aarch64*|*ARM\ aarch64*)
        portable_arch="aarch64"
        debian_arch="arm64"
        ;;
    *ARM*)
        portable_arch="armv7l"
        debian_arch="armhf"
        ;;
    *)
        echo "Unsupported Linux executable architecture: $binary_description" >&2
        exit 1
        ;;
esac

source_date_epoch="${SOURCE_DATE_EPOCH:-}"
if [[ -z "$source_date_epoch" ]]; then
    source_date_epoch="$(git -C "$project_dir" log -1 --format=%ct 2>/dev/null || date +%s)"
fi
if [[ ! "$source_date_epoch" =~ ^[0-9]+$ ]]; then
    echo "SOURCE_DATE_EPOCH must be a Unix timestamp." >&2
    exit 2
fi
release_date="$(date -u -d "@$source_date_epoch" +%F)"

mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/ufi-phone-package.XXXXXX")"
cleanup() {
    rm -rf -- "$work_dir"
}
trap cleanup EXIT HUP INT TERM

bundle_name="UFI-Phone-$version-linux-$portable_arch"
bundle_dir="$work_dir/portable/$bundle_name"
install -d \
    "$bundle_dir/usr/bin" \
    "$bundle_dir/usr/share/applications" \
    "$bundle_dir/usr/share/icons/hicolor/512x512/apps" \
    "$bundle_dir/usr/share/metainfo" \
    "$bundle_dir/usr/share/doc/ufi-phone"
install -m755 "$binary" "$bundle_dir/usr/bin/UFI-Phone"
install -m755 "$script_dir/AppRun" "$bundle_dir/AppRun"
install -m755 "$project_dir/desktop/install-linux.sh" "$bundle_dir/install.sh"
install -m755 "$project_dir/desktop/uninstall-linux.sh" "$bundle_dir/uninstall.sh"
install -m644 "$script_dir/ufi-phone.desktop" \
    "$bundle_dir/usr/share/applications/ufi-phone.desktop"
install -m644 "$project_dir/assets/ufi-phone.png" \
    "$bundle_dir/usr/share/icons/hicolor/512x512/apps/ufi-phone.png"
sed -E \
    "s|<release version=\"[^\"]+\" date=\"[^\"]+\" */>|<release version=\"$version\" date=\"$release_date\" />|" \
    "$script_dir/io.github.ruskuv1708.ufiphone.metainfo.xml" \
    > "$bundle_dir/usr/share/metainfo/io.github.ruskuv1708.ufiphone.metainfo.xml"
install -m644 "$script_dir/PACKAGE-README.md" "$bundle_dir/README.md"
install -m644 "$project_dir/LICENSE" "$bundle_dir/LICENSE"
install -m644 "$project_dir/CHANGELOG.md" "$bundle_dir/usr/share/doc/ufi-phone/CHANGELOG.md"
install -m644 "$project_dir/LICENSE" "$bundle_dir/usr/share/doc/ufi-phone/copyright"
ln -s usr/share/applications/ufi-phone.desktop "$bundle_dir/ufi-phone.desktop"
ln -s usr/share/icons/hicolor/512x512/apps/ufi-phone.png "$bundle_dir/ufi-phone.png"
ln -s ufi-phone.png "$bundle_dir/.DirIcon"

portable_archive="$output_dir/$bundle_name.tar.gz"
rm -f -- "$portable_archive"
tar --sort=name --owner=0 --group=0 --numeric-owner \
    --mtime="@$source_date_epoch" -C "$work_dir/portable" -cf - "$bundle_name" \
    | gzip -n -9 > "$portable_archive"

run_installer="$output_dir/$bundle_name.run"
rm -f -- "$run_installer"
cp "$script_dir/installer-header.sh" "$run_installer"
cat "$portable_archive" >> "$run_installer"
chmod 755 "$run_installer"

debian_root="$work_dir/debian-root"
install -d "$debian_root"
cp -a "$bundle_dir/usr" "$debian_root/"
rm -f "$debian_root/usr/share/doc/ufi-phone/CHANGELOG.md"
gzip -n -9 < "$project_dir/CHANGELOG.md" \
    > "$debian_root/usr/share/doc/ufi-phone/changelog.gz"
installed_size="$(du -sk "$debian_root" | awk '{print $1}')"

control_root="$work_dir/control"
install -d "$control_root"
sed \
    -e "s/@VERSION@/$version/g" \
    -e "s/@ARCHITECTURE@/$debian_arch/g" \
    -e "s/@INSTALLED_SIZE@/$installed_size/g" \
    -e "s/@MIN_GLIBC@/$minimum_glibc/g" \
    "$script_dir/debian/control.in" > "$control_root/control"
install -m755 "$script_dir/debian/postinst" "$control_root/postinst"
install -m755 "$script_dir/debian/postrm" "$control_root/postrm"

archive_parts="$work_dir/deb-parts"
install -d "$archive_parts"
printf '2.0\n' > "$archive_parts/debian-binary"
tar --sort=name --owner=0 --group=0 --numeric-owner \
    --mtime="@$source_date_epoch" -C "$control_root" -cf - . \
    | gzip -n -9 > "$archive_parts/control.tar.gz"
tar --sort=name --owner=0 --group=0 --numeric-owner \
    --mtime="@$source_date_epoch" -C "$debian_root" -cf - . \
    | gzip -n -9 > "$archive_parts/data.tar.gz"

debian_package="$output_dir/ufi-phone_${version}_${debian_arch}.deb"
rm -f -- "$debian_package"
(
    cd "$archive_parts"
    ar rcsD "$debian_package" debian-binary control.tar.gz data.tar.gz
)

expected_members=$'debian-binary\ncontrol.tar.gz\ndata.tar.gz'
if [[ "$(ar t "$debian_package")" != "$expected_members" ]]; then
    echo "Debian package verification failed." >&2
    exit 1
fi
tar -tzf "$portable_archive" "$bundle_name/AppRun" >/dev/null
if command -v dpkg-deb >/dev/null 2>&1; then
    dpkg-deb --info "$debian_package" >/dev/null
    dpkg-deb --contents "$debian_package" >/dev/null
fi
if command -v desktop-file-validate >/dev/null 2>&1; then
    desktop-file-validate "$bundle_dir/usr/share/applications/ufi-phone.desktop"
fi
if command -v appstreamcli >/dev/null 2>&1; then
    appstreamcli validate --no-net \
        "$bundle_dir/usr/share/metainfo/io.github.ruskuv1708.ufiphone.metainfo.xml" >/dev/null
fi

checksum_file="$output_dir/$bundle_name.SHA256SUMS"
(
    cd "$output_dir"
    sha256sum \
        "$(basename "$portable_archive")" \
        "$(basename "$run_installer")" \
        "$(basename "$debian_package")" \
        > "$(basename "$checksum_file")"
)

printf 'Created Linux packages:\n'
printf '  %s\n' "$portable_archive" "$run_installer" "$debian_package" "$checksum_file"
