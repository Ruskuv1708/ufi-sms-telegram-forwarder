# UFI Phone and SMS Gateway

Direct SMS and cellular-call support for Qualcomm-based UFI003 LTE USB modems.
The project runs privileged services on the modem's embedded Android 4.4
system and provides clients for Android, Linux, and Windows.

The recommended interface is the **UFI Phone** tablet app. It reads and sends
SMS directly through the modem's private LAN, places and receives ordinary
cellular calls, carries two-way call audio, and keeps local call history. It
does not require Telegram or another cloud service.

The older SMS-to-Telegram forwarder remains available as an optional,
independent compatibility path.

## Features

- Reads stored SIM messages and captures new `SMS_RECEIVED` broadcasts on the
  modem.
- Displays conversations and sends SMS directly in the UFI Phone tablet app.
- Uses an authenticated, LAN-only connection between the tablet and modem;
  SMS contents do not need to leave the local network.
- Runs automatically after modem and tablet reboots.
- Includes a Linux CLI for reading, archiving, deleting, and forwarding SMS
  over the modem's Qualcomm USB AT interface.
- Displays incoming cellular calls on an Android tablet with a full-screen
  alert, caller number, Answer and Hang up controls, and two-way audio.
- Places ordinary cellular calls from the tablet and keeps the latest 100
  incoming and outgoing call records locally on that tablet.
- Keeps the latest 250 synced SMS records in the tablet's private app data.
- Optionally forwards SMS through the Telegram Bot API with a persistent retry
  queue when the legacy forwarder is enabled.
- Provides a small Linux call window and matching command-line controls.
- Provides a full Linux/Windows desktop client with calls, keypad, local call
  history, conversations, SMS sending, and optional duplex audio.
- Detects and repairs the firmware's LTE-only reset after a cold boot, and
  displays a persistent recovery count in the tablet UI.

## Quick start

Start with the read-only device doctor. It reports only a small whitelist of
non-secret hardware properties and never prints pairing tokens, SMS, IMSI, or
call contents:

```bash
./ufi_setup.py doctor
```

For the exact tested hardware profile, one command can build the three Android
components, safely install the privileged modem services, pair the tablet, and
leave Telegram disabled:

```bash
./ufi_setup.py install \
  --modem-serial MODEM_ADB_SERIAL \
  --tablet-serial TABLET_ADB_SERIAL
```

The installer refuses unverified look-alike hardware. Cheap “UFI” devices can
use the same enclosure while containing unrelated chipsets, Android builds,
or signing certificates. See
[hardware profiles](hardware-profiles.json) and the
[profile guide](docs/adding-hardware-profiles.md) before porting a new model.

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
android-forwarder/       Optional headless Android 4.4 Telegram forwarder
android-network-guard/   Phone-UID radio-mode recovery service
android-voice-gateway/   System-UID call, SMS, and audio LAN gateway
android-tablet-client/   Android 8+ UFI Phone app for calls and SMS
desktop/                 Linux/Windows UFI Phone desktop application
distribution/            Google Play listing, privacy, and review materials
docs/                    Compatibility research and Apple platform roadmap
presentations/           Final TUIT decks, sources, assets, and validation
release-artifacts/       Locally downloaded CI packages (ignored by Git)
tests/                   Local voice-client integration fixture
ufi_setup.py              Read-only doctor and guarded one-command installer
ufi_sms.py               Linux USB/AT SMS receiver and fallback forwarder
ufi_voice.py             Linux voice setup, CLI, and desktop window
ufi-sms.service          Optional systemd user service for SMS fallback
```

## Unified project workspace

This directory is the canonical workspace for the whole UFI Phone project.
The application, modem services, desktop clients, research, store-delivery
materials, presentation source, and final presentation files now share this
single Git repository.

- [`presentations/final`](presentations/final/) contains the current English
  and Russian TUIT decks.
- [`presentations/source`](presentations/source/) contains the reproducible
  deck generator.
- [`presentations/validation`](presentations/validation/) contains the final
  validation receipts and rendered slide review images.
- [`release-artifacts`](release-artifacts/) is the local home for packages
  downloaded from the verified GitHub Actions build.

Generated presentation work files, superseded drafts, release binaries, and
the pre-consolidation workspace metadata are retained locally but ignored by
Git. Private runtime data such as pairing tokens and stored SMS remains in the
user configuration directories and is deliberately not copied into this
public repository.

## Direct calls and SMS on the tablet

### Requirements

- The exact tested UFI003 hardware and firmware listed above
- ADB access to the modem and tablet for installation
- JDK 17, Android SDK platform 19 for the modem, and platform 36 for the tablet
- Android Build Tools 35.0.0 for modem builds and 36.0.0 for the companion
- Platform signing keys matching the modem's firmware certificate
- An Android 8+ tablet connected to the modem's `192.168.100.0/24` LAN

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
token is transferred through ADB-only configuration components and is never
embedded in source code or an APK. Tablet setup also grants microphone and
notification permissions and exempts the foreground monitor from Android
idle mode.

Open **UFI Phone** on the tablet:

- **Calls** shows recent incoming, outgoing, and missed calls.
- **Keypad** places an ordinary carrier call. Emergency numbers, short codes,
  and service codes are deliberately blocked.
- **Messages** displays SMS stored by the modem and sends replies through the
  SIM. New messages produce a local tablet notification.

SMS and call commands are accepted only from the modem's private
`192.168.100.0/24` LAN and require the pairing token. Normal carrier charges
can apply to outgoing calls and SMS.

### Google Play build

The tablet companion has a standard Gradle 9.6 / Android Gradle Plugin 9.4.1
build that targets API 36 and produces an Android App Bundle:

```bash
cd android-tablet-client
./gradlew bundleRelease
```

Set `UFI_ANDROID_KEYSTORE`, `UFI_ANDROID_STORE_PASSWORD`,
`UFI_ANDROID_KEY_ALIAS`, and `UFI_ANDROID_KEY_PASSWORD` to create a signed
upload bundle. Without them, Gradle intentionally creates an unsigned review
artifact. The GitHub workflow builds the AAB and Linux/Windows desktop
executables; tagged versions publish release assets. Store copy, privacy and
review declarations are in [`distribution/google-play`](distribution/google-play/).

Only the ordinary Android companion belongs in Google Play. The modem gateway
and network guard require device-specific platform signing and remain behind
the guarded ADB installer.

## Optional Telegram forwarding

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
./android-forwarder/control.py --serial MODEM_ADB_SERIAL configure
```

