# Sparklet Android

Native Android client for Sparklet — a TikTok-style vertical learning feed
(short fact-checked cards with real sources, quizzes, guess-before-reveal
challenges, XP/streaks/leaderboard, spaced repetition). The backend is a
separate, already-shipped Next.js/Prisma/Postgres app; this repo is the
Android client only.

## Status

Scaffolded (2026-07-28): native Kotlin/Jetpack Compose, a Gradle project
(no wrapper committed — see Commands), a paged single-card feed screen
backed by `GET /api/feed`, the two-POST read-tracking flow against
`/api/interactions` (tracking only the one card actually settled on
screen — see the comment on `FeedScreen.kt`'s `pagerState.settledPage`
usage, this was a real integrity bug in `sparklet-ios`'s earlier pass and
is worth not repeating here), and a header stats row backed by
`GET /api/profile`. Auth (`auth/LoginController.kt`,
`auth/AuthRedirect.kt`) matches the backend's real mobile-auth contract
(code-exchange via Custom Tabs, not `sparklet-ios`'s
`ASWebAuthenticationSession` callback shape — Custom Tabs has no direct
callback, so the redirect is bridged back via `AuthRedirect`'s
`onNewIntent`/`onResume` handling instead), confirmed live against
`sparklet`'s `main` (commit `89be8be`) and with `sparklet-android` already
in `ALLOWED_MOBILE_SCHEMES` while scaffolding this. Not yet built:
quiz/guess/misconception/review answering (`sparklet-ios` hasn't built
that either, so this isn't Android falling behind a finished web/iOS
surface).

**Gradle sync verified (2026-07-29)**: opened in Android Studio (bundled
JBR 21 + Android SDK with platform `android-36.1`/build-tools `36.0.0`),
which synced successfully using its own bundled Gradle — auto-upgrading
AGP to `8.13.2`, the Gradle wrapper target to `8.13`, adding the
`org.gradle.toolchains.foojay-resolver-convention` plugin to
`settings.gradle.kts` (needed to auto-resolve JDK toolchains), and
generating `gradle/gradle-daemon-jvm.properties` pinning the daemon to
JetBrains JDK 21. That verifies the Gradle *configuration* — dependency
resolution, plugin versions — but not yet a full compile of the Kotlin/
Compose sources; run Build → Make Project (or the Run button) to confirm
those. `compileSdk`/`targetSdk` are still `34` in `app/build.gradle.kts`
while the SDK only has platform `36.1` installed — if a real build asks
for platform 34, that's Studio's SDK Manager doing its job, not a project
misconfiguration. No `gradlew`/wrapper jar was generated — Studio synced
via its own bundled Gradle rather than materializing the project's own
wrapper — so a command-line build still needs that step done manually
(`gradle wrapper` once a local Gradle install exists).

## Backend reference

The backend lives in a sibling repo on this machine:
`C:\Users\jayde\repos\Sparklet`. Treat it as the single source of
truth for the API contract — read there, don't duplicate or guess:

- `AGENTS.md` — architecture, conventions, engagement-integrity rules (read
  this first; the rules below assume it)
- `src/auth.ts` — auth setup (Auth.js v5: Google, Apple, magic-link via
  Nodemailer; Prisma-adapter DB sessions)
- `prisma/schema.prisma` — data model
- `src/app/api/**` — route handlers this app will consume, including
  `GET /api/profile` (XP/streak/goal state — `?tz=<minutes>` offset param;
  added for exactly this purpose, see its comment)
- `src/lib/xp.ts`, `src/lib/feed.ts` — XP/streak/feed-composition rules the
  client must respect rather than reimplement independently

A second sibling repo, `C:\Users\jayde\repos\sparklet-ios`, is a
native iOS client against the same backend and API contract. Its `AGENTS.md`
is worth reading before making architecture calls here — anything it already
resolved about the backend contract (not UI) applies equally to Android.

When the backend changes shape, re-read the relevant route handler instead of
assuming the previous contract still holds — there is no shared types package
between the repos.

## Server-enforced rules the client must design around

These are enforced server-side regardless of what the client sends, so build
UI that matches them rather than fights them:

- A card only counts as "read" (XP, streak, spaced-repetition recall, the
  demand signal that drives content generation) once a second POST to
  `/api/interactions` lands ≥4.5s after the first, by the *server's* clock.
  A fabricated client-side dwell time does nothing — don't build any
  optimistic-XP UI that assumes otherwise.
- All XP is server-computed and logged as one `XpEvent` row per award. Treat
  server responses as authoritative; don't keep an independently-computed
  client-side XP total as truth.
