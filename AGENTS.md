# Sparklet Android

Native Android client for Sparklet — a TikTok-style vertical learning feed
(short fact-checked cards with real sources, quizzes, guess-before-reveal
challenges, XP/streaks/leaderboard, spaced repetition). The backend is a
separate, already-shipped Next.js/Prisma/Postgres app; this repo is the
Android client only.

## Status

No code yet. A sibling client (`sparklet-ios`, same backend) is further
along and has already surfaced one shared blocker — see "Auth" below before
scaffolding anything here.

## Backend reference

The backend lives in a sibling repo on this machine:
`C:\Users\jayde\onedrive\repos\sparklet`. Treat it as the single source of
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

A second sibling repo, `C:\Users\jayde\onedrive\repos\sparklet-ios`, is a
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

## Open decisions (resolve before scaffolding further)

1. **Auth — likely already decided, confirm before implementing.** The
   backend session is cookie-based (Auth.js, Prisma-adapter DB sessions) and
   there is no token/API-key auth path for native clients yet.
   `sparklet-ios` hit this first: `WKWebView` embedding the OAuth flow trips
   Google's `disallowed_useragent` block, so cookie-carrying webviews are out
   for Google sign-in specifically. Android has the same restriction for
   embedded WebViews doing Google OAuth (Google requires Custom Tabs /
   AppAuth-style external-user-agent flow). The iOS repo's resolution: the
   backend grows a token-based fallback — `/login?client=<platform>&callback=<scheme>`
   opened in the system browser context (`ASWebAuthenticationSession` on iOS,
   Custom Tabs on Android), and on successful sign-in the backend redirects
   to `<scheme>://auth?token=<sessionToken>` instead of `/feed`, using the
   existing `Session.sessionToken` row rather than a new auth strategy. Only
   `auth()` in `src/auth.ts` needs a `Bearer <token>` fallback, not every
   call site. **That backend change has not been implemented yet as of
   2026-07-28** (confirmed via `sparklet-ios/AGENTS.md`) — if you implement
   it, do it once in the shared `sparklet` backend for both platforms, not
   twice, and confirm with the user first since that's a shipped production
   app deployed off pushes to `main`.
2. **Push.** Backend push is VAPID web-push (`PushSubscription` model,
   `src/lib/push.ts`) — cannot run in a native app (no service worker).
   Native Android push needs Firebase Cloud Messaging: a device registration
   token, not a VAPID subscription. Decide whether v1 ships with no
   notifications (iOS is doing this) or adds an FCM path — if the backend's
   `PushSubscription` model gets a `platform` discriminator column for one
   native client, do it for both at once rather than migrating twice.
3. **Client architecture.** Native Kotlin/Jetpack Compose vs. a hybrid
   (Capacitor/React Native) wrapping the existing web app. `sparklet-ios`
   went native SwiftUI for feed feel/performance; the same reasoning applies
   here, but confirm before scaffolding since it commits to rebuilding the
   feed/quiz/guess/misconception UI natively rather than reusing web code.

## Commands

No build yet — no Gradle project scaffolded. Once it exists, replace this
section with the real module name and Gradle invocation (for local builds
and any CI), plus how to point a local build at the backend (local Next.js
dev server on the LAN IP + `PORT=3001`, vs. sparkletapp.com — Android
emulators reach the host machine via `10.0.2.2`, not `localhost`).
