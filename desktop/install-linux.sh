#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
shortcut_mode="ask"
binary=""

usage() {
    cat <<'EOF'
Usage: install-linux.sh [OPTIONS] [UFI-PHONE-BINARY]

Install UFI Phone for the current user.

Options:
  --desktop-shortcut      Create a launch icon on the desktop
  --no-desktop-shortcut   Do not create (or remove our existing) desktop icon
  -h, --help              Show this help

When run interactively, the installer asks whether to create a desktop icon.
The application-menu entry is always installed.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --desktop-shortcut)
            shortcut_mode="yes"
            shift
            ;;
        --no-desktop-shortcut)
            shortcut_mode="no"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
        *)
            if [[ -n "$binary" ]]; then
                echo "Only one UFI Phone binary may be supplied." >&2
                exit 2
            fi
            binary="$1"
            shift
            ;;
    esac
done

# The same installer works from the source tree and from the portable bundle.
if [[ -f "$script_dir/usr/share/applications/ufi-phone.desktop" ]]; then
    desktop_file="$script_dir/usr/share/applications/ufi-phone.desktop"
    icon_file="$script_dir/usr/share/icons/hicolor/512x512/apps/ufi-phone.png"
    metainfo_file="$script_dir/usr/share/metainfo/io.github.ruskuv1708.ufiphone.metainfo.xml"
    uninstaller_file="$script_dir/uninstall.sh"
    binary="${binary:-$script_dir/usr/bin/UFI-Phone}"
else
    desktop_file="$project_dir/desktop/linux/ufi-phone.desktop"
    icon_file="$project_dir/assets/ufi-phone.png"
    metainfo_file="$project_dir/desktop/linux/io.github.ruskuv1708.ufiphone.metainfo.xml"
    uninstaller_file="$project_dir/desktop/uninstall-linux.sh"
    binary="${binary:-$project_dir/dist/UFI-Phone}"
fi

if [[ ! -x "$binary" ]]; then
    echo "UFI Phone executable not found: $binary" >&2
    echo "Build it first with PyInstaller or pass a release binary as the first argument." >&2
    exit 1
fi

if [[ ! -f "$desktop_file" || ! -f "$icon_file" ]]; then
    echo "The UFI Phone desktop entry or icon is missing." >&2
    exit 1
fi

shortcut_action="preserve"
if [[ "$shortcut_mode" == "ask" && -t 0 && -t 1 ]]; then
    read -r -p "Create a UFI Phone launch icon on the desktop? [y/N] " reply
    case "$reply" in
        y|Y|yes|YES|Yes) shortcut_action="create" ;;
        *) shortcut_action="remove" ;;
    esac
elif [[ "$shortcut_mode" == "yes" ]]; then
    shortcut_action="create"
elif [[ "$shortcut_mode" == "no" ]]; then
    shortcut_action="remove"
fi

install -Dm755 "$binary" "$HOME/.local/bin/UFI-Phone"
install -Dm644 "$icon_file" \
    "$HOME/.local/share/icons/hicolor/512x512/apps/ufi-phone.png"

# Use an absolute executable path so launchers work immediately even when a
# desktop session has not added ~/.local/bin to PATH yet.
desktop_exec="$HOME/.local/bin/UFI-Phone"
quoted_desktop_exec="${desktop_exec//\\/\\\\}"
quoted_desktop_exec="${quoted_desktop_exec//\"/\\\"}"
quoted_desktop_exec="${quoted_desktop_exec//\`/\\\`}"
quoted_desktop_exec="${quoted_desktop_exec//\$/\\\$}"
rendered_desktop="$(mktemp "${TMPDIR:-/tmp}/ufi-phone-desktop.XXXXXX")"
cleanup_rendered_desktop() {
    rm -f -- "$rendered_desktop"
}
trap cleanup_rendered_desktop EXIT HUP INT TERM
while IFS= read -r desktop_line || [[ -n "$desktop_line" ]]; do
    case "$desktop_line" in
        Exec=*) printf 'Exec="%s"\n' "$quoted_desktop_exec" ;;
        TryExec=*) printf 'TryExec=%s\n' "$desktop_exec" ;;
        *) printf '%s\n' "$desktop_line" ;;
    esac
done < "$desktop_file" > "$rendered_desktop"
install -Dm644 "$rendered_desktop" \
    "$HOME/.local/share/applications/ufi-phone.desktop"
rm -f -- "$rendered_desktop"
trap - EXIT HUP INT TERM
if [[ -f "$metainfo_file" ]]; then
    install -Dm644 "$metainfo_file" \
        "$HOME/.local/share/metainfo/io.github.ruskuv1708.ufiphone.metainfo.xml"
fi
if [[ -f "$uninstaller_file" ]]; then
    install -Dm755 "$uninstaller_file" "$HOME/.local/share/ufi-phone/uninstall.sh"
fi

desktop_dir=""
if command -v xdg-user-dir >/dev/null 2>&1; then
    desktop_dir="$(xdg-user-dir DESKTOP 2>/dev/null || true)"
fi
if [[ -z "$desktop_dir" || "$desktop_dir" == "$HOME" ]]; then
    desktop_dir="$HOME/Desktop"
fi
desktop_shortcut="$desktop_dir/ufi-phone.desktop"

if [[ "$shortcut_action" == "create" ]]; then
    install -Dm755 "$HOME/.local/share/applications/ufi-phone.desktop" "$desktop_shortcut"
    printf '\nX-UFI-Phone-Managed=true\n' >> "$desktop_shortcut"
    if command -v gio >/dev/null 2>&1; then
        gio set "$desktop_shortcut" metadata::trusted true >/dev/null 2>&1 || true
    fi
elif [[ "$shortcut_action" == "remove" && -f "$desktop_shortcut" ]] && \
        grep -q '^X-UFI-Phone-Managed=true$' "$desktop_shortcut"; then
    rm -f "$desktop_shortcut"
fi

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$HOME/.local/share/applications"
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t "$HOME/.local/share/icons/hicolor" || true
fi

echo "UFI Phone was installed for the current user."
echo "Launch it from the application menu or run: $HOME/.local/bin/UFI-Phone"
if [[ "$shortcut_action" == "create" ]]; then
    echo "Desktop launch icon: $desktop_shortcut"
fi
