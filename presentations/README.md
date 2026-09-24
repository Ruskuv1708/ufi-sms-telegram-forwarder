# UFI Phone presentations

This directory contains the presentation side of the same UFI Phone project.

```text
assets/       Original visual assets used by the deck generator
final/        Current English and Russian PowerPoint deliverables
reference/    Prior deck used as the visual-language reference
source/       Reproducible presentation generator
validation/   Validation receipts and final rendered slide reviews
archive/      Superseded drafts and earlier workbench files (local only)
workbench/    Regenerated intermediate files (local only)
```

The final decks are:

- `final/UFI_Phone_v0.5.0_TUIT_Startup_Pitch_EN_FINAL.pptx`
- `final/UFI_Phone_v0.5.0_TUIT_Startup_Pitch_RU_FINAL.pptx`

## Rebuild

The generator uses the presentation runtime bundled with Codex. It resolves
the project root relative to its own location, so moving the complete project
folder does not require editing source paths.

```bash
RUNTIME_NODE_MODULES=/path/to/codex-runtime/node_modules \
PRESENTATIONS_SKILL_DIR=/path/to/presentations/skill \
RUNTIME_PYTHON=/path/to/runtime/python3 \
node presentations/source/build_ufi_phone_pitch.mjs
```

The generator writes final `.pptx` files to `final/` and temporary validation
material to `workbench/`. The public decks contain no phone numbers, private
SMS content, bot tokens, or pairing credentials.
