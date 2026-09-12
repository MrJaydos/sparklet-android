# Mistakes

A running log of mistakes made while working on this repo — what happened,
how it happened, and how it was fixed.

The point is not self-flagellation; it is that the expensive mistakes here
have all been **wrong conclusions confidently written into the docs**, and
this repo's docs are the handoff between sessions. A wrong line in `AGENTS.md`
costs the next session more than a wrong line of Kotlin, because the compiler
never checks it.

Add an entry when a mistake changed what got written, committed, or believed.
Newest first.

---

## 2026-09-12 — Put sign-out where nobody could find it

**What happened.** I built the app's first sign-out UI and placed it at the
end of the Profile sheet's `LazyColumn` — after badges, top topics, notebook,
and the full History list. The user's report was "there's no way to sign out
that I can tell".

**How it happened.** I verified it by *reaching* it, not by *finding* it. My
own test swiped to the bottom of Profile eight times to tap it, and I recorded
that as "sign out verified" — the feature worked perfectly, which is exactly
what made the placement problem invisible to me. A scripted test that knows
where a control is can never discover that a human wouldn't.

The placement itself came from treating the profile as a document with
sign-out as a footer. History is unbounded — it grows with every card ever
read — so "the bottom of Profile" is not a location, it's a distance that
increases the longer someone uses the app.

**How it was fixed.** Moved to the top-right of the Profile sheet, visible
without scrolling, with a confirmation dialog added because a now-prominent
control that costs a full Custom Tab round trip to undo should not fire on one
stray tap. Note there was no iOS precedent to copy here — `sparklet-ios` has
no sign-out UI at all ("no UI for one yet" in its `AuthSession.swift`).

**Rule going forward.** Verifying that a control *works* is not verifying that
it is *reachable*. When a test has to scroll to find something, that scrolling
is a finding, not a test step — ask whether a user who didn't already know it
was there would ever get to it. Never place an action after a list with no
upper bound.

---

## 2026-09-12 — Shipped two features that had never once been run

**What happened.** Two features were committed with confident commit messages
and turned out to be broken the first time anyone actually used them.

Depth switching failed for every card without a pre-generated variant. The
commit described the 402 premium-gate handling in detail and said nothing
about having never run it.

The goal-reached slide was appended at the tail of the loaded item list, a
dozen or more swipes from the card that earned it. Its commit message
asserted "the user meets it on the next swipe either way" — presented as a
reasoned justification for diverging from iOS, and simply untrue.

**How it happened.** Both were written during a stretch when the test device
was unplugged, and both were honestly labelled unverified *in the body* of
their commits. That labelling then did no work, because the same messages
also made positive claims about runtime behaviour in the same confident
register as the verified parts. "Unverified" sat next to "the user meets it
on the next swipe" with nothing marking which was which.

The depth bug had a second cause worth naming: the failure was invisible from
outside. `catch (e: Exception)` set a UI error string but logged nothing, so
the only signal was a message on screen that a scripted test never looked for.
An 8.2s LLM generation against OkHttp's 10s default read timeout is exactly
the kind of bug that hides behind a cached happy path — the first card tried
had a cached variant and answered instantly.

**How it was fixed.** Both found in the first on-device pass after the device
came back, and fixed the same day: a per-call timeout for depth, and splicing
the goal slide after the current card. The false claim was corrected in the
commit that fixed it rather than quietly dropped.

**Rule going forward.** A commit message may describe what the code is
*intended* to do, or what was *observed* — never both in the same voice. If a
feature has not been run, the message says so in the first line, not the
fifth paragraph. And when a catch block sets user-visible state, it logs:
a silent catch turns "broken" into "mysteriously does nothing", which is the
most expensive failure shape there is.

---

## 2026-09-11 — Declared the PWA sign-in detour a hard blocker; it isn't

**What happened.** Testing sign-in on a physical device, I found that Chrome
diverts the Custom Tab's `/login` navigation into the installed Sparklet PWA
(WebAPK). I concluded sign-in "cannot complete", wrote it into `README.md` and
`AGENTS.md` as a **known blocker** with a traced intent chain, filed it in
session memory, and asked which of three fixes to pursue. All of that was
built on a false premise. Sign-in completes fine — the user signed in on the
phone minutes later and the app loaded the feed against production.

