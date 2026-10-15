---
created: 2026-08-27
updated: 2026-10-10
---

# Daily App — Decisions Log

> **Public copy.** Generated from the project's private design documents with personal context removed (see [README](README.md)). IDs such as `[R8.2]`, `D-028` and `§6.2` resolve within these six documents; references to the scope document and the content backlog point at documents that are not published.

Short entries, newest at the bottom. What was being chosen between, what was picked, what it costs. Promote to a full ADR (`adr/ADR-NNN-title.md`) only if something turns out genuinely contested and expensive. Scope lives in the scope document.

---

## D-001 — Platform: Android

**Not a decision.** It's the only phone the author owns. Recorded so the constraint is visible to anyone reading later.

**Consequence:** iOS is out of scope permanently, which removes the PWA option entirely (no shipped browser schedules local notifications, so a PWA would have made the backend a hard dependency for the one event that must work offline).

---

## D-002 — Client framework: native Kotlin

**Decided 2026-08-27.** Considered: React Native + Expo vs. native Kotlin. PWA eliminated by D-001.

**Why Kotlin:**

- **Longevity.** This is a tool meant to still run in 2029. A native Android app can sit untouched for two years and still build. An Expo project left that long is an upgrade project before it's a feature.
- **Room.** Compile-time-verified queries and first-class migrations, landing exactly on the corpus/reflection schema — the decision this project is least able to afford getting wrong.
- **Control.** Direct `AlarmManager` and `WorkManager` rather than an abstraction over them, on an app whose core risk is notification delivery.
- **Learning depth.** Control and learning depth were preferred over speed.

**What it cost:**

- **Speed.** Expo would have put something on the phone in days; this is a couple of weeks to first working version. Acceptable only because the deadline was withdrawn (see the scope document § Sequence, not schedule) — under the original one-month ceiling this decision would have gone the other way.
- **A new toolchain.** Kotlin, Android Studio, Gradle. Mitigated by Compose being declarative, so the mental model carries over from React.
- **The abandonment risk got slightly worse.** Early momentum is real, and this trades some of it away. Countermeasure: gate 1 (a notification that fires on your phone after a reboot) is deliberately small, so there's something working within days even on the slower path.

**Correction on the record:** an earlier draft of the comparison implied `expo-notifications` might not survive a reboot. That was wrong — it requests `RECEIVE_BOOT_COMPLETED` and re-registers scheduled notifications on restart, and supports `SCHEDULE_EXACT_ALARM`. Expo was not eliminated on notification grounds; it lost on longevity and Room.

---

## D-003 — No journaling in the app; nothing user-generated leaves the phone

**Decided 2026-08-27.** Trigger: no journaling in an app that is connected to the internet.

**The rule, stated one step further than asked:** *nothing you generate on the phone leaves the phone; only content authored on the PC comes down.* Extended from reflections to reminder text as well — "call back at 4" is the same category as a journal line, and it would have been syncing under the old design. Applying the rule to only the obviously-sensitive half would have been inconsistent.

**What it removes from the design:**

- In-app journaling, free-text reflection, prompts, long-form — all out. Reflection lives in the author's existing notes.
- Reflection sync, user accounts, Cognito, encryption-at-rest concerns, and the entire compliance-shaped part of the design
- The deferred "personalized significance" layer, which is now **closed rather than deferred** — it required exactly the data no longer being kept

**What the backend is reduced to:** corpus and theme distribution, PC → phone, one-way. Nothing else. Plausibly S3 + CloudFront serving a signed static JSON — no Lambda, no DynamoDB, no auth, because there's no user data to gate and one reader to serve.

**Cost accepted:** no cloud backup — a lost phone loses reminders and on-device history.

**Mitigation:** Syncthing is already running phone↔PC. The app writes a plain export file into a synced folder; the desktop picks it up. Backup without a server, and it doubles as the plain-format export required by the non-negotiables.

**Second-order consequence worth watching:** a simpler backend is a less interesting engineering artifact. If that matters, the complexity should go into the **publish pipeline** — validation, schema checks, versioning, rollback, CI — not into runtime services the app doesn't need. A content pipeline that can't ship a malformed entry is the better engineering story anyway.

**Resolved immediately after:** see D-004.

---

## D-004 — No response marker; history becomes a library

**Decided 2026-08-27.** A lightweight on-device star or "this landed" toggle was proposed as the one non-journaling record of a response. Rejected — no purpose for it.

**Consequence, which was not obvious when the question was asked:** it takes the history view with it. A record of which quote was shown on which day is derivable from the rotation policy and a date, so it isn't a feature — it's a log.

**What replaces it is better.** The screen becomes a **library rather than a log**: browse past themes, re-read the entries and the significance you wrote. It needs nothing from you, it works off the corpus alone, and your own writing is the part actually worth returning to.

