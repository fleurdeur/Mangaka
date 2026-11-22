# Privacy

Kurenai is designed to work without an account and without profiling.

## What stays on the device

- Library, history, categories, reading progress, downloads and stats.
- Source preferences, reader settings and theme.
- Cookies and challenge clearance, encrypted with `EncryptedSharedPreferences`.
- Tracker tokens, AI/translation keys and any adult-content PIN.
- Diagnostics and request metadata, kept locally and never uploaded.

## What leaves the device

Only requests you initiate:

- Pages and metadata from enabled sources.
- Optional tracker sync if you connect a tracker.
- Optional cloud AI or translation calls if you configure an endpoint and key.
- Crash reports only if you explicitly enable them in a build that includes a reporting backend. The default build contains no analytics or crash SDK.

No advertising ID, install ID or behavioural profile is collected.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Fetch sources and obey network constraints |
| `POST_NOTIFICATIONS` | Download and update notifications |
| `FOREGROUND_SERVICE*` | Downloads and narration playback |
| `WAKE_LOCK` | Finish downloads with the screen off |
| `USE_BIOMETRIC`, `VIBRATE` | App lock and haptics |

Kurenai does not request location, contacts, camera, microphone or broad storage access. Image scanning uses the system photo picker, which grants access only to the selected image.

## Data controls

- **Settings → Diagnostics → Clear cookies** wipes the encrypted cookie store.
- **Settings → Backup** exports an encrypted archive; the passphrase is never stored.
- **Settings → Privacy** disables recommendations, incognito mode and challenge solving.
- Uninstalling the app removes the Room database, DataStore, encrypted preferences and downloaded files.

## Children

Kurenai is not directed at children. Adult sources are hidden by default and can be locked behind a PIN/biometric gate.

## Contact

Privacy questions: open a discussion in the repository or use the security contact in [SECURITY.md](../SECURITY.md). Do not include personal data in public issues.
