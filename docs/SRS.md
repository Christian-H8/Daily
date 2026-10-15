---
created: 2026-08-27
updated: 2026-10-10
status: v1.3 — tracks SCOPE.md, DECISIONS.md (D-001…D-028), SCHEMA.md v2, DESIGN.md, ARCHITECTURE.md. No sections under review.
---

# Software Requirements Specification — Daily App

> **Public copy.** Generated from the project's private design documents with personal context removed (see [README](README.md)). IDs such as `[R8.2]`, `D-028` and `§6.2` resolve within these six documents; references to the scope document and the content backlog point at documents that are not published.

> **Reading note.** Working title; `DailyApp/` is a placeholder. This SRS consolidates the scope document, [DECISIONS](DECISIONS.md), [SCHEMA](SCHEMA.md) and [DESIGN](DESIGN.md) into numbered requirements. Anything tracing to a settled decision is stated as a firm `[R]`. Anything unresolved is **(OPEN)**; anything wanted but out of v1 is **(DEFERRED)**. Each function also carries a **Status** line, because code now exists and a requirements document that cannot be checked against the build is decoration.
>
> **v1.0 is a rewrite, not an increment.** v0.3 described a quote-plus-paragraph app with a cloud backend, in-app reminders, and themes as a grouping. All three are gone. D-015 replaced the content model, D-014 deferred reminders, D-016 replaced themes with tags, and D-010 resolved against a backend entirely. Requirement IDs referenced elsewhere ([R2.6], [R6.3], [R6.4]) are preserved; superseded ones are struck through rather than deleted, so the decision log's references still resolve.

## Revision History

| Date | Description | Author | Comments |
|---|---|---|---|
| 2026-10-10 | Version 1.3 | Author (with Claude) | §2.3.8 **built (D-028)**. [R8.1]–[R8.5] satisfied by `daily-notes.json` in the chosen folder plus a merge-only restore. §4 backup line updated. |
| 2026-10-10 | Version 1.2 | Author (with Claude) | **Public release (D-027).** §2.3.8 status corrected: [R8.x] is **needed and unmet**. `notes.json` is app-private, which fails [R8.2], and with cloud backup now excluded notes have no off-device copy. §4 distribution and backup lines updated. §5 open question 2 resolved: public. |
| 2026-09-14 | Version 1.1 | Author (with Claude) | Status lines updated: **§2.3.10 notes, §2.3.9 forecast, and the Home/Settings halves of §2.3.11 are built** (D-020, D-021). [R10.5] answered by a versioned JSON store rather than Room — see D-020. [R8.x] marked **not needed**: the store is the export. [NFR1] is now unblocked by the `.dev` variant. |
| 2026-09-12 | Version 1.0 | Author (with Claude) | Rewritten against SCHEMA v2. §2.3.3 rebuilt around four entry shapes (D-015). §2.3.4 reminders **deferred** (D-014). §2.3.5 library replaced by timeline + tags (D-016). §2.3.6 keeps validation, loses hosting; §2.3.7 replaced by synced-folder reading — both per **D-010, now resolved**: an existing folder sync already carries the authoring notes, so there is no backend. §2.3.10 **notes** added, returning per D-015. §2.3.9 ambient partially promoted per D-019. §2.3.11 presentation requirements added. Subject matter constrained per D-017. |
| 2026-09-11 | Version 0.3 | Author (with Claude) | [R9.2] corrected per D-009. §2.3.6 / §2.3.7 flagged UNDER REVIEW per D-010. |
| 2026-08-27 | Version 0.2 | Author (with Claude) | [R2.4]/[R2.5] rewritten after D-007: one fixed permutation rather than a per-cycle reshuffle. |
| 2026-08-27 | Version 0.1 | Author (with Claude) | Initial consolidation, D-001 through D-006. |

## Table of Contents

