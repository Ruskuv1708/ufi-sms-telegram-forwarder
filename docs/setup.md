# UFI Phone setup

UFI Phone has one recommended setup path on the computer connected to the
modem by USB:

```bash
./setup.sh
```

The guided setup detects the exact supported modem profile, selects one
connected Android 8+ tablet when present, shows the full plan, builds the
components, installs them, pairs the clients, and verifies the private-LAN
gateway. It never enables Telegram.

## Before the first run

1. Use a Linux setup computer. Windows and Linux can both run the finished
   desktop app, but privileged modem provisioning currently uses Linux build
   tools.
2. Connect the modem by USB and authorize ADB.
3. Optionally connect the Android tablet and accept its USB-debugging prompt.
4. Run the read-only check:

   ```bash
   ./ufi_setup.py doctor
   ```

The doctor identifies supported hardware without printing the SIM identity,
SMS contents, phone numbers, or pairing secret. Installation remains locked
when the modem does not exactly match a tested hardware profile.

## Common paths

Set up the modem and one detected tablet:

```bash
./setup.sh
```

Set up the modem for the desktop app only:

```bash
./setup.sh --desktop-only
```

Preview device selection without changing anything:

```bash
./setup.sh --dry-run
```

Choose devices only when more than one matching device is attached:

```bash
./setup.sh --modem-serial MODEM_SERIAL --tablet-serial TABLET_SERIAL
```

Reuse already built APKs:

```bash
./setup.sh --skip-build
```

## Add another Linux or Windows computer

On the computer that already provisioned the modem, create a private pairing
file:

```bash
./ufi_setup.py pairing --output ~/Downloads/my-modem.ufi-phone
```

Move that file to the other computer using a trusted method, then double-click
it or choose **Connection → Import pairing file** in UFI Phone. Delete the
transferred copy afterward. The file contains the modem access key; do not
email it, upload it publicly, or keep it in a shared folder.

## What remains device-specific

The desktop and tablet clients are reusable, but the two privileged modem
services are firmware-specific. Adding another inexpensive Wi-Fi USB modem
requires a reviewed hardware profile, proof of telephony/audio capabilities,
the matching platform certificate and signing key, and real SMS/call tests.
The installer does not offer an unsafe override.

If setup stops, run `./ufi_setup.py doctor` again. It reports missing ADB,
Android build tools, JDK, or the tested platform key separately so the failed
prerequisite can be repaired without repeating unrelated steps.
