# Changelog

## Unreleased

- Replaced plaintext LAN token transmission with nonce-based, command-bound
  HMAC authentication, connection limits, and protocol compatibility checks.
- Added bounded SMS/archive/retry storage, duplicate-send suppression, safer
  radio recovery, strict hardware gates, signing-key checks, and service
  sandboxing.
- Hardened CI and tagged releases with immutable action pins, dependency pins,
  privileged-source compilation, repository guards, signed-AAB enforcement,
  and release checksums.
- Added a ranked risk register and pre-deployment acceptance checklist.

## 0.5.0 — 2026-09-24

- Added a responsive landscape keypad and tablet-width Calls layout.
- Removed carrier-specific Ucell wording from the reusable client UI.
- Replaced number-derived avatars with familiar phone and message icons.
- Reduced healthy SMS sync noise and simplified the compose action.
- Added the redacted device doctor, tested hardware profile, and guarded
  one-command installer.
- Added a full Linux/Windows desktop client for calls, SMS, and local history.
- Added portable Linux, interactive user-installer, and Debian packages with
  an optional desktop launch icon.
- Added API 36 Android App Bundle builds and multi-platform release automation.
- Added Google Play submission materials, an Apple platform roadmap, and a
  sourced Uzbekistan operator compatibility report.

The modem gateway remains version 0.5.0; the tablet companion is now 0.5.0.