**Net effect on the local store:** corpus cache, reminders, and rotation bookkeeping (which entries have been shown, so the policy doesn't repeat itself — mechanical state, not a record of you). That is the entire data model. The app now records nothing about its user.

---

## D-005 — Stateless daily selection; the schema is disposable

**Decided 2026-08-27.** Full data model in [SCHEMA](SCHEMA.md).

**Today's quote is a pure function of the date**, not a stored pick. Seed a shuffle with `hash(themeId + period)`, order the theme's entries by it, index by days-elapsed. Stable within a day, no repeats until the theme is exhausted, reshuffles when a theme is re-run in a later period, reproducible on the desktop, and — consistent with D-004 — stores nothing at all, not even which days the app was opened. The obvious `daily_pick` table was considered and rejected as unnecessary state.

*Caveat:* adding an entry mid-period reshuffles the remainder and can cause one repeat or skip. Add between periods. *Cost:* no "skip this one" without adding an overrides table, which is additive later, not a migration.

**Revision to an earlier claim.** I called the schema the decision worth real care because migrating a journal is what ends projects. That stopped being true when D-003 and D-004 removed the journal. Four of the five tables are a cache of a corpus that lives in the vault and in git — drop and re-pull, no migration. **The only table that can't be thrown away is `reminders`**, a handful of hand-typed rows. Write that one migration carefully and let the rest be disposable.

**Five tables:** `themes`, `entries`, `schedule`, `reminders`, `meta` (KV). Reminders store local wall-clock time, never UTC instants. Alarm request codes derive from `reminders.id` so reboot rescheduling is idempotent.

**Authoring:** one markdown file per theme in the notes vault, entries as `##` sections — H2 is the id, first blockquote is the quote, `**Author:**` / `**Source:**` lines are metadata, the rest is significance. Scheduling lives in a separate file mapping period → theme, deliberately, so a theme stays a bag of entries rather than a calendar slot and can be re-run when the corpus is thin.

**Publish:** one JSON object, ~400 KB at a full year. No pagination, no delta sync. CI validation fails the build on a missing source, a duplicate id, or duplicate quote text — which is how the `source` requirement stops being discipline and becomes mechanical.

---

## D-006 — Themes are an authoring unit, not a schedule

**Decided 2026-08-27.** Reverses the monthly-theme recommendation.

**The objection, which was correct:** a month on one topic gets tiring, however good the topic.

**The error being corrected:** Monthly themes were recommended on authoring grounds — batching thirty entries on one topic is far cheaper to write, and it's what took the corpus estimate from 10–40 min/entry to 5–15. That argument is about the author at a desk on a Saturday. It was then allowed to silently decide what appears at 8am every morning, which is a different question with a different answer. Thirty consecutive mornings inside one idea reads as repetition however good the idea is.

**Decision:** author in themes, deliver from the whole corpus. Shuffle everything, no repeats until exhausted, index by day. Themes remain the batch unit and the library's organising principle; they don't drive a calendar.

**Why this is the right call and not a compromise — it's self-correcting.** At one theme you get that theme daily because there's nothing else, matching what the scheduled design would have done anyway. At three, mornings vary. At eight it's genuinely unpredictable. Monotony is a small-corpus problem and the system grows out of it with no policy change.

**Given up:** the deliberate arc — no "this month I sit with mortality." Pinning a theme for a stretch is an additive override later, not a redesign.

**Reversal condition:** if after two or three months the mornings feel arbitrary rather than varied — no thread, no sense of being anywhere — the arc was doing real work, and pinning is the fix.

**Knock-on:** the schedule file and its Room table are gone (four tables, not five); the "re-run a theme when thin" fallback is unnecessary; the monthly-vs-weekly question is closed since there are no periods. Selection in D-005 now seeds on the cycle number and shuffles the whole corpus.

**Note on how this was decided.** The mechanism was delegated, which was fair. But the input that actually decided it was whether a month on one topic wears on the reader, which only the reader can know, and that had already been answered. The deference was about the fix, not the experience.

---

## D-007 — Selection uses one fixed permutation, not a per-cycle reshuffle

**Decided 2026-08-27, correcting D-005.** Found by writing example data and previewing a month against it — the first time the algorithm met real input.

**The bug.** D-005 seeded the shuffle on the cycle number so each pass through the corpus arrived in a fresh order. Independent per-cycle permutations collide at the seam: with 12 entries the preview repeated `boethius-fortune-turns` after **3 days** and three more entries within 4–6. Not an implementation slip — the design guaranteed it.

**The underlying result, which is worth stating because it is not obvious:**

> You cannot have both a fresh order every cycle and a guaranteed n-day gap between repeats. The only permutation sequence with no repeat inside any n-day window is one whose order never changes. The two requirements are strictly incompatible.

**So one had to be dropped, and it was the reshuffle.** Seeing the same quote three days later reads as the app malfunctioning. Seeing the corpus in a consistent order across a 30-day pass is barely perceptible — and by the time it would be, entries have been added, which changes the order anyway.

**Freshness now comes from the corpus growing, not from reshuffling.** That is the more honest source of it, and it rewards the behaviour the project actually needs.

```
order       = corpus.sortedBy { hash(id) }   // one permutation, no cycle in the seed
todaysEntry = order[daysSince(EPOCH) mod n]
```

**Net effect:** the algorithm got shorter, the guarantee got stronger and easier to state — *you will never see the same entry twice within n days* — and the cycle concept disappeared from the design entirely.

**Verified:** minimum gap between repeats is exactly n across five full passes, and every window of n consecutive days is a complete permutation of the corpus.

---

## D-008 — A static web reader as the share surface — **DEFERRED, not in v1**

**Raised 2026-08-27.** Could the app be a web app? Not out of frustration with Android (mobile was the preference, partly to learn it) but because **sharing is easier on the web**: a link is easier to send than an APK.

**A real gap in the scope.** "Personal tool, one user" ruled out sharing implicitly, and that turns out to be wrong. Not multi-user — just being able to *show someone something written here*.

**Why the app itself still isn't a web app.** Verified: Chrome **abandoned** the Notification Triggers API — their docs say development ended because consistent cross-platform behaviour wasn't achievable. No browser can schedule a local notification. So a web app's only route to an 8am notification is a server push, which means **reminders would have to be scheduled server-side, putting reminder text on a server** — a direct violation of D-003. The platform decision holds, and it holds on *the project's own rule* rather than on engineering preference.

**Status corrected same day:** this is **convenience, not a necessity** — the plan is still a single user. So it is **deferred, not scoped**. Recorded because the reasoning is worth keeping, not because it is being built.

**Deferring costs nothing.** The reader would read `corpus.json`, which the publish pipeline produces regardless. It is purely additive later — no hooks to leave in the design, no forward-compatibility work, nothing foreclosed. Unlike the ambient layer (D-009 / [R9.3]), there is no boundary to establish now.

**If it is ever built:** add a **static web reader** over the same `corpus.json`, with per-entry deep links (`/e/<entry-id>`). One more file in the bucket the publish pipeline already writes to. Not a second product — a read-only view of content that already exists.

**Why this is clean:** the corpus is the half that is shareable by nature — it is the author's writing. The half that must never travel (reminders, anything generated on the phone) is not in it. **The share surface falls exactly along the privacy line already drawn rather than cutting across it.**

**Free consequences:** a reviewer can see the work without installing an APK (relevant to the then-open question of whether the repo is meant to be read); a desktop reading surface, previously out of scope; and theme preview in a browser before content reaches the phone.

**Correct division of labour:** native for what must fire at 8am on a locked, dozing phone; web for what must open from a link on someone else's device. Each doing what the other cannot.

**Deferred with it:** whether the whole corpus is link-public or only entries explicitly marked shareable. The quotes are public domain; the significance is the author's own writing. This only needs deciding the day a link is actually shared — until then it blocks nothing.

---

## D-009 — Weather mechanism settled in principle; still deferred

**Raised 2026-09-11.** Weather after all, with a proposal: the PC fetches it, Syncthing carries the file to the phone, the app reads it.

**Assessment: right instinct, one real flaw, wrong pipe.**

**The instinct is right** — keeping the phone out of weather-fetching fits the one-way PC to phone flow and puts no API key on the device.

**The flaw is staleness.** The PC is not always on. A synced file is only as fresh as the last time the PC was on, Syncthing ran, and the phone was reachable — so a morning after an evening shutdown shows last night's sky. **A wrong ambient cue is worse than none, because it breaks the exact illusion the feature exists to create.**

**The fix: sync forecasts, not observations.** Fetch a 24-hour forecast; the phone reads "what is expected at 08:00". A nine-hour-old *forecast* for 08:00 is legitimately accurate because forecasts are predictive; a nine-hour-old *observation* is simply wrong. This removes almost all of the staleness problem.

**But Syncthing is the wrong channel.** It would stand up a second PC to phone mechanism for a nice-to-have when one already exists. Put the forecast in the **corpus publish payload** — the PC already produces and pushes that file, and the phone already makes exactly one GET on launch. Weather then costs zero new mechanism, zero new permissions, zero new network exposure.

**Correction to an earlier claim in the scope document and [SRS](SRS.md).** Those said weather would be the first thing requiring a network call *and* a location permission. **The permission half was overstated:** `INTERNET` is already required for corpus sync ([R7.1]), and for a single-user app the coordinates can be hardcoded rather than asked of the OS. The direct-fetch option is therefore cheaper than represented — and always fresh. The PC route's remaining real advantage is that the phone never contacts a weather service at all.

**Decision: mechanism settled, feature still DEFERRED.** [R9.1]/[R9.2] do not move into v1. Step 3 — the notification gate — has not been passed, and weather design is decoration on a building that may not stand. The degradation rule from [R9.2] still governs: a stale or missing forecast degrades silently to the season/time-of-day visual.

---

## D-010 — The backend has no remaining job; journaling reopens as a consequence

**Raised 2026-09-11. NOT YET DECIDED — deliberately parked until after Step 3.**

**How the backend got here.** It started with three jobs: back up reflections, sync them across devices, and serve as cloud-engineering practice. D-003 cut journaling, which removed the first two plus accounts, auth and encryption-at-rest, leaving one job — hold the corpus so new entries reach the phone without an app reinstall. Then the obvious was pointed out: **Syncthing already moves files from the PC to the phone, and has for some time**. That last job is redundant.

**The earlier analysis was anchored wrong.** The earlier argument — "don't add Syncthing as a second PC-to-phone mechanism" (D-009) — had it backwards. Syncthing is the mechanism that already exists and is already trusted; the CDN would be the second one. The design was being defended instead of re-examined against the user's actual setup.

**If Syncthing carries the corpus:** no S3, no CloudFront, no `INTERNET` permission for corpus sync, nothing to maintain for years. The app reads the corpus from a synced folder the same way it reads the export folder.

**Three corrections the user made, all accepted:**

1. **Weather accuracy on the go** — a fair point that had been under-weighted, but it argues for the *phone having network access*, not for an AWS backend. A direct call to Open-Meteo needs no server of one's own. The two questions are separable.
2. **Cost aggregation** — free tier is per account, not per project, so other projects share the allowance. Principle accepted; magnitude here is near zero (one small file, a few GETs a day).
3. **Journaling can come back** — *the most consequential point in the exchange.*

**Why journaling reopens.** D-003's trigger was internet exposure: no journaling in an internet-connected app. Remove the internet and the reason for the cut goes with it. The closed loop returns: quote arrives, the reader responds, the response is part of the record. Syncthing also solves the backup problem that made local-only user data uncomfortable.

**Two constraints if journaling returns:**

- **Write into the vault, not a separate app database.** Journaling already happens in Obsidian. One file per day, and **the app may only create files, never edit existing ones** — that is what prevents Syncthing conflict copies when the PC side is also written to.
- **It reopens D-004.** Removing journaling is what turned history into a browsable library and killed the response marker. If responses return, that screen has a reason to exist again.

**Also at stake:** the AWS work was partly for cloud practice, and a quote app backed by S3 serving one static file is thin practice. Recommended split: **build the app on Syncthing, and build AWS separately as its own project if the practice is wanted**, so a daily-use app is not hostage to a side goal.

**Parked until after Step 3.** This changes the data model — reminders would stop being the only non-disposable table. If notifications do not hold on the author's Samsung, none of it matters, and that is still unproven.

---

## D-011 — Alarm delivery measured on the real device; the escalation ladder was wrong

**Measured 2026-09-11/12, Step 3 notification spike, Samsung on Android 16.**

**Result: the gate passes.** A 2-minute `setAndAllowWhileIdle` alarm fired at 4–5 minutes with the phone locked and untouched. Android documents that `setAndAllowWhileIdle` cannot fire more than **once per nine minutes per app** in Doze, so a 2–3 minute deferral is inside spec — not a Samsung power-management problem and not a defect.

**A correction to advice given earlier in this project.** The AI assistant's earlier advice was that if timing proved too loose, the escalation was `setExactAndAllowWhileIdle` with the `SCHEDULE_EXACT_ALARM` permission. **That was wrong.** The Android documentation applies the same nine-minute throttle to *both* `setAndAllowWhileIdle` and `setExactAndAllowWhileIdle`. Adding the permission would have bought nothing.

**The actual ladder has three rungs:**

| Call | Timing in Doze | Cost |
|---|---|---|
| `setAndAllowWhileIdle` | up to ~9 min late | none — current implementation |
| `setExactAndAllowWhileIdle` | **also up to ~9 min late** | a permission, for no gain |
| `setAlarmClock` | fires on time; the system leaves Doze shortly before | permission, and it appears in the system alarm UI |

**Only `setAlarmClock` escapes the throttle.** It is the only call that delivers minute-accuracy on a dozing device.

**Design consequences:**

- **Daily quote keeps `setAndAllowWhileIdle`.** [R3.5] already committed to "around 08:00" rather than 08:00:00, and five minutes is immaterial to a reading ritual. Measurement confirms the choice rather than forcing it.
- **Reminders are now an open question for Step 8.** [R4.x] never specified timing precision. "Call back at 4" arriving at 4:06 may or may not be acceptable; if it is not, `setAlarmClock` is the only fix, and sideloading means the Play policy restriction on that permission does not apply.

**Method note worth keeping:** the number came from printing the actual fire time in the notification body and comparing it against the tap time, with the phone locked and face down. "It fired eventually" and "it fired 3 minutes late" are different findings, and only the second one is decidable.

---

## D-012 — Reinstalling the app silently resets its battery exemption

**Found 2026-09-12, and it invalidates any soak test that spans a reinstall.**

**What happened.** The daily alarm, set for 17:29, arrived at 17:40 — and only when the phone was unlocked. That looked like a regression against D-011, where the gate had passed at 4–5 minutes.

**Cause, identified by the user:** uninstalling the app during Step 5 debugging wiped the Samsung battery-optimisation exemption set after the first test. Reinstalling does not restore it, and there is no warning.

The four measurements are consistent once that is accounted for:

| Test | Result | Exemption |
|---|---|---|
| 1st | fired on unlock | not yet set |
| 2nd | 4–5 min | set |
| after reboot | ~5 min | still set |
| 2026-09-12 | 11 min, on unlock | **wiped by reinstall** |

**The AI assistant over-corrected.** On the 11-minute reading, it declared the D-011 gate "called too early" and proposed escalating to `setAlarmClock`. That was a reversal on a single data point, in the opposite direction from the original error. **The original gate reading stands.**

**Consequence for [NFR1] — this belongs in the acceptance procedure, not in someone's memory.** The Step 9 four-day soak test is the acceptance gate for the entire project, and **it is invalid if the app is reinstalled at any point during it**, because the reinstall resets the exact setting the test depends on. The soak must run on one uninterrupted install, and the exemption must be confirmed at the start.

**Decision rule for escalating to `setAlarmClock`:** not now. If the alarm ever defers again *without* a reinstall to explain it, that means Samsung is re-restricting an app in daily use, which no configuration fixes — and `setAlarmClock` (the only call that leaves Doze to fire on time, per D-011) becomes necessary despite its cost: a permanent alarm icon in the status bar and an entry in the system's "next alarm" surface.

---

## D-013 — Delivery is the requirement; punctuality is not

**Decided 2026-09-12.** Timing accuracy matters little; receiving the notification is what matters.

**Closes the open item from D-011.** Reminder timing precision was listed as a Step 7 decision; it is now settled. Inexact `setAndAllowWhileIdle` is sufficient for both the daily quote and user-set reminders. **`setAlarmClock` is off the table** unless delivery itself fails — so no permanent status-bar alarm icon, and no `SCHEDULE_EXACT_ALARM` permission.

**Restates the [NFR1] acceptance criterion, which was framed wrongly.** It read as a punctuality test — "fires within the documented window." It should be a *delivery* test:

> **The failure being watched for is a day with no notification at all, not a late one.**

These need different evidence. Lateness is a single measurement; absence only shows up across several days. The Step 9 soak is therefore a presence check over four consecutive days, on one uninterrupted install (D-012), not a stopwatch.

**One edge worth naming.** In the degraded state observed on 2026-09-12 — battery exemption wiped — the quote still arrived, just on unlock. Under a delivery-only bar that counts as working. So the exemption matters for the *experience* (a morning quote should precede picking up the phone) without being what the acceptance gate measures.

---

## D-014 — Reminders deferred out of v1, pending real scoping

**Decided 2026-09-12.** Reminders were never adequately scoped; they began as a passing idea. Between deferring and cutting outright, the choice was **not yet**: deferred, not deleted.

**Reminders were load-bearing in three non-obvious ways, all of which now relax:**

1. **Room loses its only justification.** It was deferred to Step 7 precisely because reminders were the first data that could not be regenerated. Without them the entire local store is a cache of a file. [NFR8] — "schema risk is concentrated in one table" — had `reminders` as that table; there is now no schema risk at all, and no reason to add a database in v1.
2. **The privacy rule's reach shrinks.** D-003 was extended from journaling to reminder text on the grounds that "call back at 4" is the same category as a journal line. Sound reasoning, but with no reminder text there is nothing user-generated on the phone at all. The rule is satisfied trivially rather than constraining anything.
3. **v1 is nearly complete.** Quote, significance, library. Step 8 and the soak test and the product is done.

**The pattern across this project's cuts, worth recording because it has repeated:** the project opened with "daily reminders and reflections." Since then journaling was cut (D-003), the response marker was cut (D-004), history was replaced by a library (D-004), and now reminders are deferred. **Every cut has moved toward the thing that was actually in mind first — a quote and what it means.** The opening framing was the disposable part; the seed idea was the real one. Treat later additions with that history in mind.

**A recommendation not taken:** cut rather than park, because a parked feature still shapes the design around it. The user preferred to keep the option. Recorded so the reasoning is available if it comes up again.

**Status of [R4.x] in [SRS](SRS.md): DEFERRED.** Not deleted, not scoped. If revived, they need real scope definition first — the existing requirements were written from a throwaway remark.

---

## D-015 — The content model is rebuilt around depth; journaling returns

**Decided 2026-09-12.** The largest change since D-003. [SCHEMA](SCHEMA.md) rewritten to v2; v1's quote+significance entry is now a *component*, not a day.

**Where it came from.** Asked what "edifying information to ponder while out and about" actually meant, the answer was an earlier long-form document, *Things Worth Sitting With*: fourteen days, each pairing two ideas in collision: Surface Entry → Idea One (with named thinkers and a pull quote) → Idea Two → Collision → Implication → Reframe.

**The AI assistant's error, caught in the same conversation.** On first finding the ~129 archived wiki notes, it claimed the corpus was "largely written — 75 entries already in the format." Wrong. The wiki notes are *derivatives* of the ponder format, not instances of it (`you-are-a-steward-not-an-owner.md` cites the two-weeks document as its source). They are raw material for generating more, not ready-made days.

**The user's two constraints, both correct:**

1. **Don't limit it to collisions** — that format would go stale. This is the same instinct that produced D-006, where monthly themes were caught going monotonous. Twice now the fix has been variety in *shape*, not in depth.
2. **A quote alone is not a day.** The example: "Where the state ceaseth, there only commenceth the man who is not superfluous" carries a whole day — but only unpacked.

**Resulting model — four shapes, one floor.** The types are not different depths; they are different ways of going deep on one thing: **collision** (by tension), **expound** (by unpacking), **principle** (by development), **passage** (by close reading). The depth floor is an explicit requirement: *a day must develop its material, not merely present it*, validated crudely by a word-count minimum.

**A proposal made minutes earlier was retracted** — variable-depth types, with a short "question" type serving short moments. A thin day is precisely what the user ruled out. Variable time is served by the *library* instead; the daily item is always substantial. Questions and bare quotes became components inside entries rather than types.

**Journaling returns, as D-010 predicted.** Notes attach to **the day they were written**, not to the entry. That is what makes the repeat rotation worth having: an entry returns in ~n days, you read it with your September note beneath it, and what you write in December differs. The corpus is fixed; the user is not. **D-004 survives intact** — the app still records nothing passively, but because notes carry dates, encounter history is derivable from what the reader deliberately chose to write. The record without the surveillance.

**Room returns with notes** — the only non-disposable table, which is the justification that left when D-014 deferred reminders.

**Tags replace themes.** A theme forced one entry into one grouping; tags let it sit in several, which a graph-shaped library wants, and ~129 archived notes already carry them. D-006 is undisturbed: tags are for authoring and browsing, never scheduling.

**Unchanged:** D-005/D-007 selection, D-003's privacy rule (nothing leaves the phone), D-012's soak-test constraint, D-013's delivery-over-punctuality bar.

**Still open:** the library's traversal model (tags vs `related` edges vs search vs reading-time filter); whether notes get their own "everything I've written" view; and the production mix — collisions are expensive, passages cheap.

---

## D-016 — The library is a timeline plus tags; the vault keeps the graph

**Decided 2026-09-12.** Settles all three items left open by D-015.

**The library: a scrollable timeline, newest first, one row per day — the entry that appeared, plus any note written under it — and filtering by tag. Nothing else.**

**Search and `related`-edge traversal are explicitly not built.** The user's reasoning is a product boundary, not a shortcut:

> Search and related edges belong in the notes tool where the entries are written (Obsidian), not rebuilt in the app.

**The vault is the graph and the search; the app is the daily surface and the place notes get written.** Entries live in the vault as markdown with `wikilinks`, so Obsidian already gives traversal for free; duplicating it would mean shipping a worse version of a tool already open on the desktop. `related` stays in the schema — it serves the vault — but the app does not render it as navigation.

**Keep this line.** Most future scope requests will be some form of "what if it also…", and this answers them.

**The timeline costs no state.** Selection is a pure function of the date (D-005/D-007), so running it backwards reconstructs every past day. The statelessness chosen for picking tomorrow yields the whole history as a side effect — an unplanned payoff of that design.

**Caveat, inherited from D-007:** the permutation derives from corpus size, so publishing new entries changes what past dates compute to; a row for 3 September may show a different entry than was actually seen. **Notes pin the days that matter** — a note carries its date *and* its `entryId`, so any day the reader responded to is anchored, and the timeline renders that pairing where a note exists. Days scrolled past and forgotten drift. Accurate where the reader engaged, approximate where they didn't, still nothing tracked.

**Notes appear under their entry only.** No separate journal view. A repeat is met with what the reader thought last time sitting beneath it.

**Production: AI drafts, the author directs and edits.** Unchanged from the original assumption, and it is what makes the expensive **collision** shape affordable — the ~129 archived wiki notes are raw material a draft can be built from. The mix of shapes per month stays unquantified until measured against real effort.

---

## D-017 — Subject matter: philosophy, sociology, spirituality

**Decided 2026-09-12.** Stick to thought-provoking subjects: philosophy, sociology, and perhaps spirituality.

**This matters more than it sounds**, because the archive it draws from does not obey it. The archive is heavy with business, strategy and career material — flywheels, Goodhart-as-metrics, pioneers-rarely-win, cloud architecture. Without this constraint the corpus drifts steadily toward productivity content, which is where most of the available raw material actually lives. The constraint exists to resist the supply.

**Applied to the fourteen existing collision days: twelve kept, two cut.** **Expected Value & The Ruin Problem** (decision theory) and **Antifragility & The Paradox of Safety** (Taleb) are closer to strategy than to thought. **Goodhart's Law & The Examined Life** survives because pairing it with Socrates makes it about self-measurement rather than metrics — the pairing, not the topic, is what qualifies it.

**Tier 4 of the entry outline is retired.** The passage sources proposed — the Collins flywheel PDF chief among them — are the wrong material.

**Spirituality is new and previously undrawn.** The archive shows prior work in this area (essays on scripture). Identified seams: Pascal's *divertissement* (which lands directly on the "out and about" use case), Ecclesiastes and *hevel*, Simone Weil on attention, the apophatic tradition against Wittgenstein's last line.

**Sociology's best entries pair with existing material.** Weber's iron cage against Nietzsche's cold monster is a collision half-written by [where-the-state-ceaseth](SCHEMA.md); Simmel's blasé attitude (1903) describes a phone; Goffman is the sociological form of the imagined-audience theme.

**Noted gap: the corpus is entirely Western** — Greek, German, French. Zhuangzi and the Tao Te Ching would break that up.

**Production consequence.** The collision material needed only conversion because it was already written. Spirituality and sociology need *sourcing* before drafting, which is a slower and different kind of work.

---

## D-018 — Shape balance is the corpus's real problem, not size

**Decided 2026-09-13.** Recorded on publishing the corpus to 19 entries (version `a04a79b2b0e1`) and writing the content backlog.

**The measurement.** With six new entries committed — four Stoic `expound` days (Seneca, Marcus, Epictetus, Boethius), Pascal's *divertissement*, and the Weber/Nietzsche collision D-017 predicted — the shape distribution is **13 collision, 6 expound, 0 principle, 0 passage**.

**Two of the four shapes introduced by D-015 have never been used, and the format the user specifically warned would go stale is 68% of the corpus.** D-015 was written to prevent exactly this; it has not yet been acted on. Corpus *size* is the visible problem (19 entries means a repeat every 19 days), but size is not what breaks the premise. Shape monoculture is.

**The AI assistant's error, corrected the same day.** The first version of the content backlog ranked the four shapes by production cost — `collision` expensive, `passage` cheapest — and sequenced the whole backlog cheapest-first. The correction: the collisions had been generated in one sitting in a few minutes, and the plan was overthinking it. Correct. Drafting is fast for every shape, there is no cost gradient, and an elaborate sequencing argument built on a false one is worse than no plan. **The real bottleneck is review time, not drafting time.** PIPELINE now orders batches by *coverage* — what the corpus does not yet have — and the only batch that genuinely warrants slowing down is the non-Western one, where the cost is verification rather than composition.

**The assistant then over-corrected**, reading "overthinking" as "produce instead of plan" and starting an unrequested eight-entry drafting run. Same failure as D-012, opposite direction: a correction absorbed as a reversal instead of as information. Noted because it has now happened twice.

**Batch A published the same day — eight `principle` entries**, taking the corpus to 27 (`c2a98e94a53c`). `principle` is no longer unused; `passage` still is.

**A real constraint found while drafting: most of the wiki is already in the corpus.** Notes whose frontmatter cites the original long-form document as `source:` are *derivatives of the collisions*, not independent material — `luck-is-an-obligation-not-an-entitlement` is `free-will-and-moral-luck`, `you-are-a-steward-not-an-owner` is `civilizational-debt-and-the-ship-of-theseus`, and so on for seven notes. Drafting them as `principle` entries would have restated published days in a different shape. **Rule for any future wiki mining: check the `source:` field first.** Notes sourced `synthesis` or an external URL are the safe pool. This also corrects D-015's framing — the wiki is raw material for *fewer* entries than it appears, because much of it is downstream of the corpus rather than upstream.

**The `related` warnings are not a backlog.** An earlier draft of this decision claimed publishing `principle` entries would close five dangling links `publish.py` warns about. Wrong: D-016 settled that the vault is the graph and the app is not, so vault-only links are the intended state. The warnings should be left alone.

**the content backlog supersedes the themes note.** `THEMES.md` held 91 theme *candidates* under the v1 model D-015 replaced; tags replaced themes in D-016.

**Attribution traps are now a named list** (PIPELINE §8) rather than re-litigated per entry. Two published entries flag their own compression problems in-text (the Seneca and the Pascal); that discipline scales badly without a list. **The one that matters most: "We are what we repeatedly do. Excellence, then, is not an act, but a habit" is Will Durant, not Aristotle** — and it will attract both `consistency-beats-intensity` and `practice-primacy` when those get drafted.

**D-017's non-Western gap is scheduled last, deliberately.** It carries the highest sourcing risk, and the failure mode is writing a Western entry about an Eastern idea — *wu wei* as a productivity technique, *anattā* as a thought experiment. Two public-domain passages (Zhuangzi's butterfly, *Tao Te Ching* 11) enter early via Batch B; the four collisions wait until there is time to read properly.

