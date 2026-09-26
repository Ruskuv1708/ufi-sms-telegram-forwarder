# UFI Phone desktop

The desktop client provides calls, a keypad, local call history, direct SMS,
and two-way call audio over the modem's authenticated private-LAN protocol.

Run from source:

```bash
python3 desktop/ufi_phone_desktop.py
```

Linux audio uses the existing PipeWire/PulseAudio `parec` and `paplay`
fallback. The Windows package includes the optional `sounddevice`/PortAudio
backend. Keep the desktop on the modem's local network and pair the gateway
once with `./ufi_setup.py install` or `./ufi_voice.py setup`.

Build a native executable with Python 3.12+ and PyInstaller:

```bash
python -m pip install pyinstaller
# Windows only:
python -m pip install -r desktop/requirements-windows.txt
python -m PyInstaller --clean --noconfirm desktop/UFI-Phone.spec
```

Release builds are produced for Linux and Windows by GitHub Actions. The
pairing token and message/call history are never embedded into the executable.

## Linux packages

Create all Linux packages from a built or downloaded PyInstaller binary:

```bash
./desktop/linux/build-packages.sh --binary /path/to/UFI-Phone
```

This writes four versioned files to `dist/`:

- A distribution-independent `.run` installer for the current user. It adds
  the application-menu entry and asks whether to create a launch icon on the
  desktop.
- A portable `.tar.gz` bundle that runs through `AppRun` without installation.
- An `amd64`/`arm64` Debian package for Debian, Ubuntu, Linux Mint, and related
  distributions.
- A `SHA256SUMS` file covering all three packages.

Install the distribution-independent package:

```bash
chmod +x dist/UFI-Phone-0.5.0-linux-x86_64.run
./dist/UFI-Phone-0.5.0-linux-x86_64.run
```

For an unattended user install, pass `--desktop-shortcut` or
`--no-desktop-shortcut`. The source-tree installer accepts the same options:

```bash
./desktop/install-linux.sh --desktop-shortcut /path/to/UFI-Phone
```

Remove a user installation with
`~/.local/share/ufi-phone/uninstall.sh`. The uninstaller keeps pairing data,
call history, and messages. Install the Debian package system-wide with:

```bash
sudo apt install ./dist/ufi-phone_0.5.0_amd64.deb
```

System packages add an application-menu entry but do not modify any specific
user's desktop. Use the `.run` package when the desktop-icon prompt is wanted.
Official Linux release binaries target glibc 2.35 or later (for example,
Ubuntu 22.04, Debian 12, and Linux Mint 21 or later).

The repository also includes `windows/UFI-Phone.iss` for producing a standard
Inno Setup installer from the Windows executable. The GitHub artifact remains
portable and can run without an installer.
