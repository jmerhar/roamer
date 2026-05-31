# Roamer

Android app that automatically adds international prefixes to local phone numbers when you're roaming. Tap-to-call from Google Maps without manually editing numbers.

## The Problem

When roaming abroad with your home SIM (e.g., a Dutch SIM in Portugal), local numbers from Google Maps don't work — you need to add the country prefix (+351) before dialing. This means copying the number, editing it, and then calling. Every single time.

## The Solution

Roamer registers as the system call redirection service. All outgoing calls pass through it transparently. When you're roaming, it detects the local network country and prepends the correct international prefix. Numbers that are already international are left untouched.

**Example:** You're in Portugal with a Dutch SIM. Google Maps shows a restaurant's number as `912 345 678`. You tap call. Roamer rewrites it to `+351912345678` before the call goes through.

## How It Works

1. Android routes all outgoing calls through the registered `CallRedirectionService`
2. Roamer compares your SIM country (home) with the network country (current location)
3. If they differ → you're roaming → local numbers get the international prefix
4. If the number already starts with `+` or `00`, it passes through unchanged
5. Emergency numbers, short codes, and USSD codes are never modified

## Features

- **Automatic detection** — uses the cellular network to determine which country you're in
- **Manual override** — pick a country manually when on WiFi-only or if detection fails
- **Italy-aware** — correctly handles Italy's numbering (no trunk prefix stripping)
- **USSD pass-through** — codes like `*100#` are never modified
- **Rewrite log** — see recent rewrites for debugging
- **Zero permissions** — no contacts, no call log, no internet required

## Requirements

- Android 10+ (API 29) — required for `CallRedirectionService`
- Must be set as the default call redirection app (prompted on first launch)

## Building

Requires Android SDK installed locally.

```sh
make help      # show all commands
make build     # build debug APK
make debug     # build & install on connected device
make test      # run unit tests
```

## Supported Countries

All EU/EEA member states plus common travel destinations:

AT, BE, BG, HR, CY, CZ, DK, EE, FI, FR, DE, GR, HU, IE, IT, LV, LT, LU, MT, NL, PL, PT, RO, SK, SI, ES, SE, IS, LI, NO, CH, GB, US, CA, AU, TR, RS, BA, ME, MK, AL, XK

## Project Structure

```
app/src/main/kotlin/si/merhar/roamer/
├── RoamerCallRedirectionService.kt  # System service — intercepts calls
├── NumberRewriter.kt                # Pure rewrite logic (testable)
├── CountryDialCodes.kt              # ISO → dial code mapping
├── PreferencesRepository.kt         # DataStore persistence
└── MainActivity.kt                  # Settings UI
```

## License

Personal use.
