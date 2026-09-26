# UFI Phone risk register and protection plan

Last reviewed: 2026-09-26

This document covers the tested UFI003 modem, its privileged Android gateway,
the Android tablet client, Linux/Windows desktop clients, direct USB SMS
fallback, packaging, and releases. It separates protections already
implemented from risks that require hardware, carrier, or operational action.

## Security and reliability boundary

The modem is an Android 4.4 router with ADB access and firmware-specific system
signing. Treat it as a dedicated appliance on a private LAN, not as a trusted
general-purpose Android device. UFI Phone intentionally blocks emergency,
service, and short-code dialing; it is not an emergency-call system.

The v2 LAN protocol authenticates each connection with a fresh 256-bit HMAC
challenge. Control proofs are bound to the exact command, so captured proofs
cannot be replayed or attached to a different dial/SMS command. The pairing
secret itself is no longer sent over the network. SMS, status responses, and
raw call audio are still not encrypted end to end, so network isolation remains
mandatory.

## Ranked failure modes

| Severity | Failure mode | Likely consequence | Protection and detection | Residual action |
|---|---|---|---|---|
| Critical | Factory Wi-Fi key or router-admin password remains unchanged | Anyone nearby may join the modem LAN, read unencrypted traffic, disrupt calls, or attack old firmware | LAN source filtering, HMAC command authentication, random pairing token | Change both factory credentials before use; use WPA2/WPA3 with a unique password and disable WPS if the firmware permits it |
| Critical | Android 4.4 modem firmware is obsolete and privileged ADB is enabled | A firmware or local-network compromise controls the whole appliance | Gateway binds only to the modem LAN; installer refuses untested profiles; secrets are never stored in Git | Never expose ADB or gateway ports to WAN; isolate the modem; replace the hardware for sensitive or business-critical use |
| Critical | Platform-signing private key is copied, leaked, or used on another firmware | Privileged APK compromise or unsafe installation | Owner-only key permission check; expected certificate fingerprint required by both privileged build scripts; repository secret guard | Keep the key directory offline/backed up securely; rotate/rebuild firmware if a genuinely private production key is exposed |
| High | Pairing token is sniffed or a command is replayed/modified | Unauthorized calls, SMS, or call control | Protocol v2 challenge-response; per-connection random nonce; command-bound HMAC; constant-time verification; 12-client connection cap | Responses and media remain plaintext; use only a private, trusted modem LAN |
| High | Gateway and clients are upgraded separately | New clients reject the old plaintext protocol, and old clients cannot use v2 | Explicit protocol version checks and actionable incompatibility errors | Upgrade modem gateway, network guard, tablet, and desktop together; keep prior APKs/binaries for rollback |
| High | Radio guard changes preferred mode during a call | Dropped audio or terminated call | Guard defers corrections until telephony reports idle; readiness and recovery counters are exposed to clients | Run long-call and repeated-recovery tests on every firmware/operator profile |
| High | Similar-looking modem receives privileged APKs | Boot loop, lost cellular service, or privilege misuse | Exact product/SDK/baseband/USB/LAN profile plus required telephony/microphone capability checks; lower-level setup repeats the gate | Each new modem requires its own reviewed profile, signing model, and acceptance matrix |
| High | SIM/device SMS storage fills | New SMS may be rejected by the modem | Gateway database retains at most 5,000 messages; desktop archive rotates at 20 MiB with three backups | The SIM/firmware store is separate; monitor and periodically clear it through a trusted tool after backup |
| High | Power loss or USB brownout during a write/call | Disconnect loops, interrupted calls, stale state, archive corruption | Atomic config/history writes, SQLite transactions, reconnect loops, stale-call recovery, bounded systemd restarts | Use a powered USB port/hub; run a cold-boot and power-cycle soak test before relying on the device |
| High | Release tag is built without Android signing secrets | Uninstallable or impersonable store/release artifact | Tagged CI fails closed unless every Android signing secret is present; signed AAB is verified | Protect GitHub environments/secrets and require review for release jobs |
| High | Mutable or compromised build dependency/action | Malicious release artifact | GitHub Actions are commit-pinned; Gradle distribution has SHA-256 pin; Python build/test versions are pinned; Dependabot enabled | Add artifact provenance/code signing and periodically audit pinned updates |
| Medium | Large SMS history exceeds desktop protocol limit | Messages page appears offline | Control response ceiling increased to 1 MiB; gateway list remains capped at 250 rows | Add pagination before raising the 250-message UI limit |
| Medium | Repeated Send tap or delayed network response | Duplicate carrier SMS charges | Android and desktop suppress identical in-flight commands | Carrier submission has no end-to-end idempotency; confirm ambiguous failures before resending |
| Medium | SMS submission is mistaken for delivery | User believes the recipient received a message | UI records the modem submission result only | Delivery reports are not implemented; future UI should distinguish “sent to modem” from “delivered” |
| Medium | Long-running archives, retry queues, or databases grow forever | Disk or memory exhaustion | Rotating private JSONL archive, 5,000 pending-message cap, 50,000 Telegram fingerprint cap, 5,000-row modem DB cap | Set a retention policy appropriate for personal data and back up before deletion |
| Medium | LAN connection flood or half-open clients | Gateway thread/memory exhaustion and blocked audio | LAN-only acceptance, authentication timeout, global 12-client cap, single-uplink/downlink locks | No per-IP ban exists; isolate the network and reboot the appliance after a sustained attack |
| Medium | Raw PCM audio stalls or has feedback | Broken, one-way, or echoing call audio | Socket setup timeouts, unlimited streaming timeout after authentication, exclusive audio clients, Android echo canceller | No jitter buffer, codec, packet authentication, or desktop echo cancellation; headphones are recommended |
| Medium | Telegram bot path leaks private SMS or fails externally | Cloud exposure, duplicates, or delayed delivery | Disabled by default in service; token files require mode 0600; bounded state; redacted errors; local app path is primary | Revoke any bot token ever pasted publicly; enable Telegram only with informed consent |
| Medium | Local history/config is copied from the computer/tablet | Disclosure of phone numbers, SMS, and pairing token | Private app storage, POSIX 0600 files, 0700 directories, no secrets in binaries/repository | Storage is not application-level encrypted; use full-disk encryption and a locked user account |
| Medium | Desktop package or release is corrupted | Install failure or substituted binary | Package and consolidated release SHA-256 checksums; CI builds all supported targets | Windows/Linux executables are not yet publisher-signed |
| Medium | Background SMS service enters a crash loop | Notification spam, CPU use, log growth | `Restart=on-failure`, ten-second delay, start-rate limit, sandboxing, private umask | The legacy USB/Telegram service should remain disabled when UFI Phone is used |
| Medium | Hidden Android APIs differ across firmware | Guard, answer/hangup, SMS, or audio silently fails | Exact hardware profile; compile checks; explicit errors; refusal on unverified devices | Hardware-in-loop tests are required; source compilation cannot prove runtime compatibility |
| Medium | 2G/3G voice fallback is withdrawn or weak | Incoming calls return busy despite LTE data | Readiness warning and automatic LTE/3G mode recovery | This firmware has no usable IMS/VoLTE stack; carrier network policy can make voice impossible |
| Low | Corrupt local JSON/history cache | UI crash or lost history | Invalid rows are ignored; atomic replacement; bounded histories | No automated cloud backup by design |
| Low | Windows user has no pairing/config onboarding | Desktop app cannot start after a clean install | Strict configuration validation and clear errors | A cross-platform pairing UI remains future work; current setup is performed from the ADB host |
| Low | App-store review rejects dependent functionality | Mobile distribution delay | Ordinary tablet app is separated from privileged modem components and store declarations exist | Reviewers still need hardware-access instructions; iOS/iPadOS/macOS implementations do not yet exist |