---

## D-019 — The brass direction, and the forecast rides the sync

**Decided 2026-09-12.** Full treatment in [DESIGN](DESIGN.md); this records what was settled and why it matters beyond aesthetics.

**The app gets a deliberate look: rustic, warm, mellow — brass, worn surfaces, lamplight.** The user asked whether theming was a sidetrack from more urgent work. It is not: this is a reading app whose purpose is material you sit with, and the surface decides whether that happens. What exists now is not a design but an Android Studio default — Material purple nobody chose, an accent derived from the wallpaper, and a status-bar bug the user had to report. **`dynamicColor` is removed** (`Theme.kt` line 40); a wallpaper-derived accent and a deliberate palette cannot coexist. Body text moves to a humanist serif — Roboto is cold and undercuts the rest, and the type is the thing actually looked at for four minutes.

**A half-revealed mechanical dial, persistent at the bottom of every screen, showing position rather than time.** Two hands: a fast one for the current section, a slow one advancing a step per day so that a full revolution is one cycle of the corpus. The slow hand makes D-007's rotation something felt rather than documented — *this one is coming back around soon.* Gear ratios mesh correctly, so the clock is inaccurate but the mechanism is not.

**The case against drag-to-navigate was half right, and the revision beat both positions.** The argument was that the original idea — dragging the hands to move between sections — fails on affordance, precision and speed. The revision kept the part that mattered and inverted the rest: *swipe normally, and the gears follow.* The gesture becomes ordinary; the mechanism becomes feedback rather than a control to aim. Both problems solved, nothing interesting lost. Dragging up from the dial pulls the homepage over the current screen — starting the gesture on the dial is what keeps it from fighting the scroll of an 800-word entry.

