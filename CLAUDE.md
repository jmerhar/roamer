# Roamer — Development Guide

## Build & Test

```sh
make build    # debug APK
make debug    # install to connected device
make test     # unit tests
make clean    # clean build outputs
```

## Architecture

The app has one job: rewrite local phone numbers with international prefixes when roaming.

- **`NumberRewriter`** — pure function, all rewrite logic lives here. Stateless, easily testable.
- **`RoamerCallRedirectionService`** — thin Android service wrapper. Reads prefs synchronously (`runBlocking`), delegates to `NumberRewriter`, calls `redirectCall()` or `placeCallUnmodified()` on the calling thread.
- **`CountryDialCodes`** — static mapping, no logic beyond lookup.
- **`PreferencesRepository`** — DataStore wrapper for settings persistence.
- **`MainActivity`** — settings UI, observe-only (no business logic).

## Key Constraints

- `onPlaceCall()` must respond within ~5 seconds. Never do network I/O or heavy work there.
- `redirectCall()` / `placeCallUnmodified()` must be called on the binder thread (not from a background coroutine).
- Italy does NOT use a trunk prefix — the leading `0` is part of the subscriber number. The `noTrunkPrefixCountries` set handles this.
- USSD/MMI codes (`*`, `#` prefixed) must never be rewritten.

## Testing

All rewrite logic is in `NumberRewriter` (pure object, no Android dependencies). Tests live in `app/src/test/` and run on the JVM — no emulator needed.

## Adding a Country

Add to `CountryDialCodes.kt`:
```kotlin
"xx" to "123",  // Country Name
```

If the country doesn't use a trunk prefix (like Italy), also add to `NumberRewriter.noTrunkPrefixCountries`.
