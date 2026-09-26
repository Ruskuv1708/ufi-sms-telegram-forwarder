#!/usr/bin/env bash
set -euo pipefail

archive_line="$(awk '/^__UFI_PHONE_ARCHIVE_BELOW__$/ { print NR + 1; exit }' "$0")"
if [[ -z "$archive_line" ]]; then
    echo "This UFI Phone installer is damaged: embedded archive not found." >&2
    exit 1
fi

extract_dir="$(mktemp -d "${TMPDIR:-/tmp}/ufi-phone-installer.XXXXXX")"
cleanup() {
    rm -rf -- "$extract_dir"
}
trap cleanup EXIT HUP INT TERM

tail -n "+$archive_line" "$0" | tar -xzf - -C "$extract_dir"
installer="$(find "$extract_dir" -mindepth 2 -maxdepth 2 -type f -name install.sh -print -quit)"
if [[ -z "$installer" || ! -x "$installer" ]]; then
    echo "This UFI Phone installer is damaged: install script not found." >&2
    exit 1
fi

"$installer" "$@"
exit 0

__UFI_PHONE_ARCHIVE_BELOW__