1. Introduction — 1.1 Purpose · 1.2 Scope · 1.3 Glossary
2. General Description — 2.1 Product Perspective · 2.2 Design Pillars · 2.3 Product Functions · 2.4 Data Objects
3. Non-Functional Requirements
4. External Interfaces & Technology
5. Assumptions, Constraints & Open Questions

---

## 1. Introduction

### 1.1 Purpose

A single-user Android application that delivers **one substantial piece of writing per day**, drawn from a corpus the user assembles and edits, and that keeps every past day reachable with the user's own notes attached.

The product thesis, which every requirement below is subordinate to:

> Continually provide edifying material to ponder, especially when out and about.

Two clarifications that carry real weight:

- **Reading is the means, not the end** (D-019). It is the most effective way to get edifying material into a spare twenty minutes; where reading *fails* — walking, driving, hands busy — another means may serve the same goal.
- **A day must develop its material, not merely present it** ([SCHEMA](SCHEMA.md)). A quote with a paragraph attached is not a day. This replaces v0.3's entire content model.

The authored corpus is the only part of this that cannot be bought.

### 1.2 Scope

**In scope.** Daily delivery of one substantial entry in one of four shapes; a notification carrying it; a timeline of every past day with tag filtering; user notes attached to days; a desktop authoring path with a validating publish step; corpus distribution by an existing file-sync channel; a deliberate visual design.

**Out of scope.** Multi-user support; iOS; AI at runtime; any upload path from device to network; search; graph traversal of related entries; a separate notes browser.

**The governing constraint** ([DECISIONS](DECISIONS.md) D-003), strengthened rather than weakened since v0.3:

> Nothing the user generates on the phone leaves the phone. Only content authored on the desktop travels down to it.

In v0.3 this was policy enforced by a download-only client. **As of D-010 the app has no network code at all**, which makes it structural.

**Amended 2026-10-10 (D-028).** One thing the user generates is now written outside app storage, on purpose: the notes backup, `daily-notes.json`, in the folder the user chose. The app still sends nothing anywhere. But that folder is the synced one, so the copy reaches the desktop by the same channel that brings the corpus down, which is exactly the mitigation D-003 itself named. The precise rule is now: **the app has no path off the device; the only user content it writes outside its own storage is the opt-in backup, and where that goes is the sync tool's doing.**

### 1.3 Glossary

| Term | Meaning |
|---|---|
| **Entry** | One day's writing. Frontmatter plus an ordered list of sections. The atomic unit. |
| **Shape** | One of four ways an entry goes deep: `collision`, `expound`, `principle`, `passage` ([SCHEMA](SCHEMA.md) §1). |
| **Section** | A headed part of an entry, holding ordered blocks. |
| **Block** | A paragraph or an attributed quote. The only two kinds the client renders. |
| **Depth floor** | The rule that an entry must develop its material. Enforced crudely by a word-count minimum. |
| **Tag** | A label for authoring and browsing. Replaces "theme" (D-016). **Never affects scheduling** (D-006). |
| **Corpus** | The complete set of entries, plus a version hash. |
| **Pass** | One traversal of the corpus, *n* days long. The order does not change between passes (D-007). |
| **Publish** | Validating the vault's markdown and emitting `corpus.json`. |
| **Note** | The user's own writing, attached to a **day**, carrying that day's date and entry id. |

---

## 2. General Description

### 2.1 Product Perspective

Three components, one-way, with **no network component anywhere**:

```
Markdown (Obsidian)     ──▶  publish.py  ──▶  corpus.json
        ▲                    validate           │
        │ authored by hand                      │ folder sync
        │ on the desktop                        ▼  (already in service)
   AI drafting                              Android app
   (outside the system)                          │
                                                 ▼
                                          notes, on-device only
```

**D-010 is resolved against a backend.** The author already runs a folder sync mirroring the authoring notes to the phone — a debugged, working channel. `corpus.json` is written into the vault beside the markdown it came from, so it arrives by the same pipe with no new component, no hosting, no `INTERNET` permission and no recurring cost.

### 2.2 Design Pillars

