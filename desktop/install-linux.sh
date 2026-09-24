#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
binary="${1:-$project_dir/dist/UFI-Phone}"

if [[ ! -x "$binary" ]]; then
    echo "UFI Phone executable not found: $binary" >&2
    echo "Build it first with PyInstaller or pass a release binary as the first argument." >&2
    exit 1
fi

install -Dm755 "$binary" "$HOME/.local/bin/UFI-Phone"
install -Dm644 "$project_dir/assets/ufi-phone.png" \
    "$HOME/.local/share/icons/hicolor/512x512/apps/ufi-phone.png"
install -Dm644 "$project_dir/desktop/linux/ufi-phone.desktop" \
    "$HOME/.local/share/applications/ufi-phone.desktop"

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$HOME/.local/share/applications"
fi

echo "UFI Phone was installed for the current user."
