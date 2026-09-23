# Design QA — UFI Phone

## Evidence

- Source visual truth: `/home/Hollow/.codex/generated_images/01a0c300-a63b-7f72-8eed-fbed6a2d31a4/exec-efc18da4-0cbd-409d-937c-0073ad560417.png`
- Rendered implementation: `/tmp/ufi-phone-final.png`
- Combined comparison: `/tmp/ufi-phone-design-qa.png`
- Additional implemented states: `/tmp/ufi-phone-keypad-final.png` and `/tmp/ufi-phone-messages-final.png`
- State: light theme, idle LTE connection, Calls tab selected, actual on-device call history
- Device viewport: 800 × 1340 physical pixels, Android density override 218 dpi, user font scale 0.85
- Source pixels: 1048 × 1501
- Implementation pixels: 800 × 1340
- Native Android layout size: approximately 587 × 984 dp; CSS size and device scale factor are not applicable
- Density normalization: the implementation was kept at its native 800 × 1340 capture. The source was proportionally scaled to 936 × 1340 and placed beside it in the combined comparison. The source is a conceptual mock with a slightly wider aspect ratio than the physical tablet.

## Findings

- [P3] Informational labels differ intentionally.
  Location: connection status and call-list header.
  Evidence: the mock says `Ucell · LTE · Ready` and `See all`; the implementation adds the network-mode recovery count and says `Last 100`.
  Impact: the implementation is slightly denser, but the extra text communicates real modem health and the actual history limit.
  Fix: none required; these are deliberate functional additions.

- [P3] Actual content produces more empty space than the populated mock.
  Location: recent-calls list.
  Evidence: the mock contains eight illustrative records while the device currently contains one real record.
  Impact: none; this is data-dependent rather than layout drift. Row geometry, alignment, and the floating keypad action remain consistent with the mock.
  Fix: none required.

## Required Fidelity Surfaces

- Fonts and typography: the native Android sans-serif family, weights, hierarchy, line height, and truncation remain close to the Google Phone/Material reference. The physical device's 0.85 font-scale preference is respected rather than overridden.
- Spacing and layout rhythm: header, content margins, list row alignment, avatar sizing, floating action button, and bottom navigation follow the source hierarchy. The implementation also reserves sufficient space above Android's system navigation.
- Colors and visual tokens: pale blue-gray background, dark navy text, Google blue primary actions, mint readiness state, and muted secondary text map closely to the source palette with adequate contrast.
- Image quality and asset fidelity: the interface contains no photographic imagery or custom brand artwork. Standard controls use crisp Android vector drawables appropriate for Material phone and message actions.
- Copy and content: labels are concise and in English. Calls, Keypad, and Messages are explicit; modem sync and safety restrictions are explained where relevant.

## Full-view Comparison Evidence

The combined source/implementation image shows the same primary composition: large Phone heading, upper-right readiness/settings area, Recent calls section, aligned avatar/detail/date/call columns, lower-right dialpad action, and three-item bottom navigation. The actual tablet aspect is narrower and includes Android system bars, but no persistent app control is clipped or obscured.

## Focused Region Comparison Evidence

A separate crop was not needed because the combined 1736 × 1340 image preserves readable typography, icon edges, navigation labels, status treatment, list spacing, and the floating action at full-view scale. Keypad and Messages captures were also inspected independently to confirm consistent type, color, spacing, and bottom-navigation treatment.

## Comparison History

1. Earlier evidence: `/tmp/ufi-phone-calls.png` showed the app's bottom navigation labels too close to Android's system navigation area, a P2 persistent-control spacing issue.
2. Fix applied: bottom navigation height was increased from 90 dp to 112 dp and bottom padding from 8 dp to 26 dp. Keypad focus was also prevented from opening an unnecessary software keyboard, and call timestamps were made more compact.
3. Post-fix evidence: `/tmp/ufi-phone-final.png`, `/tmp/ufi-phone-keypad-final.png`, and `/tmp/ufi-phone-messages-final.png` show fully visible navigation icons and labels with clear separation from the system bar. No actionable P0, P1, or P2 issue remains.

## Open Questions

- None blocking. Message sending and carrier delivery depend on the active SIM/operator; QA used a non-routable invalid-recipient request so no chargeable SMS was transmitted.

## Implementation Checklist

- [x] Preserve the familiar Google Phone information hierarchy.
- [x] Keep Calls, Keypad, and Messages reachable from persistent bottom navigation.
- [x] Keep modem readiness visible without interrupting the primary task.
- [x] Prevent app navigation from colliding with Android system controls.
- [x] Verify native vector icons and readable English copy on the physical tablet.

## Follow-up Polish

- Contact-name resolution could replace raw numbers when a future contacts source is added; this is outside the current modem-only scope.

final result: passed
