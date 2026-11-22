# Security policy

## Supported versions

Security fixes are applied to the latest release and the `main` branch.

## Reporting a vulnerability

Please do not open a public issue for security vulnerabilities.

Use GitHub's private vulnerability reporting for this repository, or contact the maintainer listed on the repository profile. Include:

- Affected version/commit.
- Reproduction steps or a proof of concept.
- Impact assessment (data disclosure, code execution, denial of service, etc.).
- Any suggested fix.

You can expect an acknowledgement within a few days. Please allow time for a fix and coordinated disclosure before publishing details.

## Scope

In scope:

- Cookie/token disclosure or bypass of encrypted storage.
- WebView challenge handling that exposes arbitrary local files or JavaScript bridges.
- Backup encryption flaws.
- Path traversal in downloads or imports.
- Intent redirection, exported components without permission checks.
- Source parser code that executes untrusted input unsafely.

Out of scope:

- Content hosted by third-party sources.
- Rate limits or blocks imposed by third-party sites.
- Issues requiring a rooted device or physical access without a lock.
- Social engineering.
- Reports generated solely by automated scanners without a demonstrated impact.

## Security design notes

- Cookies and secrets use `EncryptedSharedPreferences` with AES-256-SIV/GCM keys from the Android Keystore.
- `allowBackup` is false; extraction rules exclude cookie and secret stores.
- WebView challenge solving is isolated to a hidden view, does not enable file access, and returns only cookies/clearance for the requested host.
- Release builds enable R8 full mode and do not include dependency metadata.
- Network diagnostics redact sensitive headers.

## No bounty

The project does not currently offer a paid bug bounty. Credit is given in release notes when requested.
