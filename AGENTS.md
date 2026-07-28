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

## Decisions made

- **Auth: token-based, backend built and pushed to `sparklet`'s `main` as of
  2026-07-28** (commit `89be8be`, deploying via Coolify same day). This is
  no longer open — implement Android's side against the real contract below
  rather than re-deciding it. Embedded WebViews
  doing Google OAuth trip Google's `disallowed_useragent` block on both
  platforms, so sign-in has to happen in an external user-agent (Custom Tabs
  here, matching `ASWebAuthenticationSession` on iOS), and the raw session
  cookie that flow produces can't cross into a native app anyway. The
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
