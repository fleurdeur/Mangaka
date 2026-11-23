# Changelog

All notable changes to Mangaka (Kurenai) are recorded here. The project follows semantic versioning for user-facing releases.

## 1.0.1 — Stability, ABI splits and startup fixes

### Fixed

- Replaced the FTS5 search index with FTS4 and added a Room 1 → 2 migration. Some Android SQLite builds do not include FTS5, which could crash the app while opening the library database.
- The source catalogue no longer constructs every Kotatsu parser while building the onboarding source list. Parsers are created lazily on first use, reducing startup time and memory.
- Removed a duplicate Coil `SingletonImageLoader.setSafe` call that built and discarded a second image loader at startup.
- DataStore corruption now falls back to defaults instead of crashing the app.
- Hardened the release R8 rules for the Kotatsu parser package and source adapters.

### Performance

- Source list construction and parsing are off the main thread where they can be.
- Startup now avoids network, parser and database work on the UI thread.
- Added separate ABI APKs for `arm64-v8a` and `armeabi-v7a`, plus a universal APK. All release artifacts are signed with the main release keystore.

## 1.0.0 — First public release

The first open-source build of Kurenai, a native Android reader for manga, manhwa, manhua, webtoons, comics and light novels.

### Reading

- Paged left-to-right and right-to-left readers, vertical reader and continuous Webtoon mode.
- Double-page spreads on wide screens, tap zones, volume-key paging, keyboard, gamepad, D-pad and stylus navigation.
- Auto-scroll, reading light, sleep timer, e-ink and colour-filter modes.
- Per-series settings, adaptive prefetch, tiled decoding and a configurable disk cache.
- Continue-reading home-screen widget and narration playback service.

### Library and discovery

- Multi-source search with filters and streamed results.
- Favourites, categories, history, updates, reading stats and a yearly review.
- Local recommendations, cross-source matching and source alternatives.
- AniList, MyAnimeList, Kitsu and Shikimori tracking.
- Encrypted backup and restore.

### Sources and networking

- Large Kotatsu parser catalogue through the pinned parser library.
- Per-source health tracking, automatic disabling, language and content-rating filters.
- Encrypted cookie jar, DoH, HTTP/SOCKS proxy, polite rate limiting and circuit breakers.
- Cloudflare/WebView challenge handling with an interactive fallback.

### Smart features

- On-device OCR and ML Kit translation.
- Optional OpenAI-compatible cloud or local LLM translation and librarian.
- **Animated chapters (experimental):** panel detection, character rigs, director camera plan, OpenGL ES renderer, voice casting, TTS providers, karaoke subtitles and a manual editor.

### Design and privacy

- Material 3 Expressive interface with Inter typography, coffee/crimson palette, Monet dynamic colour and AMOLED black.
- Adult-content gating, PIN/biometric app lock and no ads or analytics.
- Clean first-run onboarding with per-language source selection.

### Known limitations

- Animated chapter video export is scaffolded; MP4 muxing is planned.
- Panel detection is heuristic and may need manual correction for borderless webtoons.
- Character rigs are lightweight rectangles; per-part mesh deformation is planned.
