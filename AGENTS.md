# Sparklet Android

Native Android client for Sparklet — a TikTok-style vertical learning feed
(short fact-checked cards with real sources, quizzes, guess-before-reveal
challenges, XP/streaks/leaderboard, spaced repetition). The backend is a
separate, already-shipped Next.js/Prisma/Postgres app; this repo is the
Android client only.

## Status

**Updated 2026-09-12: the iOS port is feature-complete except ads and
billing** (decision 7), and was exercised end-to-end on a physical device —
leaderboard, notifications, profile, friends, knowledge map, feed settings and
topic filtering, card actions, card detail, depth switching, explain-back,
recap slides, the invite deep link and sign-out. README's "Needs on-device
verification" lists the two things still untested (onboarding and the friends
request lifecycle, both needing a second account) and what that test pass
found. `MISTAKES.md` records why two of those features were broken when first
run.

**Updated 2026-09-11: builds and runs on a physical device** (Galaxy Z Flip 7,
`SM-F766B`, Android 16) from a command-line `./gradlew :app:assembleDebug` —
a Gradle wrapper is now committed, so no local Gradle install is needed. The
Kotlin/Compose sources are confirmed to compile, not merely resolve. The
quiz/guess/misconception/review answering surface listed as "not yet built"
below has since been built (commit `4689c97`), putting Android ahead of
`sparklet-ios` there. Sign-in, feed, stats header and challenge
cards are confirmed working end-to-end against production on-device. With the
Sparklet PWA installed, Chrome routes sign-in through the PWA instead of the
Custom Tab; it still completes and hands back via `sparklet-android://auth`,
but the detour is confusing UX worth fixing. See README's "Testing sign-in".

Scaffolded (2026-07-28): native Kotlin/Jetpack Compose, a Gradle project,
a paged single-card feed screen backed by `GET /api/feed`, the two-POST
read-tracking flow against
`/api/interactions` (tracking only the one card actually settled on
screen — see the comment on `FeedScreen.kt`'s `pagerState.settledPage`
usage, this was a real integrity bug in `sparklet-ios`'s earlier pass and
is worth not repeating here), and a header stats row backed by
`GET /api/profile`. Auth (`auth/LoginController.kt`,
`auth/AuthRedirect.kt`) matches the backend's real mobile-auth contract
(code-exchange via Custom Tabs, not `sparklet-ios`'s
`ASWebAuthenticationSession` callback shape — Custom Tabs has no direct
callback, so the redirect arrives as a fresh Intent and is handled at process
scope by `AuthSession`; `auth/AuthRedirect.kt`, which used to bridge it into
a screen-scoped coroutine, was removed 2026-09-11, see decision 6), confirmed live against
`sparklet`'s `main` (commit `89be8be`) and with `sparklet-android` already
in `ALLOWED_MOBILE_SCHEMES` while scaffolding this. (Quiz/guess/
misconception/review answering was listed here as not yet built; it was
built in `4689c97` — see the status note above.)

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
misconfiguration. (Superseded 2026-09-11: a full compile is now verified,
and a wrapper is committed — command-line builds need no manual step. The
installed platform is `android-36`, not `36.1`; AGP fetches platform 34 on
demand.)

## Logging mistakes

`MISTAKES.md` in this repo is a running log of mistakes made while working
here — what happened, how it happened, and how it was fixed. **Keep it up to
date.** When you get something wrong, add an entry (newest first) before
moving on.

Log a mistake when it changed what got written, committed, or believed:

- a wrong conclusion that made it into these docs, a commit message, or a
  handoff summary — this is the expensive category, because `AGENTS.md` and
  `README.md` are the handoff between sessions and nothing type-checks them;
- a change to someone's device, environment, or account made on an assumption
  that turned out to be wrong, especially an irreversible one;
- a tooling trap that cost real time and will cost it again (the foldable
  `screencap` trap in `MISTAKES.md` is the model here).