- **[P1] The corpus is the product.** Every trade favours the authored content over app features.
- **[P2] Privacy is structural, not policy.** With D-010 resolved, the app has no upload path *because it has no network path*.
- **[P3] Competence and Autonomy.** Relatedness has no surface in a single-user app and must not be faked with streaks or a persona.
- **[P4] Longevity over velocity.** Prefer boring, native, dependency-light choices.
- **[P5] The app records nothing about its user** — except what the user deliberately writes (§2.3.10).
- **[P6] Nothing in this app should make the user hurry** (D-019). The reading-time counter was built and then removed under this pillar: a duration invites triage. This governs presentation decisions the way [P1] governs feature decisions.

### 2.3 Product Functions

Each function carries a **Description**, a **Stimulus/Response Sequence**, a **Status**, and **Functional Requirements**.

---

#### 2.3.1 App shell and first run

**Description.** On first launch the app must be usable with the corpus it shipped with, before the user has pointed it at anything.

**Stimulus/Response.** User installs and opens → the bundled corpus loads → today's entry is shown.

**Status: built.**

- [R1.1] The app must ship with a bundled copy of the corpus so that first launch works with no configuration.
- [R1.2] The app must present today's entry as its primary screen, reachable in zero navigation steps from launch.
- [R1.3] ~~three destinations: today, library, reminders~~ **superseded.** The app's destinations are **Home, Today, Timeline and Settings**. Reminders are gone (D-014); the reader is a sub-view of the timeline, not a destination.
- [R1.4] The app must not require an account, sign-in, or onboarding flow of any kind.
- [R1.5] The app must not request any runtime permission it does not need for a stated requirement. **In v1 that is notifications only.** No location, **no network permission at all**, and no storage permission — folder access is granted by the user picking a folder (§2.3.7), which is a grant rather than a permission.

---

#### 2.3.2 Daily entry selection

**Description.** Exactly one entry is designated for each calendar day, computed rather than stored (D-005).

**Status: built and verified.** Six unit tests plus a cross-implementation fixture.

- [R2.1] Selection must be a **pure function of the local calendar date and the corpus contents**, storing no record of the selection.
- [R2.2] Selection must be **stable within a calendar day**.
- [R2.3] Selection must draw from **the entire corpus**. Tags must not affect it (D-006, D-016).
- [R2.4] Selection must not repeat an entry until every other entry has been shown — **every window of *n* consecutive days must contain each of the *n* entries exactly once.**
- [R2.5] The minimum gap between two showings must be exactly *n* days, **including across the wrap seam** (D-007). A fresh ordering per pass is explicitly *not* required and must not be introduced — it is incompatible with this requirement.
- [R2.6] Selection must be **reproducible off-device**, so a future date can be previewed on the desktop by running the same function.
- [R2.7] The system must never present an empty state for lack of content.
- [R2.8] The system must tolerate the corpus changing size. Publishing perturbs the upcoming sequence and may cause one early repeat or skip. **Accepted, not corrected** — correcting it needs stored state, which [P5] forbids.
- [R2.9] Day boundaries must follow the device's local timezone, not UTC.
- [R2.10] **(new)** Entry shape must not affect selection. Every entry meets the depth floor, so every day is substantial and the rotation needs no weighting by type.

---

#### 2.3.3 Daily presentation and notification

**Description.** The day's entry is delivered by notification and rendered in full on opening.

**Status: built.** Renderer walks sections and styles by block kind.

