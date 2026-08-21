# Roamer — Development Guide

## Build & Test

```sh
make build          # debug APK
make debug          # install debug to connected device
make test           # unit tests
make lint           # Android Lint + ShellCheck on bin/
make check          # lint + tests — run before pushing
make build-release  # release APK
make install        # install release to connected device
make clean          # clean build outputs
make release VERSION=x.y NOTES=file  # bump, build, tag, push, publish GitHub Release
```

## What This App Actually Does

Read this before changing `NumberRewriter` — the naive mental model is wrong.

Telecom normalizes the dialled number to E.164 **before** invoking
`onPlaceCall()`, using the *network* (visited) country rather than the SIM's
(`CallRedirectionProcessorHelper.formatNumberToE164()` →
`PhoneNumberUtils.formatNumberToE164(number, tm.getNetworkCountryIso())`). So while
roaming in Portugal, `912345678` arrives at the service already as `+351912345678`.

That normalized URI is only ever *offered* to the redirection service. Every terminal
path in `CallRedirectionProcessor` that does not receive an explicit `redirectCall()`
places `mDestinationUri` — the original dial string. With no redirection app registered,
the raw digits are dialled.

**So the app's load-bearing behaviour is calling `redirectCall()` with the handle Telecom
supplied.** That is what promotes the framework's own normalization into the number
actually dialled.

Consequences to keep in mind:

- In the common roaming case `NumberRewriter.evaluate()` returns
  `PassThrough("Already international")` and does not rewrite anything. This is expected,
  not a bug. The service logs those as `(system-prefixed)` so the log is not misleadingly
  empty.
- `NumberRewriter` is the **fallback** for numbers Telecom could not parse for the visited
  region, which it passes through unchanged. That is the path where the rewrite logic
  genuinely fires.
- Because a local-format number is ambiguous, that fallback cannot distinguish a
  visited-country number from a home-country number in national format: roaming in
  Portugal, a Dutch `0612345678` becomes `+351612345678`. Documented in the README as a
  known limitation; do not "fix" it by guessing.

## Architecture

The app has one job: make outgoing calls dial in international format when roaming.

- **`NumberRewriter`** — pure function, all rewrite logic lives here. Stateless, easily
  testable. Also exposes `isDestinedForCountry()` for local SIM routing decisions.
- **`RewriteLog`** — pure retention rules for the on-screen log, kept out of
  `PreferencesRepository` so they are testable without DataStore or a Context.
- **`RoamerCallRedirectionService`** — thin Android service wrapper. Reads prefs
  synchronously (`runBlocking`), delegates to `NumberRewriter`, resolves local SIM if
  enabled, always calls `redirectCall()` on the calling thread.
- **`CountryDialCodes`** — static mapping, no logic beyond lookup.
- **`PreferencesRepository`** — DataStore wrapper for settings persistence.
- **`MainActivity`** — settings UI, observe-only (no business logic).

## Key Constraints

- `onPlaceCall()` must respond within ~5 seconds. Never do network I/O or heavy work there.
- `redirectCall()` must be called on the binder thread (not from a background coroutine).
- **Never use `placeCallUnmodified()`** — it places `mDestinationUri`, the original
  pre-normalization dial string, discarding the E.164 form. Always use
  `redirectCall(handle, ...)`.
- Italy does NOT use a trunk prefix — the leading `0` is part of the subscriber number.
  The `noTrunkPrefixCountries` set handles this.
- USSD/MMI codes (`*`, `#` prefixed) must never be rewritten.
- **Local SIM routing is independent of number rewriting** — it runs after the rewrite
  decision. The number always stays in international format; only the
  `PhoneAccountHandle` changes.
- `findLocalSimAccount()` catches `SecurityException` so it silently falls back if
  `READ_PHONE_STATE` is not granted.
- `isDestinedForCountry()` requires at least `MIN_SUBSCRIBER_LENGTH` digits after the dial
  code, so a too-short number is not mistaken for a real subscriber number in that
  country. No dial code in `CountryDialCodes` is a prefix of another, so that check is a
  length sanity check rather than a defence against prefix collisions — a test asserts the
  no-prefix property, which is what keeps the simple `startsWith` match correct.

## Signing

Credentials are never committed. Local builds read a gitignored `keystore.properties` at
the repository root; CI reads `ROAMER_KEYSTORE_PASSWORD` / `ROAMER_KEY_PASSWORD` /
`ROAMER_KEY_ALIAS`. With no credentials, `assembleRelease` still succeeds but emits an
unsigned `roamer-release-unsigned.apk`, and `bin/release.sh` refuses to publish it. See
the README for setup.

## Releasing

`make release VERSION=x.y NOTES=file` runs `bin/release.sh`, which pre-flights everything
that can fail before touching the tree — clean tree on `main`, unused tag, notes file
present, `gh` authenticated, signing credentials available, tests green — then bumps
`versionCode`/`versionName` in `app/build.gradle.kts`, builds a signed `assembleRelease`
APK, verifies the APK is actually signed, commits, tags `vX.Y`, pushes, and creates a
GitHub Release with the APK attached. A notes file is required; `DRAFT=1` creates a draft.
A failure during the build rolls the version bump back.

The release APK is named `roamer-release.apk` via `base.archivesName`.

## Testing

All rewrite logic is in `NumberRewriter` (pure object, no Android dependencies). Tests live
in `app/src/test/` and run on the JVM — no emulator needed. `make check` runs lint and
tests together.

Some tests assert through `kotlin.assert`, which the JVM evaluates only with assertions
enabled; `testOptions { unitTests.all { it.enableAssertions = true } }` states that
explicitly so those tests cannot silently become no-ops. Prefer `assertTrue`/`assertFalse`
in new tests.

When adding a test for a guard, confirm it actually exercises the guard — mutate the guard
and check the test fails. A test can pass for a reason unrelated to the one it names.

## Adding a Country

Add to `CountryDialCodes.kt`:
```kotlin
"xx" to "123",  // Country Name
```

If the country doesn't use a trunk prefix (like Italy), also add to
`NumberRewriter.noTrunkPrefixCountries`.

A new dial code must not be a prefix of an existing one, nor have an existing one as its
prefix; `CountryDialCodesTest` asserts this, because `isDestinedForCountry()` matches by
`startsWith`.