**How it happened.** I had real evidence and over-read it. I observed:

- the diversion into `H2OTransparentLauncherActivity` (true, still true),
- zero `sparklet-android://` intents in `ActivityTaskManager` (true *at that
  moment*),
- no token in the DataStore (true at that moment),
- a blank dark PWA screen in my screenshot.

What I did not do was interact with that screen or wait more than a couple of
seconds. The PWA was showing a page waiting for input. I treated a ~2-second
window of silence as proof of a dead end, then returned to the app — which
correctly reported "Sign-in was cancelled", and I read that as confirmation
rather than as the expected consequence of my own walking away.

The deeper error: I had a mechanism story ("the redirect chain runs inside the
PWA and never reaches the app") that explained my observations, and I stopped
looking once the story fit. Absence of evidence inside a window I chose became
evidence of absence.

**How it was fixed.** The user completed sign-in manually. `logcat` then
showed the real chain: the WebAPK diversion happens, and **3.7 seconds later**
`sparklet-android://auth` fires into `com.sparklet.android/.MainActivity`, the
token persists to `files/datastore/auth.preferences_pb`, and the feed renders.
`README.md` and `AGENTS.md` were rewritten to describe a *detour with bad UX*
rather than a blocker, and the session memory note was corrected.

**Rule going forward.** Before writing "X does not work" into the docs, either
drive the flow to a genuine terminal state or say explicitly how long was
waited and what was not tried. "I did not complete the flow" and "the flow
cannot complete" are different claims, and only one of them belongs in
`AGENTS.md`.

---

## 2026-09-11 — Tapped an unidentified system dialog and changed device state

**What happened.** On the first sign-in attempt Chrome showed "Continue to
Sparklet? This site wants to open the Sparklet app". I tapped **Continue**.

**How it happened.** I assumed it was Chrome confirming the handoff to *our*
native app via `sparklet-android://`. It was Chrome asking permission to hand
off to the **PWA**, which is also named "Sparklet" — the ambiguity is real,
but I acted on the assumption instead of checking first (`cmd package
resolve-activity` and the WebAPK package list, both of which I ran only
afterwards, would have told me).

**How it was fixed.** Not fully reversible — accepting made the diversion
sticky, so subsequent runs skipped the prompt, and I can no longer cleanly
observe the first-run behaviour on this device. It is disclosed as a caveat
wherever the diversion is described, rather than quietly omitted.

**Rule going forward.** On someone else's device, identify a dialog before
tapping it. A tap that grants a persistent permission is not a read-only
probe, and it can destroy the evidence you are trying to collect.

---

## 2026-09-11 — Corrupted every screenshot on a foldable

**What happened.** `adb exec-out screencap -p > out.png` produced files that
were not valid PNGs.

**How it happened.** The Flip 7 has two displays, so `screencap` writes
`[Warning] Multiple displays were found...` to **stdout**, inline with the PNG
bytes.

**How it was fixed.** Capture on-device to a file with an explicit display id,
then pull it:

```bash
adb shell screencap -d 4633128672291735937 -p /sdcard/x.png
adb pull /sdcard/x.png
```

That id is the inner 1080x2520 screen.

---

## 2026-09-11 — Left `AGENTS.md` self-contradictory after a targeted edit

**What happened.** I added a status note saying quiz/guess/misconception/
review answering was built, while the paragraph below it still read "Not yet
built: quiz/guess/misconception/review answering". I also left an 87-character
line in a file wrapped to ~72.

**How it happened.** I edited by exact-string replacement — precise, but it
only looks at the string being replaced, not at what the surrounding prose now
claims.

**How it was fixed.** Re-read the whole section afterwards and reconciled it,
marking the superseded paragraph rather than deleting the history.

**Rule going forward.** After a targeted replacement in a long doc, re-read
the entire enclosing section before moving on.