- [R3.1] The app must fire one notification per day at a configured time.
- [R3.2] Tapping the notification must open directly to today's entry. **It must not pass through the homepage** (D-019) — the morning path stays frictionless.
- [R3.3] The today screen must render **the entry's sections in order**, with paragraphs and attributed quotes distinguished visually.
- [R3.4] Attribution on a quote must always be visible, never behind a tap. It is the guard against misattribution and is not decoration.
- [R3.5] The daily notification must be schedulable as an **inexact** alarm. Exactness is not required (D-011: `setExactAndAllowWhileIdle` is subject to the same Doze throttle, so it buys nothing).
- [R3.6] Notification delivery must survive device reboot; the app must re-register on `BOOT_COMPLETED`.
- [R3.7] The app must detect when exact-alarm permission is unavailable and route to the system settings intent rather than failing silently.
- [R3.8] The app must not send more than the one daily notification. No engagement nudges, no streaks, no re-engagement messaging.
- [R3.9] **(new)** The notification must carry the entry's **title, tagline and hook** — its quote where it has one, otherwise its opening. It must **not** carry a reading time ([P6]).
- [R3.10] **(new)** The renderer must style by **section heading and block kind**, never by matching heading text. Headings are authored freely, so a new shape or an unusual heading must render without a client change ([SCHEMA](SCHEMA.md) §3).
- [R3.11] **(new)** An unrecognised block kind must be skipped, not fatal. The corpus ships ahead of the client by design.

---

#### 2.3.4 Reminders **(DEFERRED — D-014)**

Cut from v1. A reminders app already exists in better versions, and reminders were the part of v0.3 least connected to the product thesis. Requirements [R4.1]–[R4.7] are retained in v0.3 for the record and are **not** requirements of this version.

The one consequence worth carrying forward: **reminders were the justification for Room in v0.3.** That justification left with them and returned with notes (§2.3.10).

---

#### 2.3.5 The library — a timeline and tags

**Description.** Every past day, scrollable, newest first, with the note written under it where one exists.

**Stimulus/Response.** User opens the timeline → days are listed newest first with month headers → selecting a day opens that entry → filtering by tag narrows the list.

**Status: built, except notes.**

- [R5.1] The library must present a **scrollable timeline, newest first, one row per day**, showing the entry that day computes to.
- [R5.2] The library must support **filtering by tag**.
- [R5.3] The library must be derivable **entirely from the corpus and the date**, with no stored index.
- [R5.4] The library must not display when an entry was last shown, how often it has appeared, or any usage-derived value.
- [R5.5] The library must work fully offline.
- [R5.6] **Search must not be built.** **`related`-edge traversal must not be built.** The vault is the graph and the search; duplicating Obsidian badly is not the job (D-016). *Most future scope requests will be a form of "what if it also…" — this requirement answers them.*
- [R5.7] **(new)** The timeline must display a note beneath its day where one exists (§2.3.10).
- [R5.8] **(new)** Because the permutation derives from corpus size, a past row may show a different entry than was actually seen that day. **This drift is accepted.** Days the user wrote a note on are pinned by that note's stored entry id; days scrolled past and forgotten drift. Accurate where the user engaged, approximate where they did not, still nothing tracked.

---

#### 2.3.6 Authoring and publish pipeline

**Description.** The desktop path from a written entry to content on the phone. A first-class part of the system: the scope document identifies authoring friction as the single factor deciding whether the corpus reaches 300 entries or stops at 40.

**Status: built.** `corpus/tools/publish.py`, SCHEMA v2.

- [R6.1] The corpus must be authored as markdown in the Obsidian vault, **one file per entry**, in the convention in [SCHEMA](SCHEMA.md) §2. *(Changed from one file per theme — entries now run past a thousand words.)*
- [R6.2] Publishing must require no step beyond writing the file and running the publish script. No manual export, no admin UI, no app rebuild.
- [R6.3] The pipeline must **fail the build** on: a missing required section for the entry's shape; an unknown shape; an entry under the hard word floor; a duplicate or malformed entry id; or duplicate quote text across entries.
- [R6.4] Validation failure must prevent publication. A malformed entry must never reach the device.
- [R6.5] The published artefact must be a single JSON object carrying the entire corpus. Incremental or delta sync must not be built.
- [R6.6] The published object must carry a **version identifier**, so the phone can be checked against the desktop.
- [R6.7] The pipeline must not require the app to be rebuilt or reinstalled for new content to appear. **This is load-bearing:** a reinstall resets the battery-optimisation exemption (D-012), so content changes that force a rebuild would break notification delivery.
- [R6.8] **(new)** `words` and `minutes` must be **computed at publish time, never authored**. Neither is displayed ([P6], [R3.9]); they exist because they cost nothing and the timeline may want them.
- [R6.9] **(new)** Subject matter is constrained to **philosophy, sociology and spirituality** (D-017). This exists to resist the available supply — the archive the corpus draws from is majority business and career material.
- [R6.10] **(new)** A quotation that cannot be traced to a work, a book/chapter/section, and a named translator must not enter the corpus. Where a compression is used because the full passage is unwieldy, the entry must say so.