Don't log routine iteration — a compile error you fixed, a first draft you
revised. The bar is "the next session would be worse off not knowing this".

Write each entry with the three headings the file already uses (**What
happened**, **How it happened**, **How it was fixed**), plus a **Rule going
forward** when there is a generalisable one. Be specific and unsparing about
the reasoning error, not just the symptom — "I treated a 2-second window of
silence as proof of a dead end" is useful; "I made an incorrect assumption" is
not.

## Backend reference

The backend lives in a sibling repo checked out next to this one
(`../Sparklet`; the path is machine-specific — it was `C:\Users\jayde\repos\Sparklet`
when this was written, `/Users/jaydendickinson/repos/Sparklet` on the macOS
machine used since). Treat it as the single source of truth for the API
contract — read there, don't duplicate or guess:

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

A second sibling repo, `../sparklet-ios`, is a
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
  callback — the redirect arrives as a separate Intent, handled at process
  scope by `AuthSession`; see decision 6). The
  backend uses a short-lived one-time-code handoff (RFC 8252-style), not a
  token embedded directly in a redirect — a token in a URL sits in browser
  history/OS logs and goes to whatever app the OS resolves a custom scheme
  to, not necessarily this one:
  1. Open `/login?mobileScheme=sparklet-android` in Custom Tabs.
     `sparklet-android` must exactly match an entry in
     `ALLOWED_MOBILE_SCHEMES` (`src/lib/mobile-auth.ts` in the backend repo)
     — it's a fixed allowlist, not a passthrough. **Corrected 2026-09-11:**
     an unlisted scheme does *not* 400 here. `src/app/login/page.tsx` just
     falls through to an ordinary web login (`safeRedirect(callbackUrl)`),
     so a typo'd scheme looks like a successful sign-in in the browser while
     the app waits forever for a redirect that is never sent. The 400 is
     enforced one step later, in `/api/auth/mobile-complete`.
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

4. **PWA sign-in detour: accepted, not fixed (2026-09-11).** On a device with
   the Sparklet PWA installed, Chrome routes `/login` out of the Custom Tab
   and into the WebAPK, so sign-in happens in the PWA and hands back via
   `sparklet-android://auth` (~3.7s). It works; it is just a confusing detour
   through a second Sparklet-branded app. **Don't "fix" this by narrowing the
   PWA manifest `scope`.** `src/app/manifest.ts` sets no explicit scope, so it
   defaults to the `start_url` directory — `/feed` → `/`. Narrowing it to
   `/feed` would drop `/explore`, `/leaderboard`, `/profile`, `/map`,
   `/notifications`, `/card/*`, `/upgrade`, `/onboarding` and `/invite/*` out
   of standalone mode — essentially the whole app — which is a far worse
   regression than the detour it removes. The only clean fix is serving the
   mobile-login entry point from a host the WebAPK doesn't claim (e.g.
   `auth.sparkletapp.com`), which costs DNS + cert + Auth.js cookie/callback
   config + OAuth redirect-URI allowlist changes on a live backend. Judged not
   worth it: users who installed the PWA *and* the native app are a small
   population, and the flow completes for them anyway. Revisit only if that
   assumption stops holding.

5. **JSON bodies must survive kotlinx.serialization's defaults (2026-09-11).**
   `ApiClient.json` sets `encodeDefaults = true` and `explicitNulls = false`,
   and both are load-bearing against the backend's zod schemas — don't
   "simplify" them away. Defaults-omitted silently stripped the required
   `action` field from every `/api/interactions` body, and explicit nulls are
   rejected by `.optional()` (which accepts `undefined`, not `null`). Because
   `trackView` is best-effort by design, both failure modes are invisible:
   the client looks healthy while earning zero XP. When adding a request
   model, check the route's zod schema for which fields are required and
   whether optionals tolerate null.