**The homepage exists but is barred from the morning path.** Notifications deep-link straight to the entry; the homepage is where atmosphere lives and is reached deliberately.

**"Weather" is a forecast, and that distinction resolves a real conflict.** Live weather means a network call and a location permission — reintroducing precisely the condition D-003 was written against, since notes are only allowed on the phone because nothing needed the internet. A forecast is predetermined, so the desktop writes `forecast.json` into the synced folder beside `corpus.json` and the phone reads it like everything else. No network, no location, D-003 intact, notes still justified. Settings carries a manual override, because forecasts are wrong.

**Scope reopened deliberately, with a test.** The goal is not reading; reading is just the most effective way to reach the goal. D-016 set a scope boundary against "what if it also…", and this reopens it on purpose — but narrowly. **The test for any addition is whether it serves pondering in a situation where reading fails.** Most candidates are reading wearing a hat. **Audio passes**: `TextToSpeech` is built in, offline, no dependency, and covers walking, driving and the gym — the cases where "out and about" and "read 800 words" are incompatible. Ranked after notes, ahead of the rain.

**Build order unchanged.** The four-day soak test is wall-clock time, not working time, and should be running while any of this is built.

---

## D-020 — Notes are a JSON file, not Room

**Decided 2026-09-14.** Reverses the storage half of D-015 and the design in [SDS](SDS.md) §4 / [ARCHITECTURE](ARCHITECTURE.md) §2, which both specified Room.