---

#### 2.3.7 Corpus distribution

**Description.** How `corpus.json` reaches the phone. **Replaces v0.3 §2.3.7 entirely** — there is no sync client, because there is no server.

**Stimulus/Response.** Publish writes `corpus.json` into the synced notes → a folder sync mirrors them to the phone on its interval → the app reads the file from a folder the user picked once.

**Status: built.**

- [R7.1] The app must read the corpus from **a folder the user selects once**, whose contents an existing file-sync client keeps current. ~~foreground HTTP check~~ **superseded.**
- [R7.2] A missing, unreadable, or malformed file in that folder must be **silent and non-blocking**: the app falls back to its bundled copy and continues. A corpus that half-parses is treated as no corpus — publish validation is the gate, so a broken file on the phone means the sync caught it mid-write.
- [R7.3] The app must contain **no network code at all**. ~~download-only~~ strengthened: there is no client to audit.
- [R7.4] Corpus data must be treated as **disposable** and re-read from the file rather than migrated.
- [R7.5] ~~the `reminders` table must not be disposable~~ **superseded** — see [R10.5].
- [R7.6] **(new)** The app must display the loaded **corpus version, entry count, and whether it is reading the synced folder or the bundled copy.** Without this, "the phone is showing last week's file" is invisible.
- [R7.7] **(new)** Folder access must persist across reboot, since the alarm fires without the app in the foreground.

---

#### 2.3.8 Export and backup

**Description.** The backup story, which must not involve a server.

**Status: not needed (D-020).** `notes.json` is already the plain, human-readable JSON this section specified. The requirements stand; the code to satisfy them does not have to exist.

**Status corrected 2026-10-10: needed, not built (D-027).** The format half of the argument was right; the location half was missed. `notes.json` lives in app-private storage, which the sync client cannot read, so [R8.2] was never met, and [R8.5] restore has nothing to restore from. Google Auto Backup had been the only off-device copy, by template default and against D-003. It is now excluded, so **notes have no off-device copy at all**, and [R8.3]'s "before notes accumulate enough to matter" has passed. [SDS](SDS.md) §6.2, §13 item 15.

**Status: built the same day (D-028), not yet observed on the device.** Every note write also writes `daily-notes.json` into the chosen folder ([R8.1], [R8.2], [R8.4]). Settings → *Restore from folder* merges a copy back in, newest edit per day, never deleting ([R8.5]). Restore also runs when a folder is chosen. Installs that chose their folder before D-028 hold read-only permission and must choose it once more.

- [R8.1] The app must export **notes** to a plain, human-readable format. *(Changed from reminders, which no longer exist.)*
- [R8.2] The export must be written where the existing file-sync client can see it — which, per D-010, is the same folder mechanism as [R7.1].
- [R8.3] Export must exist before notes accumulate enough to matter. It is simultaneously the backup and the anti-lock-in guarantee.
- [R8.4] Export must not require a network connection. *(Trivially satisfied — there is no network.)*
- [R8.5] The app must be able to restore notes from an export file.

---

#### 2.3.9 Ambient visuals — **partially promoted (D-019)**

v0.3 deferred this wholesale. D-019 promotes the parts that do not need the network and keeps the expensive part deferred.