Useful checks:

```bash
./android-forwarder/control.py --serial MODEM_ADB_SERIAL status
./android-forwarder/control.py --serial MODEM_ADB_SERIAL test
./android-forwarder/control.py --serial MODEM_ADB_SERIAL drain
./android-forwarder/control.py --serial MODEM_ADB_SERIAL disable
./android-forwarder/control.py --serial MODEM_ADB_SERIAL enable
```

`control.py` reads `~/.config/ufi-sms/telegram.json`, requires private file
permissions, and transfers the configuration over ADB without printing the
token or placing it in shell history.

The forwarder has no launcher window. Telegram is its user interface. It is
not needed when using UFI Phone, and it should normally remain disabled to
avoid sending private SMS contents to a cloud service. `disable` cancels the
retry alarm and stops forwarding across reboots without deleting the bot
token, chat ID, or queue; `enable` resumes the preserved configuration.

## Linux USB/AT fallback

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

## Cellular-call details

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

The tested firmware was signed with the public AOSP Android 4.4 platform test
certificate. The build scripts expect matching keys under
`~/.cache/ufi-sms-android/aosp-platform/` and verify the certificate
fingerprint before signing. Platform-signed applications are highly
privileged. Do not install these APKs on unrelated hardware or use a key that
does not exactly match a device you own.

### Use from Linux

```bash
./ufi_voice.py status
./ufi_voice.py ui
./ufi_voice.py answer
./ufi_voice.py hangup
```

`./ufi_voice.py ui` now opens the full UFI Phone desktop client. It includes
Calls, Keypad, Messages, and local call history. The same application is
packaged as a portable Windows executable:

```bash
python3 desktop/ufi_phone_desktop.py
```

Linux call audio uses PipeWire/PulseAudio `parec` and `paplay`. Windows release
builds include a PortAudio backend through `sounddevice`. See
[`desktop/README.md`](desktop/README.md) for local packaging commands.

The status output includes `callReady`, `preferredNetworkMode`, and
`modeRecoveries`. Mode `9` means automatic LTE/3G fallback is ready. Mode `11`
means LTE-only and incoming calls may be reported as busy until the guard
repairs it.

The tablet client keeps a visible foreground notification so Android does not
suspend call or SMS monitoring. Use headphones when practical to reduce
acoustic echo. Only one audio client can use a call at a time.

### Current voice scope

- Incoming and outgoing calls, caller display, answer, hang up, ringtone, and
  two-way audio
- Direct SMS inbox, conversation view, local notifications, and sending
- Local tablet call history with direction, result, time, and approximate
  connected duration
- Automatic LTE-to-HSPA call fallback and return to LTE data
- Android, Linux, and Windows clients on the local modem LAN
- One active audio client at a time

Emergency numbers, short/service codes, supplementary services, and true IMS
VoLTE are not supported. The gateway deliberately rejects emergency and
service-code dialing. Calls remain ordinary carrier calls and may incur normal
operator charges.

## Carrier and platform roadmap

The overall positioning, design principles, support tiers, and milestones are
captured in the [product vision](docs/product-vision.md).

Ucell is the tested reference operator. Mobiuz, Uzmobile, Humans, and Beeline
require the documented SIM acceptance test before they can be advertised as
supported; Perfectum's CDMA and 5G SA/VoNR device paths are incompatible with
this exact UFI003. See the
[Uzbekistan market and operator report](docs/uzbekistan-market-and-operator-compatibility.md).

The recommended Apple sequence is a macOS companion followed by an iPadOS app
sharing one Swift protocol package. Background incoming calls on iPadOS need a
separate, App-Review-compliant push architecture; a local socket cannot simply
stay alive indefinitely. See the
[iPadOS and macOS roadmap](docs/apple-platform-roadmap.md).

## Security and privacy

- Never commit or paste a Telegram bot token. Revoke any token that has been
  exposed.
- Direct UFI Phone SMS stays on the modem's private LAN and does not use
  Telegram.
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
- SMS, caller numbers, and call audio remain on the local modem LAN when using
  UFI Phone. The call/SMS gateway does not send them to Telegram or any cloud
  service.

## License

Copyright (c) 2026 Kuvatov Ruslan Baxtiyarovich.

Licensed under the [MIT License](LICENSE).
