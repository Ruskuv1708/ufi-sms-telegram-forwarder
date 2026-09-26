#!/usr/bin/env bash
set -euo pipefail

desktop_dir=""
if command -v xdg-user-dir >/dev/null 2>&1; then
    desktop_dir="$(xdg-user-dir DESKTOP 2>/dev/null || true)"
fi
if [[ -z "$desktop_dir" || "$desktop_dir" == "$HOME" ]]; then
    desktop_dir="$HOME/Desktop"
fi

desktop_shortcut="$desktop_dir/ufi-phone.desktop"
if [[ -f "$desktop_shortcut" ]] && grep -q '^X-UFI-Phone-Managed=true$' "$desktop_shortcut"; then
    rm -f "$desktop_shortcut"
fi

rm -f \
    "$HOME/.local/bin/UFI-Phone" \
    "$HOME/.local/share/applications/ufi-phone.desktop" \
    "$HOME/.local/share/icons/hicolor/512x512/apps/ufi-phone.png" \
    "$HOME/.local/share/metainfo/io.github.ruskuv1708.ufiphone.metainfo.xml" \
    "$HOME/.local/share/ufi-phone/uninstall.sh"
rmdir "$HOME/.local/share/ufi-phone" 2>/dev/null || true

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$HOME/.local/share/applications"
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t "$HOME/.local/share/icons/hicolor" || true
fi

echo "UFI Phone was removed. Pairing data and call history were kept in your config directory."
