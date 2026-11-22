# Contributing to Kurenai

Thanks for helping. Kurenai is GPL-3.0 and welcomes code, source fixes, translations, testing and documentation.

## Before you start

- Check open issues and discussions to avoid duplicate work.
- For a large feature, open an issue describing the user problem first.
- Do not include copyrighted pages, scraped content, cookies, tokens or personal data in issues, tests or fixtures.

## Development

1. Install JDK 17+ and Android SDK platform 37.
2. Create `local.properties` with your SDK path.
3. Build: `./gradlew :app:assembleDebug`.
4. Run unit tests: `./gradlew test`.
5. Optional device benchmark: `./gradlew :benchmark:assembleRelease` then install both APKs.

The project uses convention plugins in `build-logic/`. Add dependencies through `gradle/libs.versions.toml`.

## Code style

- Kotlin official style, 4-space indentation, trailing commas.
- Compose: stateless where practical, state hoisted into ViewModels, no business logic in composables.
- Repositories expose `Flow` for observable data and `suspend` functions for one-shot work.
- Use the shared `KurenaiHttp` client for every network call.
- Never log cookies, authorization headers, tokens, full URLs with secrets, or file paths outside the app sandbox.
- Prefer `Outcome`/sealed results over thrown exceptions at UI boundaries.

## Source changes

Follow [docs/SOURCES.md](docs/SOURCES.md). Fix parsers upstream when possible. Never add a source that bypasses DRM or paid access.

## Tests

- Add a unit test for pure logic (director, query builder, backup crypto, matching).
- Add a Compose test for a new screen state if it is interactive.
- Network tests must use local fakes, not live sites.

## Commits

- Keep commits focused and buildable.
- Write imperative messages: `Fix FTS query escaping`, not `fixed stuff`.
- Reference issues with `Fixes #123` when applicable.
- Do not commit `local.properties`, `signing.properties`, keystores, `.gradle`, `build`, IDE files or generated schema dumps.

## Pull requests

Include:

- What changed and why.
- Screenshots or a short recording for UI changes.
- Test commands you ran.
- Any source/legal implications.

Maintainers may ask for a smaller scope. By contributing you agree your work is licensed under GPL-3.0.

## Translations

Strings live in `app/src/main/res/values/strings.xml` and feature modules. Keep placeholders intact and avoid hard-coded user-facing text in composables. Machine translation is acceptable as a starting point but must be reviewed.

## Reporting bugs

Use the issue template. Include app version, Android version, device, steps, expected/actual behaviour and a redacted log. Never attach cookies or account tokens.
