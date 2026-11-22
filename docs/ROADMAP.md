# Roadmap

This roadmap is directional. Priorities may change based on source health, platform changes and contributor time.

## Now

- Stabilise the source registry and make health reporting more actionable.
- Finish the animated export path: local H.264/AAC muxing, progress, cancellation.
- Improve panel detection for borderless webtoons and overlapping bubbles.
- Move per-part character mesh deformation from the rig contract into the GL renderer.
- Add more unit tests around the director, panel detector, backup crypto and FTS query builder.
- Baseline profile generation through the `:benchmark` module on a physical device.

## Next

- Per-source proxy/header overrides without weakening the global network policy.
- Richer category rules (auto-add by tag, source or status).
- Tracker two-way sync conflict resolution.
- Reader translation overlay that preserves bubble geometry and font fit.
- Language-learning deck with spaced repetition and offline dictionaries.
- Better TV focus traversal and gamepad remapping.
- Wear OS companion tile and chapter remote.

## Later

- Optional local LLM translation via a user-supplied endpoint.
- Panel-aware OCR that groups dialogue by speaker across panels.
- Community source packs distributed separately from the core app, with licence and safety review.
- Creator tools for original webtoons: panel timeline, camera presets and local export.
- Accessibility audit: TalkBack reading order, larger type, colour-blind safe palettes.

## Not planned

- Hosting or mirroring copyrighted chapters.
- Ads, analytics or paywalled features.
- Bypassing DRM or paid access controls.
- Uploading user libraries, cookies or reading history.
