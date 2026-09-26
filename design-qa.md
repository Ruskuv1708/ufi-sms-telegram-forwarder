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
4. A later landscape audit found the portrait keypad geometry did not fit the
   shorter 1340 × 800 viewport: its final row and call action were obscured by
   bottom navigation. Version 0.5.0 replaces that layout with a two-column
   landscape treatment. `/tmp/ufi-phone-v050-audit/keypad-landscape-final.png` shows
   all twelve keys, the call action, safety copy, and navigation at once.
5. Version 0.5.0 also constrains Calls content on wide screens, replaces
   number-derived avatar text with phone/message icons, shortens the compose
   action, hides the routine healthy-sync caption, and uses carrier-neutral
   connection wording. The new landscape result has no actionable P0, P1, or
   P2 issue.

## Open Questions

- None blocking. Message sending and carrier delivery depend on the active SIM/operator; QA used a non-routable invalid-recipient request so no chargeable SMS was transmitted.

## Implementation Checklist

- [x] Preserve the familiar Google Phone information hierarchy.
- [x] Keep Calls, Keypad, and Messages reachable from persistent bottom navigation.
- [x] Keep modem readiness visible without interrupting the primary task.
- [x] Prevent app navigation from colliding with Android system controls.
- [x] Verify native vector icons and readable English copy on the physical tablet.
- [x] Keep every keypad action visible in portrait and landscape.
- [x] Use carrier-neutral UI copy for deployment beyond Ucell.

## Follow-up Polish

- Contact-name resolution could replace raw numbers when a future contacts source is added; this is outside the current modem-only scope.

## Desktop simplification pass — 2026-09-26

### Evidence

- Source visual truth: the previous shipped desktop captures at Git commit
  `44b5e46`, paths `presentations/assets/ufi-app-calls.png` and
  `presentations/assets/ufi-app-messages.png`.
- Rendered implementation: `presentations/assets/ufi-app-calls.png`,
  `presentations/assets/ufi-app-messages.png`, and the new first-run state at
  `presentations/assets/ufi-app-setup.png`.
- Same-state combined comparisons:
  `/tmp/ufi-phone-desktop-calls-comparison-final.png` and
  `/tmp/ufi-phone-desktop-messages-comparison-final.png`.
- Responsive evidence: `/tmp/ufi-phone-setup-minimum-final.png`,
  `/tmp/ufi-phone-setup-paired-minimum-final.png`, and
  `/tmp/ufi-phone-messages-minimum.png`.
- Viewports: 900 × 600 for the primary comparison and 820 × 580 for the
  minimum-window check. Source and implementation captures are both 1× native
  pixels with no density normalization or browser/CSS scaling.
- State: light theme, paired/idle modem for Calls and Messages; unpaired clean
  install for Connection.

### Findings

- No actionable P0, P1, or P2 difference remains. The redesign intentionally
  replaces platform-default gray controls with the existing Google-inspired
  blue, mint, navy, and pale-surface design language while preserving every
  primary action from the source.
- [P3] Native Tk widgets remain more rectangular than the Android Material
  client. This is an accepted platform constraint; the hierarchy, color,
  padding, selected state, and control weight now carry the shared language
  without imitating rounded mobile controls poorly.

### Required fidelity surfaces

- Fonts and typography: Segoe UI with the operating-system sans-serif fallback
  preserves a clear display/section/body hierarchy. Both comparison images
  show stronger title weight, quieter helper copy, and no truncated primary
  labels.
- Spacing and layout rhythm: the 184-pixel navigation rail, 28-pixel content
  inset, status card, and section gaps create consistent grouping. Calls,
  Messages, and Connection remain usable at 820 × 580.
- Colors and visual tokens: the implementation consistently uses navy text,
  Google-style blue selection/actions, mint readiness, amber repair, red
  offline/setup, and pale blue-gray surfaces with readable contrast.
- Image quality and asset fidelity: the only branded image is the existing
  512-pixel UFI Phone icon, downsampled cleanly for the header. No placeholder
  image, emoji icon, CSS art, or generated decoration was introduced.
- Copy and content: setup now uses short task language—Connect, Run one command,
  Use anywhere—and tells users when a pairing file is private. Healthy states
  are concise; detailed recovery information remains available in Connection.
- States and accessibility: selected, disabled, ready, repair, offline, and
  unpaired states are visually distinct. Controls remain native keyboard-
  reachable widgets with focus behavior and text labels.

### Full-view and focused comparison

The side-by-side Calls comparison shows the same navigation, readiness,
call-control, and history structure at the same size, with clearer grouping
and less visual noise. The Messages comparison confirms a wider conversation
rail and distinct incoming/outgoing message surfaces. Text and control details
remain readable in the full 1800 × 600 comparisons, so no additional crop was
needed.

### Comparison history

1. First minimum-window capture showed setup instructions truncating at
   820 × 580, a P2 responsive issue.
2. The copy was shortened, wrap widths were constrained to the available
   content column, and vertical gaps were reduced.
3. `/tmp/ufi-phone-setup-minimum-final.png` shows all three setup steps and
   their explanations without clipping; the P2 is closed.
4. The denser paired Connection state was also checked at 820 × 580 after the
   final spacing pass. `/tmp/ufi-phone-setup-paired-minimum-final.png` keeps
   all import, export, open-phone, and setup guidance visible.

### Implementation checklist

- [x] Preserve Calls, Keypad, and Messages as the stable navigation model.
- [x] Add a complete unpaired first-run state instead of an error dialog.
- [x] Keep modem readiness visible but quiet when healthy.
- [x] Make incoming and outgoing messages scannable as separate surfaces.
- [x] Verify primary and minimum desktop window sizes.
- [x] Keep the Android design language and desktop platform conventions aligned.

final result: passed
