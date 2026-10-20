# Roamer — Development Guide

## Build & Test

```sh
make build          # debug APK
make debug          # install debug to connected device
make test           # unit tests + bats tests for bin/release.sh
make lint           # Android Lint + ShellCheck on bin/ and test/
make coverage       # Kover + kcov, then the shared coverage gate
make check          # lint + tests + coverage — run before pushing
make build-release  # release APK
make install        # install release to connected device
make clean          # clean build and coverage outputs
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
  region, which it passes through unchanged. It is gated behind the `useFallbackRewrite`
  preference, **off by default**, because the rewrite has to assume a local-format number
  belongs to the country the device is in — wrong for a home-country number in national
  format, which becomes a live number in the wrong country (roaming in Portugal, a Dutch
  `0612345678` becomes `+351612345678`). Do not "fix" that by guessing harder; it is not
  decidable from the number.
- Measured on a device roaming in PT on an NL SIM: 14 of 14 logged calls took the
  framework path, and the fallback fired zero times. Treat it as rare by default.
- `evaluate()` reports outcomes decided *before* the rewrite (already international, USSD,
  short number, not roaming) whether or not the fallback is on, so the log still explains
  why a call was left alone. Only the rewrite itself is gated.

## Architecture

The app's main job is to make outgoing calls dial in international format when roaming;
optionally it also chooses which SIM places a call.

- **`NumberRewriter`** — pure function, all rewrite logic lives here. Stateless, easily
  testable. Also exposes `isDestinedForCountry()` for local SIM routing decisions.
- **`SimRouting`** — pure SIM-selection rules for `useNumberCountrySim` and `useLocalSim`,
  kept out of the service so they are testable without TelecomManager.
- **`RewriteLog`** — pure retention rules for the on-screen log, kept out of
  `PreferencesRepository` so they are testable without DataStore or a Context.
- **`RoamerCallRedirectionService`** — thin Android service wrapper. Reads prefs
  synchronously (`runBlocking`), delegates to `NumberRewriter`, resolves the SIM to
  route through if enabled, always calls `redirectCall()` on the calling thread.
- **`CountryDialCodes`** — static mapping, no logic beyond lookup.
- **`PreferencesRepository`** — DataStore wrapper for settings persistence. `enabled`
  (default on) is the master switch; `useFallbackRewrite` (default off) gates the rewriter;
  `useLocalSim` and `useNumberCountrySim` are mutually exclusive (enabling one disables
  the other in the same write); they and `manualCountry` are independent of the rest.
- **`MainActivity`** — settings UI, observe-only (no business logic).

## Key Constraints

- `onPlaceCall()` must respond within ~5 seconds. Never do network I/O or heavy work there.
- `redirectCall()` must be called on the binder thread (not from a background coroutine).
- **`placeCallUnmodified()` is only for the disabled state.** It places `mDestinationUri`,
  the original pre-normalization dial string, discarding the E.164 form — which is exactly
  what "behave as if this app were not installed" means, and so is how the master `enabled`
  preference expresses off. While enabled, always answer with `redirectCall(handle, ...)`;
  using `placeCallUnmodified()` there would silently defeat the app's entire purpose.
- **Trunk and international prefixes are per country, in `CountryDialCodes.NumberingPlan`.**
  Do not reintroduce a hardcoded `0`/`00`. Three countries in the set break the European
  norm: Hungary dials domestic long distance as `06`, and the NANP (US/Canada) uses trunk
  `1` with international `011`. Italy has no trunk prefix and its leading `0` is part of the
  subscriber number, so it is the one country with an empty `trunkPrefixes`.
- Countries whose plan defines *no* trunk prefix still keep the default `0`, deliberately: a
  valid national number there cannot begin with `0`, so a leading `0` is a typing habit and
  removing it recovers a valid number (`0912345678` in Portugal → `+351912345678`).
- USSD/MMI codes (`*`, `#` prefixed) must never be rewritten.
- **Service numbers in the 11x ranges are never rewritten**: EU-harmonised `116xxx` and
  national directory enquiries like France's `118XYZ` are six digits or fewer with no
  international form. The rule is length ≤ 6 and a `11` prefix, which cannot catch a
  geographic number — the shortest such national numbers (Hungary, Poland) are 8–9 digits.
- A result longer than 15 digits is rejected rather than dialled, per the E.164 ceiling.
- **SIM routing is independent of number rewriting** — it runs after the rewrite
  decision. The number always stays in international format; only the
  `PhoneAccountHandle` changes. `useLocalSim` switches only while roaming, for numbers
  destined for the visited country; `useNumberCountrySim` switches for any number whose
  country matches a SIM, wherever the device is. The second covers every call the first
  would move, so the settings keep them mutually exclusive. `SimRouting.choose()` still
  gives the number-country rule precedence, and treats its match as final even when it is
  the initial account, so a state with both on stays well defined. Both prefer the initial
  account when several SIMs qualify.
