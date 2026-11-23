# Architecture

Kurenai follows a multi-module MVVM design. Modules depend inward on stable contracts, never on feature implementations.

```
:app
 ├── :feature:*        Compose UI + ViewModel
 ├── :core:data        repositories, workers, engines
 ├── :core:parsers     Kotatsu adapter
 ├── :core:network     HTTP, cookies, challenge solving
 ├── :core:database    Room + FTS4
 ├── :core:datastore   DataStore + encrypted secrets
 ├── :core:image       Coil pipeline
 ├── :core:source      source contracts
 ├── :core:model       domain models
 ├── :core:common      dispatchers, events, outcomes
 └── :core:designsystem theme + components
```

## Conventions

`build-logic/` is an included build with `kurenai.android.{application,library,compose,hilt,room,feature}` plugins. They centralise SDK levels, Java/Kotlin toolchains, Compose, Hilt, Room schema handling and feature wiring so module build files stay small.

- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 24`.
- Java 17 bytecode, Kotlin 2.4, KSP for Room and Hilt.
- Version catalog in `gradle/libs.versions.toml`; no hard-coded versions in modules.

## Data flow

UI emits events to a `ViewModel`; the ViewModel calls a repository; repositories combine Room flows, DataStore settings and source calls. Source calls are suspend functions with `Outcome`-style handling so failures become source-health events rather than crashes.

Reader flow:

1. `ReaderViewModel` resolves a chapter and asks `ReaderRepository` for pages.
2. Downloaded files take precedence over remote URLs.
3. Coil fetches and decodes via the shared `ImageLoader`; prefetch is driven by measured page timings.
4. Progress is debounced and stored through `MangaRepository`, which also feeds history, stats and the continue-reading widget.

Animated flow:

1. `AnimatedChapterViewModel` loads the first page bitmap through the shared HTTP client.
2. `PanelDetector` segments gutters; `BubbleDetector` proposes caption boxes.
3. `RigBuilder` creates lightweight part rectangles; `Director` builds `CameraShot`s.
4. `AnimRenderer` uploads the page as a texture and displaces a 24×24 grid on the GPU.
5. `NarrationEngine` selects a TTS provider and streams word timings back to the subtitle overlay.

## Persistence

- Room database `kurenai.db` with WAL. FTS4 virtual table `manga_fts` is created in a callback and kept in sync with triggers.
- Preferences DataStore stores JSON-serialised `AppSettings` and `SourcePreferences`; unknown keys are tolerated for forward compatibility.
- `EncryptedSharedPreferences` stores cookies, tracker tokens, AI/translation keys and the adult-content PIN hash.
- Backups are AES-256-GCM encrypted with PBKDF2-derived keys and are opt-in.

## Threading

- `KurenaiDispatchers` supplies IO/default/main qualifiers.
- Network and disk work never runs on the main thread.
- Multi-source search uses `channelFlow` with a semaphore and per-source timeout; results stream to the UI in chunks.
- Download workers cap concurrency per host and honour battery/network constraints.

## Design system

`core:designsystem` defines the coffee/crimson palette, Inter typography, expressive shapes and components. `KurenaiTheme` supports light, dark, AMOLED, dynamic Monet colour and an optional cover-adaptive palette. All feature screens consume the same tokens and `LocalKurenaiColors`.

## Testing

- Unit tests live beside modules (`src/test`).
- Compose UI tests use the Material 3 test harness.
- `:benchmark` contains a Macrobenchmark startup journey; it requires a connected device or emulator.