**Status: built**, except [R9.4].

- [R9.1] **(BUILT)** Presentation may vary with **time of day and season**, derived from the device clock with no network call and no permission.
- [R9.2] **(BUILT)** Presentation may vary with the **forecast**, applied elsewhere in the app as a background tint only — never enough to compete with text.
- [R9.2a] Forecast, **not observation** (D-009). A forecast for 08:00 written the previous evening is still right at 08:00; an observation from the previous evening is simply wrong, and a wrong ambient cue is worse than none.
- [R9.2b] The forecast must arrive **by the channel that already carries the corpus** — a `forecast.json` beside `corpus.json` — never by a fetch of its own. **This is a privacy requirement, not a tidiness one:** a weather API would reintroduce the network, and the network is the exact condition under which the user excluded journaling (D-003). Notes and live weather cannot coexist.
- [R9.2c] Weather requires **no new permission** under this design — the coordinates are a constant chosen on the desktop, and the phone never asks the OS anything.
- [R9.2d] **(BUILT)** Settings must carry a **manual override** of the forecast presentation. Forecasts are wrong often enough that the user must be able to say what it is.
- [R9.3] **(v1 REQUIREMENT)** The ambient layer must be architecturally separate from content rendering, one-way: the entry renderer must know nothing about it.
- [R9.4] **(DEFERRED)** Rendered weather on the homepage — gathering cloud, falling rain, flashes on thunder. The most expensive piece and the most likely to look cheap; built last and alone.

---

#### 2.3.10 Notes **(new — returns per D-015)**

**Description.** The user's own writing, attached to days. Excluded in v0.3 solely because the app touched the internet; D-010 removed the internet, so the exclusion lapsed.

**Stimulus/Response.** User reads an entry → writes a note → it is stored on the device → it appears beneath that day in the timeline, and beneath the entry when it comes round again.

**Status: built.** A day's note sits under its entry on Today and in the reader, and under its row in the timeline.

- [R10.1] A note must attach to **the day it was written**, and must carry that day's **date and entry id**.
- [R10.2] Notes must appear **beneath their entry and nowhere else**. There must be **no separate notes browser** (D-016).
- [R10.3] Note text must **never leave the device through the app** — the same rule that would have governed journal text under D-003. *(Amended by D-028: the opt-in backup writes a copy into the chosen folder ([R8.2]); the app itself still has no network path, and whether that file travels is up to the sync tool.)*
- [R10.4] The app must not record whether an entry was read, only what the user chose to write. **Encounter history is derivable from notes** — which is the record without the surveillance, and is consistent with D-004.
- [R10.5] Notes are the **only non-disposable data in the app** and require a real migration path. All schema-migration effort belongs here and nowhere else. **Satisfied without Room (D-020):** `NoteStore` carries an explicit `VERSION` and a `migrate()` that steps a document forward one version at a time, reads an unrecognised newer version as-is rather than discarding it, and skips a malformed row without losing the file. Writes go through a temp file and a rename.
- [R10.6] A repeat must be met with what the user wrote last time. This is what makes the *n*-day rotation worth having: the corpus is fixed, the user is not.
- [R10.7] **(new)** **One note per day, appended to.** A day with a second thought in it extends the same note rather than creating another. Splitting a day's response into separate rows would make it a log, which is what D-004 removed; a page in a notebook does not start over because you came back to it in the afternoon.

---

#### 2.3.11 Presentation **(new — D-019)**

**Description.** The visual design is a product requirement, not decoration. The app's purpose is material the user sits with; the surface decides whether that happens.

**Status: built**, except the rain. Palette, type, edge-to-edge, the dial, the swipe, Home and Settings are all in; [R9.4] animation is not.

