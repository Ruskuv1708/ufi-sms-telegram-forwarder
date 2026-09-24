# UFI Phone Companion Privacy Policy

Effective date: September 24, 2026

UFI Phone Companion is an open-source local-network companion for compatible
UFI modem hardware. It is published by Kuvatov Ruslan Baxtiyarovich (the
“Developer”).

## Data handled by the app

The app can handle phone numbers, SMS sender and recipient information, SMS
content, call state and history, microphone audio during an active call, modem
status, and a randomly generated modem-pairing token. These data are needed to
provide the call and messaging features selected by the user.

The Developer does not operate a server for the app and does not receive,
collect, sell, or use these data for advertising or analytics. Calls, SMS, and
the pairing token travel only between the user's Android device and the user's
modem on their local network. Microphone audio is streamed only during an
active call and is not recorded by the app.

The app stores synced messages, call history, and pairing settings in its
private application storage. Android notifications may display caller or SMS
sender information according to the user's notification settings.

## Permissions

- Microphone: sends the user's voice to the modem during an active call.
- Notifications and full-screen call alerts: announces incoming calls and SMS.
- Network and connected-device foreground service: maintains the local modem
  connection while monitoring for calls and messages.
- Start at boot and wake lock: restores monitoring after a device restart.

## Sharing and third parties

The app does not include advertising, analytics, tracking SDKs, or cloud SMS
forwarding. The mobile network operator processes ordinary calls and SMS under
the operator's own terms. The repository contains a separate, optional legacy
Telegram forwarder, but it is not used or enabled by UFI Phone Companion.

## Retention and deletion

Messages and call history remain on the Android device until replaced by the
local retention limit, cleared in Android settings, or removed by uninstalling
the app. Modem-side records follow the modem's own storage behavior. Users can
delete all app-held data by choosing Clear storage in Android settings or by
uninstalling the app.

## Security

The app authenticates each modem connection with a randomly generated token
that is provisioned through ADB and kept in private application storage. The
connection is restricted to the modem's private LAN. Users should keep the
modem Wi-Fi password private and should not expose the gateway ports to the
internet.

## Changes and contact

Material changes will be published in this repository before a new release.
Privacy questions can be submitted to the project maintainer through the
[repository issue tracker](https://github.com/Ruskuv1708/ufi-sms-telegram-forwarder/issues).
