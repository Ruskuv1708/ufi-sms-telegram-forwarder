# UFI Phone for Linux

UFI Phone provides desktop calls, SMS conversations, a keypad, local call
history, and two-way call audio through a paired UFI003 modem gateway.

## Install

The easiest distribution-independent installation is the `.run` package:

```bash
chmod +x UFI-Phone-VERSION-linux-ARCH.run
./UFI-Phone-VERSION-linux-ARCH.run
```

The installer adds UFI Phone to the current user's application menu and asks
whether to create a launch icon on the desktop. It does not require root.

For unattended installation, pass `--desktop-shortcut` or
`--no-desktop-shortcut`. To remove the user installation later, run
`~/.local/share/ufi-phone/uninstall.sh`.

Debian, Ubuntu, Linux Mint, and related distributions can install the `.deb`
package system-wide:

```bash
sudo apt install ./ufi-phone_VERSION_ARCH.deb
```

The Debian package adds an application-menu launcher. Desktop shortcuts are
deliberately left to the desktop environment or the user-level `.run`
installer because system package installation must not modify one user's
desktop.

## Run without installing

Extract the portable `.tar.gz` package and launch `AppRun`:

```bash
tar -xzf UFI-Phone-VERSION-linux-ARCH.tar.gz
./UFI-Phone-VERSION-linux-ARCH/AppRun
```

## Runtime notes

- Pair the modem gateway before launching UFI Phone.
- The desktop app communicates only over the modem's authenticated private
  LAN protocol.
- Two-way audio uses `parec` and `paplay`, supplied by PulseAudio utilities
  and compatible with PipeWire's PulseAudio layer.
- Official release binaries target glibc 2.35 or later (Ubuntu 22.04,
  Debian 12, Linux Mint 21, and suitably recent distributions). Locally built
  packages inherit the compatibility limits of the supplied executable.
- Pairing credentials and call/message data are not bundled in the package.
