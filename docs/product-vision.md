# Product vision: UFI Phone

## One-line promise

Give inexpensive, compatible cellular modems a familiar private calling and
messaging interface on the screens people already own.

## What the product is

UFI Phone is a local communications appliance with three layers:

1. A small, hardware-specific gateway on the modem owns the SIM, carrier call,
   SMS database, and radio recovery.
2. A token-authenticated protocol exposes only call, message, audio, and health
   operations on the modem's private LAN.
3. Familiar Android, Linux, and Windows clients provide Calls, Keypad,
   Messages, call history, notifications, and audio.

This separation is the product advantage. Users do not need Telegram or a
developer-operated relay, while client design can improve independently of the
low-cost modem firmware.

## Design language

- Familiar before novel: Calls, Keypad, and Messages use the vocabulary and
  hierarchy people recognize from Google Phone and Messages.
- Quiet when healthy: routine sync is invisible; failures and unsafe call mode
  are explicit and actionable.
- One primary action per screen: call, answer, or send.
- Responsive rather than phone-stretched: portrait is focused and landscape
  uses width for centered lists or a two-column keypad.
- Carrier-neutral: UI says modem/network readiness; operator-specific behavior
  belongs in tested compatibility data.
- Honest boundaries: no universal-VoLTE claim, emergency dialing, or hidden
  cloud dependency.

## Support tiers

- **Tested:** exact modem/firmware profile has passed build, security, SMS,
  voice, audio, reboot, and operator acceptance tests. Guarded installer runs.
- **Candidate:** read-only doctor recognizes useful primitives, but porting or
  operator tests are incomplete. Installation remains refused.
- **Unsupported:** radio standard, privilege/signing model, or audio path is
  incompatible. The UI may still inspire a separate implementation, but the
  existing gateway is not advertised for it.

## Delivery shape

- Android companion: direct APK for development and signed AAB for Google Play.
- Modem gateway: source-built, profile-gated ADB install; never a universal
  store binary.
- Linux/Windows: portable desktop binaries, with optional native installers.
- macOS/iPadOS: shared Swift protocol core, macOS first, then an iPad foreground
  companion and a deliberate decision about compliant background ringing.

## Near-term milestones

1. Complete real-SIM acceptance tests for Mobiuz, Uzmobile, Humans, and Beeline.
2. Add a sanitized demo gateway and automated screenshot/review-video mode.
3. Recruit the required Android closed-test cohort and collect reliability,
   battery, audio, and reconnect data.
4. Publish the first signed Android and desktop release from one tag.
5. Port one second modem profile without weakening the exact-match installer.
6. Prototype the shared Swift protocol package and macOS client.

## Measures that matter

- incoming-call readiness after cold boot;
- missed events attributable to disconnect/reconnect behavior;
- call setup success and two-way-audio success by operator;
- time from clean checkout to a paired supported device;
- battery and network usage during 24-hour monitoring;
- support incidents per exact hardware/firmware profile.

The product should grow by adding measured profiles and polished clients, not
by broadening compatibility claims faster than the evidence.
