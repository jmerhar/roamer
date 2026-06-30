# Roamer — Development Guide

## Build & Test

```sh
make build    # debug APK
make debug    # install to connected device
make test     # unit tests
make clean    # clean build outputs
make release VERSION=x.y NOTES=file  # bump, build, tag, push, publish GitHub Release
```

## Releasing

`make release VERSION=x.y NOTES=file` runs `bin/release.sh`, which: checks the
tree is clean and on `main`, bumps `versionCode`/`versionName` in
`app/build.gradle.kts`, builds a signed `assembleRelease` APK, commits, tags
`vX.Y`, pushes, and creates a GitHub Release (via `gh`) with the APK attached.
A notes file is **required** — the script aborts without `NOTES=file`; `DRAFT=1`
creates a draft. The release APK is named `roamer-release.apk` via
`base.archivesName` in `app/build.gradle.kts`.

## Architecture

The app has one job: rewrite local phone numbers with international prefixes when roaming.

- **`NumberRewriter`** — pure function, all rewrite logic lives here. Stateless, easily testable. Also exposes `isDestinedForCountry()` for local SIM routing decisions.
- **`RoamerCallRedirectionService`** — thin Android service wrapper. Reads prefs synchronously (`runBlocking`), delegates to `NumberRewriter`, resolves local SIM if enabled, always calls `redirectCall()` on the calling thread.
- **`CountryDialCodes`** — static mapping, no logic beyond lookup.
- **`PreferencesRepository`** — DataStore wrapper for settings persistence.
- **`MainActivity`** — settings UI, observe-only (no business logic).

## Key Constraints

- `onPlaceCall()` must respond within ~5 seconds. Never do network I/O or heavy work there.
- `redirectCall()` must be called on the binder thread (not from a background coroutine).
- **Never use `placeCallUnmodified()`** — the Telecom framework normalizes numbers (adds international prefix) before invoking `onPlaceCall()`, but `placeCallUnmodified()` reverts to the original pre-normalization number. Always use `redirectCall(handle, ...)` to ensure the normalized number is what actually gets dialed.
- Italy does NOT use a trunk prefix — the leading `0` is part of the subscriber number. The `noTrunkPrefixCountries` set handles this.
- USSD/MMI codes (`*`, `#` prefixed) must never be rewritten.
- **Local SIM routing is independent of number rewriting** — it runs after the rewrite decision. The number always stays in international format; only the `PhoneAccountHandle` changes.
- `findLocalSimAccount()` catches `SecurityException` so it silently falls back if `READ_PHONE_STATE` is not granted.
- **Dial code prefix ambiguity** — `NumberRewriter.isDestinedForCountry()` guards against false prefix matches (e.g. `+351...` must not match US dial code `+1`) by requiring at least 6 subscriber digits after the dial code.

## Testing

All rewrite logic is in `NumberRewriter` (pure object, no Android dependencies). Tests live in `app/src/test/` and run on the JVM — no emulator needed.

`isDestinedForCountry()` is also tested here — it validates that a number in international format targets a specific country (used by local SIM routing).

## Adding a Country

Add to `CountryDialCodes.kt`:
```kotlin
"xx" to "123",  // Country Name
```

If the country doesn't use a trunk prefix (like Italy), also add to `NumberRewriter.noTrunkPrefixCountries`.
