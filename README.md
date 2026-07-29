# Sparklet Android

Native Kotlin/Jetpack Compose client for Sparklet. See `AGENTS.md` for
architecture and the backend contract this app depends on.

## Status

Scaffold, with Gradle sync now verified in Android Studio (2026-07-29) —
BUILD SUCCESSFUL after Studio auto-upgraded AGP to `8.13.2`, the wrapper
target to Gradle `8.13`, and added the `foojay-resolver-convention` plugin
for JDK toolchain resolution. That confirms the project's Gradle
configuration and dependency graph resolve; it does not yet confirm the
Kotlin/Compose sources themselves compile clean — run **Build → Make
Project** to check that next, and report back anything that fails.

Written to mirror the further-along `sparklet-ios` client's scaffolded
surface: a Custom-Tabs-based sign-in against the real mobile-auth contract,
a paged single-card feed screen backed by `GET /api/feed`, the two-POST
read-tracking flow against `/api/interactions` (tracking only the one card
actually settled on screen — see the comment in `FeedScreen.kt`, this was a
real integrity bug in the iOS client's earlier pass and is worth not
repeating here), and a header stats row backed by `GET /api/profile`. Not
yet built: quiz/guess/misconception/review answering (iOS hasn't built that
either).

`compileSdk`/`targetSdk` are still `34` while the installed SDK only has
platform `36.1` — if a build prompts to install platform 34 via SDK
Manager, that's expected, not a project bug.

## Building

No Gradle wrapper is committed (`gradlew`/`gradle-wrapper.jar` — generating
them needs a local Gradle install). Confirmed as of 2026-07-29: Android
Studio doesn't need it either — opening this directory directly, it
recognized the `settings.gradle.kts`/`build.gradle.kts` files and synced
using its own bundled Gradle, without ever materializing the project's own
wrapper scripts. A command-line build (`./gradlew ...`) still needs that
wrapper generated manually once a local Gradle install exists
(`gradle wrapper`); building through Android Studio does not.

1. Install [Android Studio](https://developer.android.com/studio) (bundles
   a JDK and lets you install SDK platforms/tools through its SDK Manager).
2. Open this directory directly and let it sync — click "Sync Now" if it
   shows a banner saying Gradle files changed (expected the first time, as
   Studio settles on its own AGP/Gradle version choices).
3. Requires JDK 17 (set in `app/build.gradle.kts`'s `compileOptions`) and
   `compileSdk`/`targetSdk` 34 — Android Studio will prompt to install
   whatever SDK platform is missing.

## Pointing at a local backend

`app/src/main/java/com/sparklet/android/config/AppConfig.kt` hardcodes
`apiBaseUrl` to `https://sparkletapp.com`. To test against a local
`sparklet` dev server instead:

- From the emulator, the host machine is `10.0.2.2`, not `localhost` —
  already allowed for cleartext HTTP in
  `app/src/main/res/xml/network_security_config.xml`.
- From a physical device, use your machine's LAN IP instead, and add that
  IP to `network_security_config.xml` (or run the dev server over HTTPS).
- Either way, use the port from the `sparklet` repo's `npm run dev`
  (`PORT=3001`), e.g. `http://10.0.2.2:3001`.

## Testing sign-in

The backend's mobile-auth contract (commit `89be8be` on `sparklet`'s `main`,
deployed via Coolify as of 2026-07-28) is live in production, and
`sparklet-android` is already in its `ALLOWED_MOBILE_SCHEMES` allowlist —
confirmed directly in `sparklet/src/lib/mobile-auth.ts` while scaffolding
this. Sign-in has not been exercised against an actual device/emulator from
this client, though — if a request behaves differently than `AGENTS.md`
describes, trust what you observe over that doc and update it.