- `callCapableSims()` catches `SecurityException` so it silently falls back if
  `READ_PHONE_STATE` is not granted.
- `isDestinedForCountry()` requires at least `MIN_SUBSCRIBER_LENGTH` digits after the dial
  code, so a too-short number is not mistaken for a real subscriber number in that
  country. No dial code in `CountryDialCodes` is a prefix of another, so that check is a
  length sanity check rather than a defence against prefix collisions — a test asserts the
  no-prefix property, which is what keeps the simple `startsWith` match correct.

## Toolchain

AGP provides Kotlin support itself, so there is **no `org.jetbrains.kotlin.android`
plugin** — adding one back fails the build outright. Two consequences that are easy to trip
over:

- `jvmTarget` is not set anywhere. It follows `android.compileOptions.targetCompatibility`,
  so changing the Java level moves the Kotlin target with it. (Verified: compiled classes
  report bytecode major version 61 = Java 17.)
- `kotlin-test-junit` must match the Kotlin version AGP bundles, not the newest release.
  A newer one fails with "Module was compiled with an incompatible version of Kotlin",
  naming the expected metadata version — use that. Dependabot will propose newer
  `kotlin-test` versions that cannot be taken for this reason.

`compileSdk` is ahead of `targetSdk` on purpose: AndroidX requires a recent `compileSdk` to
compile against, while raising `targetSdk` opts into new runtime behaviour and needs testing
on a device.

## Signing

Credentials are never committed. Local builds read a gitignored `keystore.properties` at
the repository root; CI reads `ROAMER_KEYSTORE_PASSWORD` / `ROAMER_KEY_PASSWORD` /
`ROAMER_KEY_ALIAS`. With no credentials, `assembleRelease` still succeeds but emits an
unsigned `roamer-release-unsigned.apk`, and `bin/release.sh` refuses to publish it. See
the README for setup.

## Releasing

`make release VERSION=x.y NOTES=file` runs `bin/release.sh`, which pre-flights everything
that can fail before touching the tree — clean tree on `main` in step with `origin/main`,
unused tag, notes file present, `gh` authenticated, signing credentials available, tests
green — then bumps `versionCode`/`versionName` in `app/build.gradle.kts`, builds a signed
`assembleRelease` APK, verifies the APK is actually signed, commits, tags `vX.Y`, pushes, and creates a
GitHub Release with the APK attached. A notes file is required; `DRAFT=1` creates a draft.
A failure during the build rolls the version bump back.

The release APK is named `roamer-release.apk` via `base.archivesName`.

## Testing

All rewrite logic is in `NumberRewriter` (pure object, no Android dependencies). Tests live
in `app/src/test/` and run on the JVM — no emulator needed. `make check` runs lint, tests
and the coverage gates together.

`bin/release.sh` is tested by `test/release.bats`, against a throwaway repository with a bare
origin and stub `gradlew`, `gh` and `apksigner` (see `test/test_helper.bash`), so nothing is
built or published.

Coverage is configured in `coverage.toml` and enforced in CI by the shared
`jmerhar/coverage` actions. Kover excludes the Android framework classes in
`app/build.gradle.kts`; keep logic out of them so it stays measured.
`bin/run-shell-coverage.sh` uses a local kcov and bats when installed, otherwise the pinned
container. Stock kcov on macOS fails the suite — it traces with `/bin/bash` 3.2, and with
`--bash-parser` it fails whenever `ulimit -n` exceeds `kern.maxfilesperproc` — so locally it
needs a kcov wrapper that fixes both, or `KCOV_FORCE_DOCKER=1`. Do not add the workaround to
this repo; it belongs in the wrapper.

Some tests assert through `kotlin.assert`, which the JVM evaluates only with assertions
enabled; `testOptions { unitTests.all { it.enableAssertions = true } }` states that
explicitly so those tests cannot silently become no-ops. Prefer `assertTrue`/`assertFalse`
in new tests.

When adding a test for a guard, confirm it actually exercises the guard — mutate the guard
and check the test fails. A test can pass for a reason unrelated to the one it names.

## Adding a Country

Add to `CountryDialCodes.plans`, checking the country's national numbering plan rather than
assuming the European norm:

```kotlin
"xx" to NumberingPlan("123"),                                   // trunk 0, international 00
"xx" to NumberingPlan("123", trunkPrefixes = emptyList()),      // leading 0 is part of the NSN
"xx" to NumberingPlan("123", listOf("06", "0"), listOf("00")),  // multi-digit trunk prefix
```

Two invariants a new entry must not break, both covered by `CountryDialCodesTest`:

- The dial code must not be a prefix of an existing one, nor have one as its prefix —
  `isDestinedForCountry()` matches by `startsWith`.
- Trunk prefixes are matched longest-first, so list a multi-digit prefix alongside the bare
  `0` it shadows if both are valid domestically.
