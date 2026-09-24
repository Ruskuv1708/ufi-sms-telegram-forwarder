# Google Play release checklist

## Automated deliverables

- [x] Package `com.ufi.voiceclient`
- [x] Minimum API 26; target and compile API 36
- [x] Android App Bundle build via Gradle 9.6 / Android Gradle Plugin 9.4.1
- [x] Optional CI signing through repository secrets
- [x] Foreground-service types declared in the manifest
- [x] Privacy policy, Data safety worksheet, listing copy, and declaration draft
- [x] Unit, APK, AAB, and on-device responsive-layout checks

## Publisher actions

- [ ] Create or verify the personal Google Play developer account.
- [ ] Add a private public-support email to the listing and privacy policy.
- [ ] Generate an upload key, enroll in Play App Signing, and store the key and
      passwords outside the repository.
- [ ] Add `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`,
      `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` as GitHub Actions secrets.
- [ ] Create the app in Play Console and upload the signed AAB.
- [ ] Complete App access, Ads, Content rating, Target audience, Data safety,
      Privacy policy, Foreground service, and Full-screen intent forms.
- [ ] Use the public rendered policy URL:
      `https://github.com/Ruskuv1708/ufi-sms-telegram-forwarder/blob/main/distribution/google-play/privacy-policy.md`
- [ ] Upload phone and tablet screenshots made with synthetic data.
- [ ] Upload the foreground-service/full-screen-intent demonstration video.
- [ ] If the personal account was created after November 13, 2023, complete a
      closed test with at least 12 opted-in testers for 14 continuous days
      before requesting production access.
- [ ] Verify the exact current Console requirements immediately before launch.

Only the Android companion belongs in Play. The modem gateway and network
guard require device-specific platform signing and must remain in the guarded
ADB installer.

## Official references

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Android App Bundle guide](https://developer.android.com/guide/app-bundle)
- [Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [Foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en-GB)
- [Production access for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