6. **Sign-in is orchestrated at process scope, not screen scope
   (2026-09-11).** `AuthSession` owns the whole flow and
   `auth/AuthRedirect.kt` is gone. Do not move this back into the UI layer.
   The flow necessarily leaves the app for a Custom Tab, so by the time the
   one-time code arrives as a fresh Intent, the Activity and its composition
   may both have been destroyed. The previous design bridged the redirect
   into a suspend function running in `LoginScreen`'s
   `rememberCoroutineScope` through a `MutableSharedFlow` with `replay = 0`,
   which silently dropped the code whenever nothing was subscribed:

   - **Activity recreation mid-sign-in** — any configuration change, and on a
     foldable that includes simply folding or unfolding while the tab is
     open. The composition is torn down, its scope is cancelled, and the
     emission reaches zero subscribers.
   - **Process death while backgrounded in the browser** — the redirect
     restarts the app, but no coroutine is waiting.

   Both burn a single-use 60-second code and drop the user back on the login
   screen with no error. `AuthSession.onAuthRedirect` therefore redeems any
   code that arrives *without* requiring that a sign-in was started in this
   process, and runs the exchange in its own application-lifetime scope.
   Verified by cold-starting the app straight from a
   `sparklet-android://auth?code=…` Intent with no sign-in in flight: the
   exchange is attempted and its result surfaces. `LoginScreen` holds no
   sign-in state and starts no coroutine — it renders
   `AuthSession.signInState`.

7. **Ads and billing are blocked on inputs, not effort (2026-09-12).** These
   are the only two `sparklet-ios` surfaces not ported, and neither is a
   porting job:

   - **Billing.** The backend has `/api/billing/apple/verify` plus Stripe
     (`checkout`/`portal`/`webhook`) and **no Google Play route at all** —
     grep for `androidpublisher` in the `sparklet` repo and you get nothing.
     Android can't verify a purchase against a backend that has no endpoint
     to verify it with, so this needs a `/api/billing/google/verify` that
     validates a Play purchase token via the Play Developer API, plus Play
     Console products and a service account. Server work first, client
     second. Do not ship a client-trusted "premium = true" in the meantime.
   - **Ads.** iOS uses Google Mobile Ads with UMP consent and ATT. The
     Android equivalent needs its own AdMob app ID and ad unit IDs (they are
     per-platform, so the iOS ones can't be reused) and a UMP consent flow —
     the EEA/UK consent gathering is a legal requirement, not polish. It also
     depends on premium state to know whether to show ads at all, so it is
     gated behind billing above.

   Both are deliberately absent rather than stubbed: a stub here would either
   misreport entitlement or show unconsented ads.

## Commands

A Gradle wrapper **is** committed as of 2026-09-11 (generated with a
standalone Gradle 9.3.1 in a throwaway directory and copied in, pinned to the
Gradle 8.13 the existing `gradle-wrapper.properties` already targeted), so
command-line builds no longer need a local Gradle install — only a JDK 17+:

```bash
export JAVA_HOME=/path/to/a/jdk17
./gradlew :app:assembleDebug     # ~18 min cold, ~20s incremental
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Opening the repo root in Android Studio still works as before.

No CI yet. The API base URL is no longer hardcoded in `AppConfig.kt` — it
defaults to production and is overridden by a Gradle property, so pointing at
a dev server never means editing (or accidentally committing) tracked source:

```properties
# local.properties, gitignored
sparklet.apiBaseUrl=http://192.168.1.42:3001
```

It previously *was* hardcoded, to `http://10.0.2.2:3001` — the emulator's
alias for the host machine, which resolves to nothing on a physical device,
so every device build silently failed every request. Emulators reach the host
at `10.0.2.2`; a physical device needs the machine's LAN IP. Debug builds
permit cleartext to any host (`app/src/debug/res/xml/network_security_config.xml`)
so no per-IP allowlisting is needed; release builds stay HTTPS-only. Use the
port from that repo's `npm run dev` (`PORT=3001`).
