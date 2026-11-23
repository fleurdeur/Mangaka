# Changelog

All notable changes to Mangaka (Kurenai) are recorded here. The project follows semantic versioning for user-facing releases.

## 1.0.2 — Reader, animated chapters and source reliability

### Fixed

- First-run onboarding, the app lock, the challenge browser and every normal screen now respect the system status and navigation bars. Only the reader and animated player draw edge-to-edge on purpose.
- Animated chapters no longer open to a blank screen. The GL renderer reloads its texture after the page is processed, survives GPU texture limits on tall webtoon pages, uses a valid vignette shader and keeps rendering when playback is paused.
- Panel camera timing now follows the per-panel narration length instead of a single average, so voice and camera stay in sync.
- Narration reads real OCR text from the page (with bubble-to-panel assignment) instead of generic chapter titles, prefers high-quality offline/neural voices and respects casting speed/pitch.
- Protocol-relative, root-relative, HTML-escaped and `data:` chapter image URLs are resolved before they reach Coil or the renderer.
- Added a Referer and image Accept header to chapter images for sources that reject hotlinks.
- Cloudflare detection no longer mistakes ordinary Cloudflare pages for challenges, while still catching header-stripped 200 interstitials.
- Search no longer treats a normal Cloudflare-protected page as a challenge, and DNS failures rotate to another parser domain and retry once.
- Auto-crop now removes only uniform paper borders, losslessly, and never crops dark full-bleed art.

### Reader

- Added right-to-left paged reading, true vertical paging and correct two-page spread ordering for manga.
- Chapter images load at original resolution with ARGB_8888 and no RGB565 subsampling.
- Added SVG and GIF decoding alongside the platform AVIF/HEIF/WebP/PNG/JPEG decoders.
- Adaptive prefetch, volume-key paging, keyboard navigation and per-series settings remain intact.

### Animated chapters

- Page decoding honours the Low/Balanced/High/Ultra quality setting, up to the GPU-safe 4096 px texture limit.
- The mesh is built once instead of allocating vertex/index buffers every frame.
- Reduced-motion forces still cameras, photosensitivity-safe limits full-intensity shake, and narration-off plays a clean timed slideshow.
- Removed settings that had no implementation so every visible control changes real behaviour.

### Speed and sources

- Plain source searches skip the remote filter/tag catalogue, removing a second request from most searches.
- Added a 96 MB HTTP cache, a 5-minute DNS answer cache, a two-provider DoH fallback and per-host politeness.
- "Search all sources" runs ten sources at a time with per-source timeouts and streams results as each source answers.
- Series details are cached for 45 seconds and reused when opening the chapter list.
- Added a repeatable source repair action and replay-on-domain-rotation for "unable to resolve host" failures.
- Search results are cached per source/filter/page for five minutes.

### Design

- Rebuilt the onboarding and app-lock screens with inset-safe gradients, a clear step header, a thicker progress bar and scrollable content on small phones.
- Expanded Material 3 colour roles, surface containers, outlined cards and section accents; dynamic colour now follows the selected palette consistently.
- Added a continue-reading hero card and cleaner progressive search feedback.

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