- [R11.1] The app must use a **deliberate palette**, not a generated default. Dynamic colour must be off: a wallpaper-derived accent cannot coexist with a chosen one.
- [R11.2] Body text must be a **serif**, at a size and leading chosen for reading at arm's length rather than scanning.
- [R11.3] The app must honour the system **light/dark** setting, and must draw correctly **edge-to-edge** with status-bar icons contrasting against the app's own background.
- [R11.4] A **dial** may be present on every screen as a persistent element — never a destination, and never in the path from notification to entry.
- [R11.5] The dial must show **position, not time**: which section the user is in, and where today sits in the corpus rotation.
- [R11.6] The dial must **retreat while reading** and return when the user is no longer reading.
- [R11.7] Section navigation must be an **ordinary swipe**; any mechanical animation is **feedback, not a control to aim**. A gesture on the critical path must cost nothing to learn and nothing to perform.
- [R11.8] No element of the presentation may report a duration, countdown, or progress-through-the-text ([P6]).

---

#### 2.3.12 Audio **(DEFERRED — D-019)**

- [R12.1] **(DEFERRED)** The day's entry may be spoken aloud using the platform's built-in text-to-speech — offline, no dependency, no network.

Recorded rather than merely noted because it is the only candidate that passed D-019's test: **does it serve pondering in a situation where reading fails?** Walking, driving, hands busy. Every other proposal was reading wearing a hat. Ranked after notes, ahead of [R9.4].

---

### 2.4 Data Objects

| Object | Fields | Disposable? |
|---|---|---|
| **Entry** | id, type, title, tagline, tags, related, words, minutes, sections | Yes — re-read from `corpus.json` |
| **Section** | heading, thinkers, blocks | Yes |
| **Block** | kind (`p` \| `quote`), text, attribution | Yes |
| **Note** | id, date, entryId, text, createdAt | **No — user-authored, must migrate** |
| **Prefs** | corpus folder URI, notification time, forecast override | Yes |

~~Theme~~ and ~~Reminder~~ no longer exist. There is deliberately no table for daily picks, response markers, activity, or history.

**Sections are a list, not named fields.** The renderer walks them in order, which is what allows a fifth shape to ship corpus-first with no client change ([R3.10]).

---

## 3. Non-Functional Requirements

- [NFR1] **Notification reliability is the gating quality attribute — and as of D-021 it is unblocked.** The `.dev` debug variant means the release build can be installed once, granted its exemption, and left untouched for four days while development continues on a separate package. A day that doesn't arrive is a product that doesn't exist. Acceptance: a scheduled notification fires correctly for four consecutive days across at least one reboot and one battery-saver cycle, **on one uninterrupted install** — a reinstall resets the battery exemption and voids the run (D-012).
- [NFR2] **Offline by construction.** Every function works with no network, because there is no network.
- [NFR3] **No outbound user data, structurally.** The client contains no network code. Verifiable by inspecting the manifest.
- [NFR4] **The corpus must never dead-end.** Cycling is intrinsic to the algorithm, not a fallback branch.
- [NFR5] **Longevity.** Must remain buildable and runnable after long periods without maintenance. Prefer platform APIs; treat every added library as a future upgrade obligation.
- [NFR6] ~~Hosting must remain within free tier~~ **superseded — there is no hosting.** The cost of running this is zero by construction (D-010).
- [NFR7] **Legibility of the morning screen.** Readable at arm's length, one-handed, before coffee. Typography and contrast take priority over decoration — and over the dial, which is why [R11.6] exists.
- [NFR8] **Schema risk is concentrated in one table.** Corpus data is re-read; `notes` must migrate. Migration effort belongs there and nowhere else.
- [NFR9] **Authoring friction is a quality attribute.** An entry must go from written to on-device without manual steps. The corpus stalling is the project's primary failure mode.
- [NFR10] **(new) Shape variety is a quality attribute.** The user predicted the collision format going stale, and D-018 measured the corpus at 68% collisions with two of four shapes unused. A corpus that is nominally varied and actually monotonous fails the same way a stalled one does.

---

## 4. External Interfaces & Technology

