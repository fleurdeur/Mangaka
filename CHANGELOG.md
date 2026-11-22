# Changelog

All notable changes to Mangaka (Kurenai) are recorded here. The project follows semantic versioning for user-facing releases.

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
