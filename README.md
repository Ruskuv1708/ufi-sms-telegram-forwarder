# UFI SMS Telegram Forwarder

Autonomous SMS-to-Telegram forwarding for Qualcomm-based UFI003 LTE USB
modems. The project can run directly on the modem's embedded Android 4.4
system, or on a connected Linux computer as a fallback.

The modem-resident Android app is the recommended mode: it survives computer
shutdowns and USB reconnects, starts after modem reboot, keeps a persistent
delivery queue, retries after network failures, and suppresses duplicate SMS
notifications.

## Features

- Receives Android `SMS_RECEIVED` broadcasts and periodically scans the inbox
  as a recovery path.
- Forwards sender, timestamp, and message text through the Telegram Bot API.
- Keeps failed deliveries in a private SQLite queue and retries every five
  minutes or when connectivity returns.
- Runs automatically after the modem reboots.
- Stores the Telegram token in Android private app data, never in source code
  or the APK.
- Includes a Linux CLI for reading, archiving, deleting, and forwarding SMS
  over the modem's Qualcomm USB AT interface.

## Tested hardware

- Generic **LTE 4G Wi-Fi Dongle / UFI003**
- USB ID `05c6:90b4`
- Qualcomm MSM8916, ARMv7
- Android 4.4.4 / API 19
- Baseband observed during development: `UFI003_CT 20220903`

UFI-branded dongles are sold with several unrelated chipsets and firmware
variants. Verify the USB ID and Android/ADB availability before installing.

## Repository layout

```text
android-forwarder/   Headless Android 4.4 SMS-to-Telegram app
ufi_sms.py           Linux USB/AT command-line receiver and fallback forwarder
ufi-sms.service      Optional systemd user service for the Linux fallback
```

## Modem-resident Android app

### Requirements

- ADB access to the modem
- JDK 17
- Android SDK platform 19
- Android build-tools 35.0.0

`android-forwarder/build.sh` uses `JAVA_HOME` and `ANDROID_SDK_ROOT` when set.
Without them it looks for a local toolchain under
`~/.cache/ufi-sms-android/toolchain/`.

### Build and install

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk

./android-forwarder/build.sh
adb install -r ./android-forwarder/build/ufi-sms-forwarder.apk
```

The build creates a private, persistent signing key under
`~/.config/ufi-sms/`. Keep that directory safe so future APK updates retain
access to the existing app data.

### Connect Telegram

Create a bot with [BotFather](https://t.me/BotFather), send `/start` to the
bot, and run the private setup assistant. Token input is hidden:

```bash
./ufi_sms.py telegram-setup --no-restart
./android-forwarder/control.py configure
```

Useful checks:

```bash
./android-forwarder/control.py status
./android-forwarder/control.py test
./android-forwarder/control.py drain
```

`control.py` reads `~/.config/ufi-sms/telegram.json`, requires private file
permissions, and transfers the configuration over ADB without printing the
token or placing it in shell history.

The app has no launcher window. Telegram is its user interface.

## Linux fallback

The standalone Python tool talks directly to USB interface 2 using PyUSB. It
uses [uv](https://docs.astral.sh/uv/) to install the pinned `pyusb` dependency
declared inside the script.

```bash
./ufi_sms.py status
./ufi_sms.py setup --storage SM
./ufi_sms.py inbox
./ufi_sms.py watch --storage SM --notify
```

Other commands include `read`, `delete`, `saved`, `telegram-setup`,
`telegram-status`, and `telegram-test`. Run `./ufi_sms.py --help` for the full
command list.

If access to `05c6:90b4` is denied, install an appropriate udev rule for your
Linux distribution, for example:

```udev
SUBSYSTEM=="usb", ATTR{idVendor}=="05c6", ATTR{idProduct}=="90b4", MODE="0660", TAG+="uaccess"
```

To install the optional user service:

```bash
install -Dm755 ufi_sms.py ~/.local/share/ufi-sms/ufi_sms.py
install -Dm644 ufi-sms.service ~/.config/systemd/user/ufi-sms.service
systemctl --user daemon-reload
systemctl --user enable --now ufi-sms.service
```

Do not run the Linux watcher and the Android forwarder at the same time unless
you intentionally want duplicate delivery paths.

## Security and privacy

- Never commit or paste a Telegram bot token. Revoke any token that has been
  exposed.
- Local Telegram configuration and archived SMS data are created with private
  file permissions.
- The Android app uses the modem's system `curl` with a bundled trusted root
  certificate because the tested Android 4.4 Java TLS stack cannot negotiate
  with the current Telegram endpoint.
- Enabling forwarding sends SMS sender information and message contents to
  Telegram. Review Telegram's privacy terms before use.
- The ADB control receiver is restricted to Android's privileged shell
  permission; ordinary installed apps cannot reconfigure it.

## License

[MIT](LICENSE)