**What Room buys, and why none of it applies here.** Room is Google's ORM over the SQLite that ships in Android. It gives compile-time-checked SQL, `Flow` observability that pushes on every write, versioned migrations with a `MigrationTestHelper`, and query pushdown into an indexed database. All four are for a scale this app will not reach. **The design ceiling is 999 notes** — a realistic lifetime maximum for one person. An earlier version of this decision cited "~365 rows a year", which was wrong twice over — a note every day is an *ideal*, not a floor or a cap, and the figure that matters is the lifetime total, not an annual rate. At 999 notes of a few hundred words the whole file is under a megabyte, read into memory in a single pass, with one writer, one process, no joins and no partial loads. Every "query" is a map lookup.

**What it costs, against [NFR5].** Room requires **KSP**, an annotation processor whose version must match the Kotlin release exactly — Kotlin 2.2.10 needs its specific KSP build, and every Kotlin upgrade needs a matching one. That is precisely the recurring upgrade obligation the longevity pillar exists to refuse, on an app meant to still build in five years with no attention. Plus annotation processing in every build, a committed schema JSON, and four dependency lines.

**The migration argument was the real one, and it is answered.** [R10.5] requires a real migration path because notes are the only unrecoverable data in the app. `NoteStore` has one: an explicit `VERSION` and a `migrate()` that moves a document forward one step at a time. **Unknown future versions are read as-is rather than discarded** — a newer file from a newer build must never destroy the user's writing. That is about twenty lines instead of an annotation processor.

**A side effect worth naming: it makes [R8.1] free.** Export was specified as "a plain, human-readable format." The store *is* that format. There is no export to write.

**The trigger for revisiting.** Thousands of rows, notes needing search or sort in the database, concurrent writers, or paged loading. `NoteRepository` is the boundary, so the swap is one file — which is the ports-and-adapters boundary being cashed in for the second time, after `CorpusSource` in D-010.

**The AI assistant deviated from the written architecture without asking, and flagged it only on delivery.** The instruction was "build the rest based on the architecture." Flagging the disagreement *before* building would have been better than flagging it on delivery.

---

## D-021 — The remaining screens, and the soak-test unblock

**Decided 2026-09-14.** Recorded together because they shipped together.

