# Google Play Data safety worksheet

Use this as a Console worksheet; verify every answer again against the final
binary and the policy wording shown by Play Console.

## Proposed declarations

- Does the app collect or share required user-data categories with the
  developer or a third party? **No.** Processing is on the user's Android
  device and modem; the developer has no receiving server.
- Is data shared for advertising, analytics, personalization, or fraud
  prevention? **No.** The app contains none of those SDKs.
- Can users request deletion? **Yes, locally.** Clear app storage or uninstall
  the app. No developer-held account or server record exists.
- Is an account required? **No.**
- Are SMS, phone numbers, call history, and microphone audio handled? **Yes,
  only as on-device/local-network app functionality.** Explain this clearly in
  the privacy policy even when it is outside Play's “collected” definition.

## Final-binary checks

- Confirm there are no analytics or crash-reporting dependencies.
- Confirm the optional Telegram forwarder is not packaged in the Play app.
- Confirm microphone audio is not recorded to storage.
- Confirm screenshots and review videos contain synthetic data only.
- Re-evaluate the form if any relay, diagnostics upload, or cloud push service
  is added later.
