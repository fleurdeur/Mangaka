# Sources and parsers

Kurenai is a client. It ships a parser library and no content. You are responsible for the sources you enable and for complying with local law and each site's terms.

## Bundled catalogue

`core:parsers` depends on:

```kotlin
implementation("com.github.KotatsuApp:kotatsu-parsers:<pinned-commit>")
```

The commit is pinned in `gradle/libs.versions.toml`. The library is GPL-3.0. Its parsers implement `MangaParser` and are constructed through `KotatsuLoaderContext`, which provides:

- the shared OkHttp client and encrypted cookie jar,
- a WebView JavaScript evaluator for challenge pages,
- bitmap redraw hooks,
- source configuration storage.

`KotatsuMangaSource` adapts the parser model into Kurenai's `Manga`, `MangaChapter`, `MangaPage` and `Tag` types. IDs use the form `kotatsu:<SOURCE>:<remote-id>` so mixed catalogues remain unambiguous.

`KotatsuSourceRegistry` exposes every `MangaParserSource` plus the offline `SampleSource`. Sources can be filtered by language, content kind, content rating and health.

## Content safety

- `ContentRating.ADULT` and `ContentType.HENTAI` are hidden until `adultContentUnlocked` is true.
- Adult unlock is a local setting; the UI also supports a PIN and biometric app lock.
- Source health is recorded per request. Repeated failures auto-disable a source and surface in **More → Sources**.

## Adding a source

Preferred path: add or fix the parser upstream in Kotatsu, then bump the pinned commit. This keeps one implementation for everyone and avoids licence duplication.

Alternative: implement `MangaSource` directly:

```kotlin
interface MangaSource {
    val info: SourceInfo
    suspend fun popular(page: Int): List<Manga>
    suspend fun search(query: String, page: Int, filters: SearchFilters): List<Manga>
    suspend fun details(manga: Manga): Manga
    suspend fun chapters(manga: Manga): List<MangaChapter>
    suspend fun pages(chapter: MangaChapter): List<MangaPage>
    fun filters(): FilterOptions
}
```

Register it in `SourceRegistry`. Use `KurenaiHttp.client()` for all requests so rate limiting, challenge handling, cookies and proxy settings apply. Never bypass these interceptors.

Guidelines:

- Respect robots.txt, rate limits and terms of service.
- Do not add sources that require DRM circumvention or paywall bypass.
- Do not hard-code credentials, cookies or private API tokens.
- Provide a meaningful `SourceLicense` and attribution in `SourceInfo`.

## Removing a source

Source removal requests are handled under [DMCA.md](DMCA.md). A source can be disabled remotely by removing it from the bundled registry in a patch release; local user data remains untouched.

## Network etiquette

- Per-host token bucket with jitter.
- Circuit breaker after repeated 5xx/403 responses.
- Exponential backoff with a minimum delay.
- Clear user-agent and no browser fingerprint spoofing beyond what a challenge requires.
- Challenge cookies are stored encrypted and cleared from **Settings → Diagnostics**.
