# UFI SMS Telegram Forwarder and Call Gateway

SMS forwarding and incoming-call support for Qualcomm-based UFI003 LTE USB
modems. The project runs privileged services on the modem's embedded Android
4.4 system and provides clients for Linux and Android tablets.

The modem-resident Android app is the recommended mode: it survives computer
shutdowns and USB reconnects, starts after modem reboot, keeps a persistent
delivery queue, retries after network failures, and suppresses duplicate SMS
notifications.

The optional voice gateway rings on a connected Android tablet or Linux
computer, answers and hangs up incoming cellular calls, and carries two-way
audio over the modem's private Wi-Fi LAN.

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
- Displays incoming cellular calls on an Android tablet with a full-screen
  alert, caller number, Answer and Hang up controls, and two-way audio.
- Provides a small Linux call window and matching command-line controls.
- Detects and repairs the firmware's LTE-only reset after a cold boot, and
  displays a persistent recovery count in the tablet UI.

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
android-forwarder/       Headless Android 4.4 SMS-to-Telegram app
android-network-guard/   Phone-UID radio-mode recovery service
android-voice-gateway/   System-UID call control and audio LAN gateway
android-tablet-client/   Android 8+ incoming-call client
tests/                   Local voice-client integration fixture
ufi_sms.py               Linux USB/AT SMS receiver and fallback forwarder
ufi_voice.py             Linux voice setup, CLI, and desktop window
ufi-sms.service          Optional systemd user service for SMS fallback
```

## SMS forwarding on the modem

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

## Incoming-call gateway

### Important network limitation

The tested UFI003 firmware does not contain a working IMS stack, so this is
not an end-to-end VoLTE implementation. Ucell delivers an incoming call by
moving the modem from LTE to WCDMA/HSPA for a circuit-switched voice call.
After the call ends, mobile data returns to LTE automatically.

The firmware normally selects LTE-only mode after a cold boot, which makes
incoming callers hear a busy signal. `android-network-guard` applies automatic
LTE/GSM/WCDMA mode at boot, again after 30 seconds, 2 minutes, and 5 minutes,
and then periodically as a safety check. The tablet displays both current call
readiness and the number of times an unsafe LTE-only setting was recovered.

### Voice requirements

- The exact tested UFI003 hardware and firmware listed above
- ADB access to the modem for installation
- JDK 17, Android SDK platform 19 and build-tools 35.0.0
- Android SDK platform 35 to build the tablet client
- Platform signing keys matching the modem's firmware certificate
- A tablet or Linux computer connected to the modem's `192.168.100.0/24` LAN

The tested firmware was signed with the public AOSP Android 4.4 platform test
certificate. The build scripts expect matching keys under
`~/.cache/ufi-sms-android/aosp-platform/` and verify the certificate
fingerprint before signing. Platform-signed applications are highly
privileged. Do not install these APKs on unrelated hardware or use a key that
does not exactly match a device you own.

### Build and pair

```bash
./android-network-guard/build.sh
./android-voice-gateway/build.sh
./android-tablet-client/build.sh

./ufi_voice.py setup --modem-serial MODEM_ADB_SERIAL
./ufi_voice.py setup-tablet --tablet-serial TABLET_ADB_SERIAL
```

`setup` generates a random 256-bit LAN token and stores it in
`~/.config/ufi-voice-gateway/client.json` with user-only permissions. The
token is transferred through an ADB-only configuration component; it is not
embedded in source code or an APK. Tablet setup also grants the required
microphone and notification permissions and exempts the foreground call
monitor from Android idle mode.

### Use from Linux

```bash
./ufi_voice.py status
./ufi_voice.py ui
./ufi_voice.py answer
./ufi_voice.py hangup
```

The status output includes `callReady`, `preferredNetworkMode`, and
`modeRecoveries`. Mode `9` means automatic LTE/3G fallback is ready. Mode `11`
means LTE-only and incoming calls may be reported as busy until the guard
repairs it.

The tablet client keeps a visible foreground notification so Android does not
suspend call monitoring. Use headphones when practical to reduce acoustic
echo. Only one audio client can use a call at a time.

### Current voice scope

- Incoming calls, caller display, answer, hang up, ringtone, and two-way audio
- Automatic LTE-to-HSPA call fallback and return to LTE data
- Android tablet and Linux desktop clients on the local modem LAN
- One active audio client at a time

Outgoing dialing, emergency calling, supplementary services, and true IMS
VoLTE are not implemented. Calls remain ordinary carrier calls and may incur
normal operator charges.

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
- Voice servers bind only to `192.168.100.1`, reject clients outside the local
  `/24`, and require the random token before every control or audio session.
- Caller numbers and call audio remain on the local modem LAN. The voice
  components do not send them to Telegram or any cloud service.

## License

Copyright (c) 2026 Kuvatov Ruslan Baxtiyarovich.

Licensed under the [MIT License](LICENSE).
