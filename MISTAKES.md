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