- The daily card-count goal and the XP ring answer different questions ("did
  I hit my count today" vs "did I hit my XP today") — keep them visually and
  logically separate, same as the web client. The per-user card-count goal
  itself is a client-only preference on web (`localStorage`, never sent to
  the server) — keep the Android equivalent (`SharedPreferences`/DataStore)
  local too rather than expecting the backend to know it.

## Decisions made

1. **Auth: token-based, backend built and pushed to `sparklet`'s `main` as of
  2026-07-28** (commit `89be8be`, deploying via Coolify same day). This is
  no longer open — implement Android's side against the real contract below
  rather than re-deciding it. Embedded WebViews
  doing Google OAuth trip Google's `disallowed_useragent` block on both
  platforms, so sign-in has to happen in an external user-agent (Custom Tabs
  here, matching `ASWebAuthenticationSession` on iOS), and the raw session
  cookie that flow produces can't cross into a native app anyway (unlike
  `ASWebAuthenticationSession`, Custom Tabs has no direct completion
  callback — the redirect arrives as a separate Intent, bridged back to the
  in-flight sign-in via `AuthRedirect`'s `onNewIntent`/`onResume` handling;
  see `auth/AuthRedirect.kt`). The
  backend uses a short-lived one-time-code handoff (RFC 8252-style), not a
  token embedded directly in a redirect — a token in a URL sits in browser
  history/OS logs and goes to whatever app the OS resolves a custom scheme
  to, not necessarily this one:
  1. Open `/login?mobileScheme=sparklet-android` in Custom Tabs.
     `sparklet-android` must exactly match an entry in
     `ALLOWED_MOBILE_SCHEMES` (`src/lib/mobile-auth.ts` in the backend repo)
     — it's a fixed allowlist, not a passthrough; anything else 400s.
  2. Once Google/Apple/magic-link sign-in completes, the backend redirects
     (still holding the session cookie it just set) to
     `/api/auth/mobile-complete?scheme=sparklet-android`, which mints a
     one-time, 60-second-lived code and redirects again to
     `sparklet-android://auth?code=<code>` — register that exact scheme as
     an intent filter (or, better, a verified Android App Link if you want
     protection against another app claiming the same custom scheme; a raw
     custom scheme has no such protection on Android).
  3. The app receives that code via the intent and — over a direct HTTPS
     `POST` from the app itself, never through the browser/Custom Tab —
     exchanges it at `/api/auth/mobile-exchange` (`{ code }` →
     `{ token, expires }`). That token is a freshly minted `Session.sessionToken`
     row, distinct from the browser's own cookie session (revoking one
     doesn't touch the other). Send it thereafter as
     `Authorization: Bearer <token>`.
  4. Sign-out: `DELETE /api/auth/mobile-session` with the same
     `Authorization` header revokes it.
  Session is a sliding 30-day window (extended on use once within 7 days of
  expiring) — no separate refresh flow, just re-run steps 1–3 on a 401.
  Verified end-to-end locally on the backend: cookie auth unaffected by the
  change, valid Bearer works, an invalid/expired Bearer 401s even with a
  valid cookie also present (never silently falls back to it), a replayed
  code is rejected, an unlisted scheme is rejected, sign-out actually
  revokes the token, all against a local dev server. Not yet exercised
  against the deployed app or from an actual Android client — if a request
  against `sparkletapp.com` behaves differently than this section describes,
  trust what you observe over this doc and update it.
2. **Push: deferred for v1, matching `sparklet-ios`.** Existing push is
   VAPID web-push (`PushSubscription` model, `src/lib/push.ts`) — cannot run
   in a native app (no service worker). Native Android push would need
   Firebase Cloud Messaging (a device registration token, not a VAPID
   subscription) plus a real server-side FCM send path, not just a client
   SDK add. v1 ships with zero notifications. This has a real product cost,
   not a free one — streaks/daily-goal are the retention mechanic, and
   shipping without push weakens that from day one; revisit once there's
   appetite to touch `PushSubscription` on both platforms at once (add the
   `platform` discriminator column then, rather than migrating twice).
3. **Client architecture: native Kotlin/Jetpack Compose**, not a hybrid
   (Capacitor/React Native) wrapper. The mobile-auth contract that just
   shipped (see above) is native-shaped by construction — Custom Tabs →
   custom-scheme intent → direct HTTPS POST exchange → `Bearer` token,
   specifically because cookie-carrying webviews are disallowed for Google
   OAuth. A hybrid wrapper would want exactly the cookie-in-webview session
   the backend was just re-engineered to avoid, fighting that work rather
   than using it. `sparklet-ios` went native SwiftUI for the same reasoning
   plus feed feel/performance. Cost: rebuilding feed/quiz/guess/
   misconception/review UI natively rather than reusing the web app's React
   code — and `sparklet-ios` hasn't finished the quiz/guess/review-answering
   surface either, so Android starts behind web on that UI, not at parity.

## Commands

No Gradle wrapper is committed — generating `gradlew`/`gradle-wrapper.jar`
needs a local Gradle install, which the environment this was scaffolded in
doesn't have. Requires Android Studio (bundles a JDK; installs SDK
platforms via its SDK Manager):

```bash
# Open the repo root directly in Android Studio — it recognizes the
# settings.gradle.kts/build.gradle.kts files and offers to generate the
# wrapper and sync on first open.
```

No CI yet. To point a local build at the `sparklet` dev server instead of
production, edit `app/src/main/java/com/sparklet/android/config/AppConfig.kt`'s
`apiBaseUrl` — Android emulators reach the host machine via `10.0.2.2`, not
`localhost` (already allowed for cleartext HTTP in
`app/src/main/res/xml/network_security_config.xml`); a physical device
needs your machine's LAN IP added there instead. Either way, use the port
from that repo's `npm run dev` (`PORT=3001`).
