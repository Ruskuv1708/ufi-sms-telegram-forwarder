# iPadOS and macOS roadmap

## Product boundary

An Apple client can be a polished LAN companion to the modem; it cannot turn an
iPad or Mac into the modem's native cellular radio. The modem still owns the
SIM, carrier registration, SMS database, and cellular call. The Apple device
would send authenticated commands and carry call audio over the modem's private
network.

## Shared architecture

Build one Swift package containing:

- the token-authenticated control protocol;
- call state and readiness models;
- SMS list/send/mark-read operations;
- audio framing for 8 kHz downlink and 48 kHz uplink;
- local call-history and connection-health models;
- protocol fixtures matching `tests/fake_voice_gateway.py`.

Use SwiftUI for platform-specific shells. The iPad layout should mirror the
Android Calls, Keypad, and Messages structure. The macOS app can use the same
screens plus a menu-bar incoming-call indicator and keyboard shortcuts.

## iPadOS phase

1. Foreground calls and SMS over `Network.framework` sockets.
2. `NSLocalNetworkUsageDescription` explaining the direct modem connection,
   microphone permission for active call audio, and user notifications.
3. CallKit presentation for active incoming/outgoing calls. CallKit supplies
   the system calling UI, but the app remains responsible for the modem LAN
   connection and audio transport.
4. Treat reliable background incoming calls as a separate architecture
   decision. iPadOS can suspend an ordinary local socket. PushKit is for real
   VoIP pushes and would require a compliant push server, creating a cloud
   dependency the current local-only product intentionally avoids. Do not use
   silent pushes or background modes merely to keep arbitrary polling alive.

Apple requires a local-network purpose string for apps that directly connect
to local hosts. See Apple's
[local-network privacy guidance](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy)
and
[`NSLocalNetworkUsageDescription`](https://developer.apple.com/documentation/bundleresources/information-property-list/nslocalnetworkusagedescription).
Call presentation should follow Apple's
[CallKit documentation](https://developer.apple.com/documentation/CallKit).

## macOS phase

1. Reuse the Swift protocol package and SwiftUI view models.
2. Use Core Audio for duplex call audio and system notifications for ringing.
3. Support a signed app bundle, menu-bar mode, launch-at-login through
   `SMAppService`, and Keychain storage for the pairing token.
4. Offer the Mac App Store build after sandbox/network/audio entitlements are
   validated. A direct-download build must be Developer ID signed and
   notarized with `notarytool`; Apple explains the process in
   [Notarizing macOS software before distribution](https://developer.apple.com/documentation/Security/notarizing-macos-software-before-distribution).

## Delivery prerequisites

- Apple Developer Program membership owned by the publisher;
- a Mac with the current Xcode release;
- unique iPadOS/macOS bundle identifiers and signing certificates;
- App Store Connect records, support/privacy URLs, screenshots, review notes,
  and test modem access for App Review;
- a final decision on local-only foreground ringing versus an optional,
  privacy-reviewed push relay for reliable iPad background calls.

The recommended first Apple deliverable is the macOS companion because desktop
background networking and release testing fit the existing local gateway more
naturally. Build iPadOS from the same Swift package once foreground calling and
SMS are stable.