- **Platform (CONSTRAINT — D-001):** Android. The user owns no other phone. iOS is permanently out of scope, which also eliminates the PWA option, since no shipped browser schedules local notifications.
- **Client (DECIDED — D-002):** native Kotlin with Jetpack Compose; `AlarmManager` for the daily alarm. **No database — notes are a versioned JSON file (D-020).** minSdk 31, targetSdk 37.
- **JSON:** `org.json`, which ships with Android. No serialisation library.
- **Distribution:** sideloaded, from **GitHub Releases** since 2026-10-10 (D-027). Each `v*` tag builds a signed APK in CI. Every release is signed with the same key as the author's installed copy, so updates install in place and keep notes. Still no Play Store listing, therefore no Play policy constraint on `USE_EXACT_ALARM`, and the battery-optimisation exemption is granted by hand. Code is MIT; corpus text is CC BY-NC 4.0.
- **Corpus transport:** an existing folder-sync app, already in service on the author's setup. No new component.
- **Publish:** `corpus/tools/publish.py`. Validation, then emit. No CI, no upload.
- **Backup transport:** the same synced folder, as `daily-notes.json` (D-028). The sync pair must copy phone→desktop for the copy to leave the phone. Google cloud backup is explicitly excluded for notes (`data_extraction_rules.xml`, D-027); device-to-device transfer still carries them.
- **AI:** used only outside the system, to draft entries which the user then edits. **No model is called at runtime, ever.** No API keys ship in the app.

---

## 5. Assumptions, Constraints & Open Questions

### Assumptions

- Exactly one user, one phone, one desktop. *(Per install. Public distribution, D-027, does not change this: anyone else who installs it is the one user of their copy, reading the bundled corpus.)*
- The user authors on the desktop in markdown, in an Obsidian vault already in daily use.
- The corpus grows indefinitely and is never complete. The app must be correct at 27 entries and at 2,000.
- Entries are AI-drafted and human-edited. **The edit pass is assumed**, and is what distinguishes this from a generated feed.

### Constraints

- No deadline. Sequence matters, dates do not.
- Zero recurring cost.
- The privacy invariant (D-003) is not negotiable against convenience. **Any feature requiring the network must be weighed against notes, not merely against privacy in the abstract** — the two cannot coexist.
- Ship threshold: the app is not done before the content is.

### Open Questions

1. **(OPEN)** Markdown *within* a block — the publish format carries plain text per block today. Whether emphasis inside a paragraph is worth a renderer is unanswered; the current answer is no.
2. **(RESOLVED)** ~~Whether the repository is meant to be read~~ — public on GitHub since 2026-10-10, with a README and a signed APK on Releases (D-027).
3. **(OPEN, deferred by design)** Whether a "pin a tag for a stretch" override is ever needed. Trigger for revisiting: mornings feeling arbitrary rather than varied after two or three months (D-006).
4. **(RESOLVED)** ~~Whether the fast hand wraps or stops, and whether the slow hand advances at midnight or on first open~~ — it stops, and midnight. A hand that stops tells you there is nothing past the end; a dial that lies until you show up is worse than one that is merely uninteresting.
5. **(OPEN)** The notification opens `MainActivity`, which starts on Today. Correct in effect, but [R3.2] needs an intent extra before Home could ever be the start destination.

**Resolved since v0.3:** the backend (D-010 — none), journaling (D-015 — returns as notes, built in D-021), corpus hosting (none), markdown rendering strategy (structured blocks at publish time, not a client-side parser), note storage (D-020 — a versioned JSON file, not Room), and the soak-test sequencing conflict (D-021 — a `.dev` variant).

---

## Related
- the scope document — what this is and why, with the reasoning behind each cut
- [DECISIONS](DECISIONS.md) — D-001 … D-028, the decision log this SRS derives from
- [SCHEMA](SCHEMA.md) — authoring format, publish format, the four shapes
- [DESIGN](DESIGN.md) — visual and interaction design
- the content backlog — the content backlog
- the software development lifecycle — this is the Requirements-phase artifact
