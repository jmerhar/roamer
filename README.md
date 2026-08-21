# Roamer

Android app that makes outgoing calls dial in unambiguous international format while
you're roaming, so you can tap-to-call a local number from Google Maps without editing
it first.

## The Problem

Abroad on your home SIM, a number in local format is ambiguous: `912 345 678` is only a
Portuguese number if something supplies the `+351`.

In practice a visited network will often route a local number as a local call, so dialling
one is not guaranteed to fail — GSMA's roaming guidance reserves its "use full
international format" advice for calls home or to a third country. What full international
format buys you is determinism: `+351912345678` means one thing everywhere, to every
operator, with no dependence on how the visited network chooses to interpret bare digits.

Android already works out that international form — but it does not dial it.

## How It Works

Before handing an outgoing call to a call redirection service, Android's Telecom layer
normalizes the dialled number to E.164 using the **visited network's** country, not the
SIM's:

```java
// CallRedirectionProcessorHelper.formatNumberToE164()
PhoneNumberUtils.formatNumberToE164(number, tm.getNetworkCountryIso())
```

That normalized number is only ever *offered* to the redirection service. Every path in
Telecom that does not receive an explicit `redirectCall()` places the original dial
string instead. With no redirection app installed, the digits you typed are the digits
that go out.

Roamer registers as that service and always answers with `redirectCall()`, passing back
the handle Telecom supplied. That single decision is what makes the E.164 form the number
actually dialled. It never calls `placeCallUnmodified()`, which would revert to the
original dial string and undo the normalization.

When Telecom *cannot* parse the number for the visited region it passes the original
string through untouched. `NumberRewriter` is the fallback for that case: it strips the
trunk prefix where appropriate and prepends the visited country's dial code itself.

Either way the result is the same: the call is placed in international format.

## What Is Not Rewritten

- Numbers already in international form (`+…` or `00…`) — passed through
- USSD/MMI codes (`*100#`, `#31#…`) — never touched
- Short and service numbers, including emergency numbers like `112`
- Every call when the SIM country matches the network country, i.e. when not roaming

## Known Limitation

A number in local format is genuinely ambiguous, and Roamer cannot resolve that
ambiguity: it has no way to distinguish a visited-country number from a home-country
number typed in national format. While roaming in Portugal, a Dutch mobile entered as
`0612345678` becomes `+351612345678`.

Dial home-country numbers in full international format (or save contacts that way, which
Android does by default). The rewrite log in the app shows what each call was turned into.

## Features

- **Automatic detection** — uses the cellular network to determine which country you're in
- **Manual override** — pick a country manually when on WiFi-only or if detection fails
- **Local SIM routing** — optionally route local calls through a local SIM (dual-SIM phones)
- **Italy-aware** — Italian numbers keep their leading `0`, which is part of the subscriber
  number rather than a trunk prefix
- **Rewrite log** — see what recent calls were dialled as

## Requirements

- Android 10+ (API 29) — required for `CallRedirectionService`
- Must be set as the default call redirection app (prompted on first launch)
- `READ_PHONE_STATE` is declared in the manifest and requested at runtime only when you
  enable local SIM routing; the app works without granting it
- Dual-SIM with a local SIM for the local SIM routing feature (optional)

## Building

Requires the Android SDK installed locally. No signing credentials are needed to build,
test, or lint.

```sh
make help           # show all commands
make build          # build debug APK
make debug          # build & install debug on connected device
make test           # run unit tests
make lint           # Android Lint + ShellCheck
make check          # lint + tests (run this before pushing)
make build-release  # build release APK
make install        # build & install release on connected device
```

## Releasing

Releases are built and published from `main` with a single command.
[`bin/release.sh`](bin/release.sh) runs the checks, bumps the version, builds a signed
release APK, commits the bump, tags it `vX.Y`, pushes, and publishes a GitHub Release with
the APK attached.

```sh
make release VERSION=1.1 NOTES=notes.md            # release with notes
make release VERSION=1.1 NOTES=notes.md DRAFT=1    # create a draft release
```

Release notes are required — every release must ship a notes file explaining what changed.

Pre-flight checks run before anything is modified: clean working tree on `main`, tag not
already taken, notes file present, `gh` installed and authenticated, signing credentials
available, and the full test suite green. If the build fails the version bump is rolled
back, leaving the tree as it was found.

### Signing setup

Signing credentials are kept out of version control. Create a keystore and a gitignored
`keystore.properties` at the repository root:

```sh
keytool -genkeypair -keystore keystore/release.keystore -alias roamer \
    -keyalg RSA -keysize 4096 -validity 10000
```

```properties
# keystore.properties — gitignored, never commit
storePassword=…
keyPassword=…
keyAlias=roamer
```

`ROAMER_KEYSTORE_PASSWORD`, `ROAMER_KEY_PASSWORD`, and `ROAMER_KEY_ALIAS` are read as
alternatives, for CI. Without credentials the release build still succeeds but produces an
unsigned APK, and the release script refuses to publish it.

Back the keystore up somewhere safe. Android only accepts an update to an installed app if
it is signed with the same key, so losing it means existing installs cannot be upgraded.

## Supported Countries

All EU member states, the remaining EEA states, and common nearby destinations:

AT, BE, BG, HR, CY, CZ, DK, EE, FI, FR, DE, GR, HU, IE, IT, LV, LT, LU, MT, NL, PL, PT,
RO, SK, SI, ES, SE, IS, LI, NO, CH, GB, US, CA, AU, TR, RS, BA, ME, MK, AL, XK

A country that is not listed is left alone: the call passes through unrewritten rather
than being given a wrong prefix.

## Project Structure

```
app/src/main/kotlin/si/merhar/roamer/
├── RoamerCallRedirectionService.kt  # System service — intercepts calls
├── NumberRewriter.kt                # Pure rewrite logic (testable)
├── CountryDialCodes.kt              # ISO → dial code mapping
├── RewriteLog.kt                    # Pure log-retention rules
├── PreferencesRepository.kt         # DataStore persistence
└── MainActivity.kt                  # Settings UI
```

## License

[GNU General Public License v3.0](LICENSE)
