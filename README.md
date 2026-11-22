# Mangaka

**Kurenai** is an open-source, native Android reader for manga, manhwa, manhua, webtoons, comics and light novels. It is a spiritual successor to Kotatsu Redo: calm to use, cinematic to look at, and built to stay fast on real devices.

- Package: `app.kurenai`
- Language: Kotlin + Jetpack Compose + Material 3 Expressive
- Minimum Android: 8.0 (API 24)
- License: GNU GPL v3.0
- No ads. No analytics. No accounts required.

> Kurenai does not host or distribute manga. It is a client that talks to sources you choose. Please support the official releases and follow the laws that apply to you.

## Highlights

### Reading
- Horizontal paged and vertical webtoon readers with tap zones, volume-key paging, keyboard/gamepad/D-pad navigation and stylus support.
- Double-page spreads on wide screens, auto-scroll with speed control, e-ink and colour-filter modes, reading light and sleep timer.
- Per-series reader settings, adaptive prefetch, tiled decoding and a disk cache with a configurable budget.
- Continue-reading widget, PiP entry point, Wear OS remote hooks and TV/Leanback launcher support.

### Library & discovery
- Multi-source search, per-source filters, tags, content kinds and ratings.
- Favourites, categories, reading history, updates and yearly stats.
- Local recommendation engine plus cross-source matching by title similarity.
- Optional "Ask AI" librarian that works locally first; cloud completion uses your own OpenAI-compatible endpoint.

### Animated chapters
The headline experiment turns a chapter page into a short cinematic sequence:

1. **Panel detection** finds gutters and reading order, including right-to-left manga.
2. **Bubble/caption regions** are detected; OCR is provided by on-device ML Kit in the translation feature.
3. **Lightweight rigs** are generated for detected characters.
4. A **director** builds push-in, pan, parallax and shake shots that respect reduced-motion and photosensitivity settings.
5. A **real-time OpenGL ES 2.0 renderer** applies mesh deformation and a cinematic vignette at 60 fps.
6. **Narration** uses the system TTS engine, optional offline neural packs, or a VoiceStudio adapter, with word timing for karaoke subtitles.

The animated pipeline is intentionally local and conservative. It never invents dialogue, and exports are personal-use only. See [docs/ANIMATED_CHAPTERS.md](docs/ANIMATED_CHAPTERS.md) for the exact current state and limitations.

### Sources and networking
- Bundled Kotatsu parsers through JitPack (`com.github.KotatsuApp:kotatsu-parsers`), giving access to the large Kotatsu source catalogue. This library is GPL-3.0; see [docs/SOURCES.md](docs/SOURCES.md).
- Cloudflare and generic browser challenges are handled with a hidden WebView, an encrypted cookie jar and a human fallback dialog when a site needs interaction.
- DoH, HTTP/SOCKS proxy, polite per-host rate limiting, circuit breakers and source health tracking with automatic disabling.
- Encrypted local storage for cookies, tracker tokens and API keys.

### Privacy by default
- No advertising, telemetry or analytics SDKs.
- Adult content stays hidden until unlocked, optionally behind a PIN/biometric gate.
- Tracker and cloud keys are opt-in and stored in `EncryptedSharedPreferences`.
- Network diagnostics are local and redact cookies and authorization headers.

## Module map

| Module | Responsibility |
| --- | --- |
| `:app` | Application shell, navigation, onboarding, widgets, playback service |
| `:core:model` | Immutable domain models and settings |
| `:core:common` | Dispatchers, outcomes, event bus |
| `:core:database` | Room entities, DAOs, FTS5 search |
| `:core:datastore` | Preferences DataStore and encrypted secret storage |
| `:core:network` | OkHttp stack, encrypted cookies, challenge solving, DoH, proxy |
| `:core:source` | Source contracts, registry and catalogue |
| `:core:parsers` | Kotatsu parser adapter and bundled source registry |
| `:core:image` | Coil 3 pipeline, memory/disk budgets |
| `:core:data` | Repositories, recommendations, downloads, backup, trackers, AI |
| `:core:designsystem` | Material 3 Expressive theme, colour, type, components |
| `:feature:library` | Library, history, categories, search |
| `:feature:browse` | Source browsing and multi-source search |
| `:feature:details` | Series details, chapters, trackers, related titles |
| `:feature:reader` | Paged/webtoon reader |
| `:feature:updates` | Update feed |
| `:feature:animator` | Panel detection, rigs, director, GL renderer, TTS |
| `:feature:translation` | OCR, on-device translation, cloud/LLM adapter |
| `:feature:settings` / `:feature:more` | Settings, sources, diagnostics, downloads, stats |
| `:benchmark` | Macrobenchmark startup and scroll journeys |

Build logic lives in `build-logic/` as convention plugins.

## Build

Requirements: JDK 17+, Android SDK with platform 37, and an internet connection for the first dependency resolution.

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Create `local.properties` with your SDK path (never commit it):

```properties
sdk.dir=/path/to/Android/Sdk
```

Release builds read `signing.properties` from the repository root:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Neither `local.properties` nor `signing.properties` is tracked. The release build enables R8 full mode, resource shrinking and ABI/resource packaging rules.

Useful checks:

```bash
./gradlew :app:lintDebug
./gradlew test
./gradlew :benchmark:assembleRelease
```

## Source catalogue

The Kotatsu parser library is pulled from JitPack at the commit pinned in `gradle/libs.versions.toml`. Source metadata is translated into Kurenai's `MangaSource` contract at runtime. Adding a parser means adding it upstream or implementing `MangaSource` in `:core:source`; see [docs/SOURCES.md](docs/SOURCES.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Security issues should follow [SECURITY.md](SECURITY.md), not the public issue tracker.

## Legal

Kurenai is licensed under GPL-3.0. The bundled parser library retains its own GPL-3.0 license and copyright. Kurenai is not affiliated with Kotatsu, Kotatsu Redo, any scanlation group or any publisher. See [docs/DMCA.md](docs/DMCA.md) and [docs/PRIVACY.md](docs/PRIVACY.md).