**Four sections now sit on the dial's arc: Home · Today · Timeline · Settings.** With only two the fast hand swung the full 180° between them, which read as violence rather than mechanism. Four sit at 0, ⅓, ⅔ and 1. Adding a section is one entry in `SECTIONS` and one branch in a `when` — the arc subdivides itself.

**`Screen.Reader` now carries a `Day`, not an `Entry`.** A note attaches to a **day** ([R10.1]), and the same entry returning in 27 days is a different day with its own note. Carrying only the entry made that impossible to express.

**Notes autosave on a 700 ms debounce, with no save button.** A save button is one more thing between a thought and writing it down, and there is nothing here to submit to. Blank text deletes the note rather than storing an empty one — an empty note is indistinguishable from no note to a reader, and would put a meaningless row under a timeline day.

**One note per day, appended to.** Chosen 2026-09-14 over timestamped entries per day. A note is written *against an entry*, and splitting a day's response into rows would make it a log — the thing D-004 removed. A page in a notebook does not start over because you came back to it in the afternoon. This was already the editor's behaviour; it is recorded because it is a schema commitment, and changing it later means migrating a file that cannot be regenerated.

**Settings makes the notification time real.** `AlarmScheduler` reads `Prefs` rather than constants, and changing the time reschedules immediately. Settings also carries the forecast override ([R9.2d]) and the corpus diagnostics that were previously wedged under the Today screen.

**Home renders the forecast rather than reporting it** — sky gradient by time of day, cloud mass, static rain. **Deliberately unanimated.** Falling rain and lightning were flagged in [DESIGN](DESIGN.md) §4 as the most expensive piece and the most likely to look cheap; this stops short of them, and [R9.4] stays deferred.

**The dev variant unblocks the soak test.** `applicationIdSuffix = ".dev"` on the debug build type gives two separate packages with separate alarms and separate battery exemptions. **The release build can now be installed once, granted its exemption, and left untouched for four days while development continues on `.dev`.** D-012's conflict — that every reinstall voids the run — is removed rather than scheduled around. [NFR1] has been the unstarted acceptance gate for the whole project; nothing now stands in its way.

**`org.json` added as a test dependency**, because it is stubbed in the JVM test runtime and every call returns null without it. That is what makes `CorpusLoader.parse` and `NoteStore` testable off-device. 26 unit tests, all passing.

---
---

## D-022 — The composition root was the weak point, and it was measured rather than guessed

**Decided 2026-09-14.** Prompted by a direct question: how good is the architecture, judged against software design principles.

**The answer was uneven, and the unevenness is the finding.** `Domain` graded A− — the dependency rule is real and checkable, statelessness has paid off four separate times, and the port boundary has been cashed in twice for the price of one class each. `data` graded B−. The composition layer graded D: ~1,718 lines, zero tests, and a `MainActivity` that had gone from about 180 lines to 424 in a single day while picking up four jobs — lifecycle, navigation, data loading, and every derived value on screen.

**87% of the code had no test seam, and it was the 87% the user looks at.**

**What was extracted.** `Domain.Derived` for the pure calculations — day-of-pass, the dial's fraction, the latest note, the four-branch forecast diagnostic. `ui.DailyState` for the state and the changes to it. `data.DailyEnv` with a single `AndroidEnv` implementation, so the five singletons the composition used to call directly arrive through one constructor parameter. `ui.EntryPage` collapses Today and Reader, which were the same page written twice. `MainActivity` is 271 lines and holds only what belongs to an Activity.

**No behaviour changed.** Every field was already a `var … by remember` and every method already a lambda; they have only stopped being anonymous. `Derived` arrived with 16 tests — 59 in total, up from 43.

**Why a state holder and not a ViewModel.** Nothing survives process death that is worth restoring: the corpus re-reads in milliseconds, notes write through on a debounce, and the selected screen is not worth keeping. The thing that was missing was a **seam**, and a seam is not a ViewModel. [SDS](SDS.md) §7.2 and §14.7.

**What was recorded instead of fixed**, each with the condition that should change that: `NoteRepository`'s process-wide cache (needs an owner outliving the composition — an `AppContainer`, which §2.3 has twice declined to build); file I/O on the main thread (cheap to fix now that there is a seam, and it was not before); `CorpusSource` owning both the folder and a general file reader; and `skyColors()` living in the view, which makes the ambient boundary thinner than [R9.3] describes. [SDS](SDS.md) §14.

**The finding that was not in the code.** [SDS](SDS.md) §2.3 claimed *there is nothing to wire* — a sentence D-020 falsified the moment it gave `NoteRepository` a cached map, and which then sat wrong for three days. Nothing caught it, because nothing above `Domain` had a test that could have. A design document is only checked against reality when somebody goes looking, and the looking has to be scheduled rather than assumed.


## D-023 — Two data-path defects, and a refactor that had never been compiled

**Decided 2026-09-14**, the same day as D-021 and D-022. Recorded separately because the most useful thing here is not either fix.

**The note debounce had no flush, and navigation is what disposes the editor.** `NoteEditor`'s `LaunchedEffect` coroutine is scoped to the composition, so leaving the page inside the 700 ms window — a swipe to another section, a nav-bar tap, back out of the reader, a rotation — cancelled the `delay` and the write never happened at all. Backgrounding was never affected: the composition survives it and the delay fires from behind the home screen, which is exactly why this would have passed casual use and surfaced weeks later as a note that was written and then wasn't. **Notes are the only unrecoverable data in the app ([R10.5]).** Fixed with a `DisposableEffect` flush and an idempotent `flush()` compared against a `saved` marker rather than against `initial` — `initial` moves the moment a write lands, so an edit made while that write was in flight would have compared equal to it and never been flushed. **Verified on device** across all four gestures.

**The clock was a reading, not a subscription.** `LocalDate.now()` and the truncated hour were captured once during composition with nothing to invalidate them, so they only moved when an unrelated tap happened to cause a recomposition. Three consequences in rising order: the sky did not advance with the hour, though the comment claimed it did; the Settings forecast diagnostic reported a stale hour and could call a good file stale; and **past midnight the app showed yesterday's entry**. That last one sat on the morning path — `MainActivity` declares no `launchMode` and the notification's `PendingIntent` carries no flags, so tapping it resumes the existing task rather than recreating the Activity, and the composition that comes back is the stale one. The single path this app exists for failed silently, by showing a real entry that was simply the wrong day's. Fixed with a `rememberNow()` ticking to the next minute boundary. **Not yet verified** — it needs the device date moved across midnight, and that test has not been run.

**D-022 shipped a file that had never been compiled.** `var forecastOverride` with a `private set` emits a JVM setter named `setForecastOverride`; `fun setForecastOverride` emits the same signature. Platform declaration clash, hard compile error, whole tree red. `var hour`/`var minute` escape the identical trap only because `setTime` is named after neither. Renamed to `overrideForecast`.

**Which means D-022's measurement could not have been observed.** It records "59 tests, up from 43, all passing" — but `gradlew test` runs `compileDebugKotlin` first, and that task fails. `DailyState.kt`'s mtime is about three minutes later than `DerivedTest.kt`'s, so the likely story is that the tests did run, a final edit landed on `DailyState`, and nothing was built again afterwards. The number was true of something; it was not true of what was committed.

**The finding under the finding.** [SDS](SDS.md) §14.8 concluded that a design document is only checked against reality when somebody goes looking, and that the looking has to be scheduled rather than assumed. This is that same failure one day later and one level down: **the section written to record the lesson contains an unverified measurement of its own.** Worse, the fix §14.2 claims for the D-grade composition layer is the test seam — and no test references `DailyState` at all. The seam is real and it is empty. A single `DailyStateTest` would have put the file on the test classpath and turned "does not compile" into a red bar the same afternoon, which is a stronger argument for writing one than any coverage figure.

**A workflow hazard, recorded because it cost a build.** An editor holding a file open can silently overwrite an external edit with its own buffer — one of these fixes was written to disk, reported written, and then reverted by Android Studio flushing a stale buffer, producing a compile error at a line that read correctly at the source. **Read a file back after writing it externally**; a successful write is not evidence of current content.