## Implemented protection mechanisms

- Protocol v2 HMAC challenge-response with a new nonce per control/audio socket.
- Dial/SMS/control proofs bound to the exact command and gateway port.
- Literal private-LAN host validation; DNS and off-subnet destinations rejected.
- Global connection cap, authentication/read timeouts, and exclusive audio streams.
- Bounded modem SMS database, rotating desktop archive, and bounded retry state.
- Duplicate in-flight SMS suppression in Android and desktop clients.
- Network-mode correction deferred while any call is active.
- Privileged installer gates at both high-level and low-level entry points.
- Local signing-key permission and certificate fingerprint enforcement.
- Atomic private configuration/history writes and strict file modes.
- Hardened optional systemd service with rate limits and Telegram disabled by default.
- Commit-pinned CI actions, pinned Python tooling, legacy Android source compilation,
  repository guards, signed-tag enforcement, and release checksums.

## Required pre-deployment checklist

1. Change the modem Wi-Fi password and router administrator password; disable
   WPS and WAN administration if those controls exist.
2. Run `./ufi_setup.py doctor --json` and confirm the device is an exact tested
   profile. Never override an `unverified` result.
3. Back up the signing directory and confirm the private key is mode `0600`.
4. Build and install all v2 components in one maintenance window.
5. Test incoming/outgoing SMS, Unicode multipart SMS, and ambiguous send failure.
6. Test incoming/outgoing/missed calls, hangup, both audio directions, and a
   call longer than 30 minutes.
7. Perform at least ten cold boots and USB power cycles; verify data, SMS, and
   calls recover without a reconnect storm.
8. Test every intended SIM/operator/tariff. Working data does not prove that
   circuit-switched voice will work.
9. Keep Telegram disabled unless its privacy trade-off is explicitly accepted.
10. Verify release checksums and retain the last known-good APKs/installers.

## Release blockers before broad public deployment

- Hardware-in-loop soak testing on more than one modem unit and each claimed
  Uzbekistan operator.
- Encrypted/authenticated control responses and call media, or an explicitly
  isolated transport such as a private USB/Ethernet link.
- A safe SIM-storage usage and cleanup workflow.
- Publisher signing for Windows and Linux packages plus build provenance.
- Recovery documentation for a failed platform APK update or radio-mode change.
- Independent review of the privileged APKs and platform-key custody process.

The automated checks reduce predictable software failures; they do not turn
unsupported modem firmware into a safety-critical or emergency-call platform.
