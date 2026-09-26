# Adding support for another UFI modem

“UFI” describes a product shape, not one hardware platform. Do not install the
privileged gateway because a device has the same case or web interface.

## 1. Produce a redacted probe

Connect the candidate over ADB and run:

```bash
./ufi_setup.py doctor --serial DEVICE_SERIAL --json > candidate-report.json
```

The doctor queries a strict property whitelist. Review the file before sharing
it anyway, and never attach a full `getprop`, modem diagnostic dump, SMS
database, IMEI, IMSI, Wi-Fi password, or pairing configuration.

## 2. Establish the privilege model

The current gateway uses `android.uid.system`; the radio guard uses
`android.uid.phone`. A new profile therefore needs all of the following:

- an Android-based modem with authorized ADB access;
- telephony, SMS, and microphone/audio-route APIs compatible with the source;
- a legally obtained signing certificate/private key that exactly matches the
  device firmware, or a different implementation that does not require shared
  system UIDs;
- a gateway bind address reachable only from the modem LAN;
- a verified safe preferred-network-mode strategy for that radio firmware.

Never reuse this project's tested platform key on a device with a different
platform certificate. Never distribute a vendor's private production key.

## 3. Add and review the profile

Add a narrowly matched entry to `hardware-profiles.json`: product device, SDK,
baseband family, USB ID, LAN address, capabilities, and signing requirements.
Add a fixture report with sensitive identifiers replaced, plus tests proving a
near-match does not pass. Keep installation refusal as the default until SMS,
voice control, audio, reboot recovery, and network isolation all pass.

## 4. Run the acceptance matrix

- Build and package install/uninstall behavior.
- Incoming/outgoing SMS, multipart text, Unicode, and read state.
- Incoming/outgoing calls, missed calls, audio in both directions, and hangup.
- Cold boot and repeated preferred-network-mode recovery.
- LTE data before and after calls.
- Unauthorized LAN client and wrong-token rejection.
- Protocol-v2 challenge freshness, command-tamper rejection, and rejection of
  unpaired or legacy plaintext clients. Upgrade gateway and clients together.
- Each intended Uzbekistan operator/SIM/tariff using the matrix in the
  [operator report](uzbekistan-market-and-operator-compatibility.md).

Mark a profile `tested` only after those results are reproducible. Until then,
publish it as a porting branch or design note rather than weakening the guarded
installer.