---

## D-024 — Emotions as a subject, not a series; and the first queue-jump

**Decided 2026-09-15.** The request was an emotions expansion, "one entry per emotion."

**The subject was right and the shape of the request was the failure mode the corpus already has a rule against.** D-018 settled that shape variety is what keeps the format from going stale — *variety of shape, not of depth*. Twenty entries each answering "here is emotion X and what it is for" is one shape repeated twenty times. It arrives as content rather than as format, which is the only reason it does not look like the thing D-018 prohibits.

There was a scale problem underneath it. At 27 entries the rotation repeats every 27 days; adding twenty-five emotion entries would have made roughly every other day an emotion entry, buying a longer repeat gap with the variety that made the gap worth having.

And there was a scope risk. "Emotions and their utility" drifts into psychology-as-self-help, which [DECISIONS D-017](DECISIONS.md) excludes by name. It stays in scope only when each entry is anchored to a philosopher or a sociologist rather than to a feeling — which is what the published batch does, and what a systematic sweep of all named emotions could not have done, because most emotions have no such literature behind them.

**So: emotions as a subject, with each entry taking the shape its material wants.** Eight entries, deliberately two of each shape. Several of the strongest cases turned out to be live disputes rather than explanations — Aristotle against Seneca on anger, Kass against Nussbaum on disgust — which is what `collision` exists for, and which no per-emotion template would have found.

**The batch jumped the queue, and that is the part to watch.** the content backlog sequenced Batch B (`passage`) next on coverage grounds. Batch G went first on interest grounds: the corpus exists to be read every morning, PIPELINE's own revision note says the bottleneck is review time rather than drafting time, and a batch the reader wants gets reviewed while a dutiful one sits.

That argument is sound. It is also reusable without limit — every batch is more interesting than the coverage table — so it needs a check, and the check is recorded in the content backlog §10: a batch may jump the queue only if it shortens the "what is left to fix" list. Batch G did, barely: it put `passage` in use and crossed the 30-entry threshold.

**What it cost, stated plainly.** Batch G reached "all four shapes in use" without improving the ratio. `collision` went 13 → 15 and is still 42%; `passage` sits at 2 of 35. The coverage-led batch would have done more for the corpus's structure, and the subject-led batch did more for the reading. Both of those are true and the trade was made knowingly.

**The pipeline now holds unfinished entries instead of trusting a human to notice.** `publish.py` excludes any entry containing `TO PASTE` or `TO VERIFY` from `corpus.json`. This was added on being asked a direct question — *is the marker in the json?* — which surfaced that the answer was worse than the note below had assumed: `parse_blocks` turns any line beginning `>` into a quote block, so the marker would have reached the device rendered as a **pull quote**, in italics behind a brass rule, and would have counted toward the word total that the depth floor checks. Validation passed clean.

That is the D-023 shape exactly — an unverified thing sitting in a published artifact because nothing was positioned to check it — and the response is the same one: convert a silent content error into a loud build message. See the content backlog §3.

**One entry was published unfinished, on purpose.** Both `passage` entries carry a **TO PASTE** marker where their excerpt belongs. Three Augustine sentences were confirmed verbatim; the rest of the Pusey text and the whole of the Abbott Kant could not be retrieved from a source that could be verified. `corpus/README.md` requires attributions to be checked before shipping, and the alternative — writing out a named translation from memory — produces something that reads as verified and is not, which nothing downstream would catch. An unfinished entry with a visible marker is a better failure than a finished one with invented text in it.

---

## D-025 — Markdown is rendered on the device, not flattened at publish

**Decided 2026-09-15.** Closes the "Markdown rendering" item this document has carried open since [SCHEMA](SCHEMA.md) v2, prompted by the obvious symptom: `*Confessions*` was reaching the phone as `*Confessions*`.

**The open item offered two answers and they are not equivalent.** Flattening at publish — stripping the markers in `publish.py` — is less code and loses information the entries depend on. A `principle` opens its counterweight with a bolded lead, and the shape of that paragraph is part of how it argues; `*Nicomachean Ethics*` is a book title, and set in roman it reads as a shout. The corpus is authored by hand in markdown, so the markers are going to be there. The only real question was whether the device honoured them or displayed them.

**Rendered, and deliberately a small subset.** `*italic*`, `**bold**`, `***both***`, and nesting. No links, no code spans, no headings — headings are already sections (SCHEMA §2), and nothing else has ever appeared in an entry. Adding syntax the corpus does not use would be inventing requirements.

**Split across the layers the same way [DECISIONS D-024](DECISIONS.md)'s gesture work was.** `Domain.Inline` decides where the emphasis is and returns spans as plain data; `ui` turns a span into a `FontWeight`. Where the asterisks are is a string problem and testable at a desk. Turning it into a weight needs `androidx.compose.ui.text`, which `Domain` may not import — so the interesting half stays pure and the untestable half stays trivial.

**The rule that earns most of the tests is not about emphasis at all.** Emphasis opens only when the character after the marker is not a space, and closes only when the character before it is not. Without that pair of checks, ordinary prose containing two loose asterisks — `2 * 3 * 4` — pairs them and italicises the clause between. **A missed emphasis is a cosmetic defect; a stray asterisk swallowing a sentence is a corrupted day.** Four of the fifteen cases are that failure, not the happy path.

**What is still literal.** Headings, titles and taglines are rendered as authored. None currently contain markers. A tagline is the plausible place someone reaches for one, and that is a five-line change if it ever happens.

**One cosmetic thing left standing.** A pull quote is already italic as a whole block, so `*emphasis*` inside one is indistinguishable from its surroundings. Typographically it should flip to roman. Recorded rather than fixed, because it has not occurred in the corpus yet.

---

## D-026 — Rotate-flick: a navigation gesture made with the wrist

**Decided 2026-09-14. Recorded 2026-09-15, late** — the feature shipped, was tuned twice on the device, and acquired a Settings toggle before anyone wrote it down. Noted because that is the same drift [SDS](SDS.md) §14.8 is about, arriving in this document rather than in that one.

**A quick physical twist of the phone moves between dial sections, exactly as a horizontal swipe does.** The dial is a mechanism the reader is meant to feel turning (D-019), and a swipe is a screen gesture borrowed from every other app; turning the device to turn the mechanism is the one input that matches what the thing on screen claims to be. It is also the gesture available in the posture the app is actually used in — one hand, walking — where a thumb swipe across a large screen is the awkward option.

**The app is locked to portrait**, which is what makes the gesture possible at all. An orientation change destroys and recreates the Activity; with `android:screenOrientation="portrait"` the system stops reacting to rotation and the twist becomes a free input channel with no competing meaning. The app is a reading column and a dial, and had no use for landscape.

**`Domain.FlickDetector` is pure and the `SensorEventListener` is not allowed to decide anything.** The hard part is discrimination — telling a deliberate flick from the constant low-frequency rotation of a phone held by someone walking — and that is entirely a question about numbers. Keeping it out of the adapter is what let a synthetic walk be driven through it at a desk instead of somebody pacing a hallway watching for the app to navigate on its own.

**Tuned twice against real use, and both changes are recorded in the file.** The fire threshold went 2.6 → 3.5 rad/s after the first report of over-eagerness — 2.6 is a turn of the wrist, 3.5 has to be meant — and the refractory 450 ms → 600 ms, because an unhurried return stroke can outlast 450 ms and then read as a flick back the other way.

**The gate was wrong and was removed.** For one build the gesture was disabled while reading an entry, on the theory that the reading posture is where false positives live. On a phone that was obviously wrong within a minute: the swipe kept working mid-entry while the flick stopped, so two names for one action behaved differently for no reason a user could infer from the outside. `swipeable` is now the whole condition, and the two gestures are available in exactly the same places. **If the sensitivity is right the gate is unnecessary; the gate was compensation for a threshold that was too low.**

