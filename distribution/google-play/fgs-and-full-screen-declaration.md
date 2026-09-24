# Foreground service and full-screen intent declaration

## Foreground service types

`connectedDevice`

The app keeps an authenticated socket connection to a modem owned by the user,
polling for carrier call state and SMS while the user expects the companion to
remain reachable. If the service stops, incoming call and message alerts do
not arrive reliably.

`microphone`

After the user answers or places a call, the service captures microphone audio
and streams it to the user's modem for the duration of that active call. Audio
capture stops when the call ends or the user disconnects audio.

## Full-screen intent

The app's core purpose includes receiving calls. `USE_FULL_SCREEN_INTENT` is
used only for a real incoming carrier call reported by the paired modem, so the
user can see Answer and Hang up controls on a locked or sleeping Android
device. It is not used for promotions or ordinary messages.

## Review-video script

1. Show the Android device connected to the UFI modem Wi-Fi.
2. Open UFI Phone and show “Modem · LTE · Ready.”
3. Put the app in the background and lock the screen.
4. Place a non-emergency test call to the modem SIM.
5. Show the full-screen incoming-call UI, answer, connect audio, and hang up.
6. Open Android's active-app/foreground-service surface and explain that the
   connected-device service provides ongoing call and SMS monitoring.
7. Place an outgoing test call and show that microphone access exists only
   during active call audio.

Use a dedicated test SIM and synthetic contact names/numbers in the video.
