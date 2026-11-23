# Animated chapters

Animated chapters are a local, transformative presentation layer. They do not generate new story content and they never upload pages.

## Current pipeline

| Stage | Implementation | Status |
| --- | --- | --- |
| Page load | Shared OkHttp client, first page bitmap | Working |
| Panel detection | Downsample → gutter luma/variance runs → column runs → RTL order → min-area filter | Working |
| Bubble detection | ML Kit OCR lines assigned to their containing panel | Working |
| OCR | ML Kit text recognition in the translation feature | Working |
| Character rigs | Lightweight head/torso/arms/hair rectangles generated from panel geometry | Working (heuristic) |
| Director | Push-in, pan, parallax, shake, cut/dissolve/slide transitions | Working |
| Renderer | OpenGL ES 2.0, cached 24×24 mesh, safe vignette, 60 fps target | Working |
| Narration | Real page OCR text, best available voice, Android TTS / offline pack / VoiceStudio bridge | Working |
| Word timing | `UtteranceProgressListener.onRangeStart` where supported | Working |
| Subtitles / karaoke | Compose overlay with active-word highlight | Working |
| Voice casting | Narrator + per-character voice and pitch assignment | Working |
| Manual editor | Panel reorder and speaker assignment | Working |
| Export | UI entry point and local-render messaging | Planned |

## Detection

`PanelDetector` converts the bitmap to a small luma grid, treats low-variance light rows and columns as gutters, and intersects row/column runs into candidate panels. Reading order is left-to-right or right-to-left based on the series content kind. If no reliable gutters are found, the whole page becomes one panel and the UI shows a warning instead of inventing a layout.

## Rigs

`RigBuilder` derives face/body slots from panel proportions. `RigMath` drives head turn, breathing, sway, blink and mouth-open values. The current renderer applies global mesh deformation to the page; per-part mesh deformation is the next step and is isolated behind `CharacterRig` so the renderer can evolve without changing the plan format.

## Director

`Director.compose` uses each panel's narration duration, so camera and voice stay aligned, and chooses transition types from panel aspect and index. `MotionIntensity.STILL` disables all movement. `reducedMotion` and `photosensitivitySafe` are checked before playback and clamp shake/flash.

## Narration and casting

- `SYSTEM`: Android `TextToSpeech`.
- `NEURAL_OFFLINE`: reads installed packs from `filesDir/voice_packs`; falls back to system TTS until a pack is present.
- `VOICE_STUDIO`: adapter for a local engine or the user's own API key; falls back to system TTS.

No TTS provider is bundled with cloud credentials. Nothing is sent to a third party unless the user configures it.

## Performance

- One page is processed at a time; results can be cached per chapter.
- The GL mesh is built once (625 vertices) and reused every frame.
- Processing runs off the main thread; the UI shows progress.
- Decoded pages are capped by the quality setting and the GPU texture limit.

## Limitations

- Panel detection is heuristic and will struggle with borderless webtoons, full-bleed art and overlapping bubbles.
- Character segmentation is rectangle-based, not a full semantic segmentation model.
- Export currently presents the local-render contract but does not yet mux video/audio.
- Lip sync is amplitude/word-timing based, not phoneme-based.
- Right-to-left ordering is inferred from content kind, not from page content.

## Safety and ethics

- Only process media the user has lawful access to.
- Exports are personal-use only and must not be redistributed.
- The feature is enabled in settings and can be disabled entirely.
- Reduced motion and photosensitivity options take precedence over cinematic settings.