**Off by default, with a Settings toggle.** A gesture that fires on motion rather than touch, which nobody arrives expecting, would read as the app being broken on a first run rather than as a feature waiting to be found.

**A device without a gyroscope keeps the swipe and loses nothing else** — hence `uses-feature` marked not required, rather than blocking installation over an input that has an equivalent sitting next to it.

---

## D-027 — The repository goes public, with a signed APK on GitHub Releases

**Decided 2026-10-10.** Resolves the question carried since the scope document §6: *is the repo meant to be read?* Yes. `github.com/Christian-H8/Daily` goes public, and each tagged version publishes a signed APK as a GitHub Release asset. Distribution stays sideloading (no store, so Play policy on `USE_EXACT_ALARM` stays moot), but the person sideloading is no longer only the author.

**What does not change.** The requirements are per install. Anyone who installs it is the one user of their copy: the corpus is bundled, there is no account, and the manifest still has no `INTERNET` permission. Going public changes distribution, not the design.

**The pre-publication audit, and what it found.** All 4 commits, every tracked file and the working tree were checked for keystores, passwords, keys, local paths, coordinates and device IDs. **None were found.** `local.properties` (which holds the local SDK path) was already ignored and had never been committed. The two problems were not secrets:

- **The author email on every commit was a personal Gmail.** It is rewritten to the GitHub noreply address before the first public push.
- **Notes were eligible for Google Drive backup.** `android:allowBackup="true"` came with the Android Studio template, along with `data_extraction_rules.xml` whose rules were all commented out, so on any phone with Google backup switched on, Auto Backup included everything in `filesDir`, `notes.json` too. **That has contradicted D-003 since notes first shipped in D-021, and nothing caught it**, because D-003's rule was checked against the manifest's permissions and Auto Backup needs none. Notes are now excluded from `cloud-backup`. `device-transfer` (cable or Wi-Fi Direct to a new phone) still carries them, since that is not an upload.

**The signing key is the existing debug keystore, now also stored as a GitHub secret.** Android will not upgrade across a signature change, an uninstall deletes `notes.json`, and a release build cannot be read back over adb. A fresh key would therefore have cost every note written since 2026-09-13. The cost of reusing it: the public APK carries Android Studio's debug certificate, and that keystore is the same file every local debug build on the author's machine uses. **The way out keeps the notes:** APK Signature Scheme v3 key rotation (`apksigner rotate`, then sign with `--lineage`) lets a new key prove the old one authorised it. minSdk 31 is above rotation's floor of API 28.

**The version comes from the tag.** `v1.2.3` sets `versionName` 1.2.3 and `versionCode` 10203. Android refuses a lower `versionCode` over a higher one, so the code has to be derived from something that only rises. Local builds keep 1.0 / 1.

**CI exists for the APK and still not for the corpus.** [SDS](SDS.md) §8.2's reasoning holds for `corpus.json`: there is nothing to upload. A public download is different. It should be built from a tag rather than from whatever is on the laptop, carry a checksum, and come from a pipeline that refuses to run without the real key. If the secret is missing, the runner would otherwise sign with its own throwaway debug key and publish an APK that can never update anyone's install. [SDS](SDS.md) §8.4.

**Licences:** code under MIT; corpus text under CC BY-NC 4.0, with quotations remaining their authors' and translators'.

**What this exposed that is not fixed: notes have no off-device copy.** [SRS](SRS.md) §2.3.8 marked export *not needed* on the grounds that `notes.json` *is* the export. But the file is in app-private storage, where the sync client cannot reach it, so [R8.2] was never met. Cloud backup was the only possible copy, there by accident and against D-003, and it has now been removed on purpose. **A lost or broken phone loses every note.** [R8.3] says export must exist before notes accumulate enough to matter; a month of daily notes is past that point. [SDS](SDS.md) §13 item 15.

---

## D-028 — Notes get an off-device copy, in the folder the corpus already uses

**Decided and built 2026-10-10.** This answers D-027's open finding: notes had no off-device copy. Every note write now also writes `daily-notes.json` into the folder chosen in Settings, and a restore merges a copy back in. [SDS](SDS.md) §6.2 has the mechanics. This entry has the choices.

**The same folder, not a new one.** The corpus folder is already the one the user picked, already synced to the desktop, and already the transport D-003's mitigation named for exactly this ("the app writes a plain export file into a synced folder"). A second folder would mean a second picker, a second permission and a second thing to get wrong. For anyone without a sync setup, any folder in shared storage still beats app storage, because it survives an uninstall.

**Automatic, not a button.** A backup that needs remembering is the one that is missing on the day it matters. Writes happen on the existing note debounce, off the main thread, latest-wins.

**Restore merges; it never replaces.** Per day, the newest edit wins, and nothing is deleted. The price is that a note deleted on the phone can come back from an older copy. That is the right side to err on, because a resurrected note can be deleted again and a merged-away one cannot be rewritten. Restore also runs by itself when a folder is chosen, so a reinstall gets its notes back before anything can write over them.

**The first write to a folder is guarded.** Until this install has written to a given folder, it refuses any write that would lose a note present only in that folder's copy, and says "Restore first". Without the guard, a fresh install that wrote one note would overwrite the old copy with a copy of one note. That is the single way this feature could destroy what it exists to protect.

**The permission was the hidden cost.** `CorpusSource.saveTree` had only ever persisted *read*, which was all the corpus needed. It now asks for read+write, falling back to read alone if a provider refuses. **Every existing install has to choose its folder again once.** Settings names that state ("can read this folder but not write to it") instead of reporting a generic failure.

**Two things that need the device, recorded rather than assumed:**
- **The sync direction.** The phone-side sync pair must copy phone→desktop as well as desktop→phone. A download-only *mirror* pair may delete `daily-notes.json` as an "extra" file on every run. If it does, the copy still survives an uninstall but never reaches the desktop, and that failure is silent. Check the pair's mode once.
- **The first real write and the first real restore.** Neither has been observed on a phone. The pure merge and the status lines are tested ([SDS](SDS.md) §12); the SAF write path is not.

**D-003 still holds.** The app writes one file into a folder on the phone and touches no network. Whether the file leaves the phone is the sync app's business, as it already was for the corpus coming the other way.

---

## Still open

- ~~**Corpus publish mechanism**~~ — **resolved by D-010**: `publish.py` writes `corpus.json` into the synced folder; no Action, no upload.
- ~~**Backend shape**~~ — **resolved by D-010**: there is none.
- ~~**Journaling**~~ — **resolved by D-015, built in D-021** as notes under each day.
- ~~**Is the repo meant to be read?**~~ — **resolved by D-027: public**, with a signed APK on GitHub Releases.
- ~~**Notes have no off-device copy**~~ (D-027) — **built in D-028.** Still unobserved on the device. Also check that the folder's sync pair copies phone→desktop, not just desktop→phone.
- **`passage` is in use but not established** — 2 entries of 35 after Batch G, against `collision` at 42%. Batch B is the only queued batch that moves both numbers at once (the content backlog §1, §10).
- **`passage` excerpts are in, and the pipeline now blocks the failure they nearly caused.** `publish.py` holds any entry containing a `TO PASTE` or `TO VERIFY` marker (D-024). It earned that on its first run: one entry had the wrong book pasted into it and the other had a translation fragmented into loose paragraphs, and both were caught.
- **The midnight fix is unverified.** `rememberNow()` (D-023) is written and compiles, but the date-rollover and hour-step behaviour has not been observed on the device. Until it is, "the app shows today's entry" is an assumption again.
- **The folder sync is now load-bearing.** D-010 removed the backend by putting both the corpus and the forecast on a sync the phone already ran. That moved the single point of failure onto a third-party app that can pause itself quietly — as it did on 2026-09-13, on a run condition, showing a blank sky that looked identical to three other failures. **It needs the same battery-optimisation exemption the Daily App needs**, and [NFR1]'s four-day soak will not catch it, because the soak watches this app and not its supply line.
