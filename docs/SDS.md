---
created: 2026-08-27
updated: 2026-10-10
status: v1.8 — derived from SRS.md v1.0. Public since 2026-10-10 (D-027); §8.4 is the APK release pipeline; notes have an off-device copy (D-028). Notes, Home, Settings and the forecast are built. §14 records measured technical debt; §14.10 is the second scheduled measurement, §14.11 the correctness bug it sits next to, and §14.12 the full SOLID/GRASP pass §14 had been asserting without showing.
---

# Software Design Specification — Daily App

> **Public copy.** Generated from the project's private design documents with personal context removed (see [README](README.md)). IDs such as `[R8.2]`, `D-028` and `§6.2` resolve within these six documents; references to the scope document and the content backlog point at documents that are not published.

> **Reading note.** The Design-phase artifact per the software development lifecycle: *how* the requirements in [SRS](SRS.md) get built. Every design element traces to at least one `[Rn.x]` or `[NFRn]`; §11 is the matrix.
>
> **This describes code that exists.** Where v0.3 proposed, this documents — and where the implementation differs from what was proposed, the implementation wins and the difference is stated. As of v1.1 nothing here is `(planned)`: what remains unbuilt is deferred by requirement, not pending.
>
> **The three sections v0.3 flagged UNDER REVIEW are resolved.** D-010 settled against a backend: `CorpusSource` is a file reader over a synced folder, `INTERNET` never entered the manifest, and §8 kept its validation while losing its upload. The port/adapter boundary in §2.1 is what made that a one-class change rather than a rewrite — the boundary earning its keep exactly once, which is the usual rate.

## Revision History

| Date | Description | Author | Comments |
|---|---|---|---|
| 2026-10-10 | Version 1.8 | Author (with Claude) | **Notes backup built (D-028)** and §6.2 updated to match. §2.2 tree rebuilt: package renamed `Domain` → `domain`, `DailySelector`/`StableHash` moved to match their package, and three files the tree had drifted past are added. §11 [R8.x] row corrected. §12: `NoteMerge` and `BackupStatus` rows, and a count of 124 observed passing, with how they were run. §13 items 3, 7 and 15 closed. |
| 2026-10-10 | Version 1.7 | Author (with Claude) | **Public release (D-027).** §8.1: the repo is public. §8.4 added: tag → GitHub Actions → signed APK on Releases. §6.2 corrected: `notes.json` is app-private, so the sync client cannot see it, [R8.x] is unmet, and notes have no off-device copy now that cloud backup is excluded. §11 [R8.x] row corrected to match. §13 item 4 closed; items 15–16 added. |
| 2026-09-14 | Version 1.2 | Author (with Claude) | **§14 Technical Debt added** — the architecture measured against SOLID/GRASP rather than asserted. `MainActivity` was a 424-line God object with no test seam; §14.2 records the extraction of `Domain.Derived`, `ui.DailyState` and the `data.DailyEnv` port that fixed it. §2.3 corrected: D-020 made `NoteRepository` stateful and the claim that *there is nothing to wire* went stale without anyone noticing. §7.2 rewritten around `DailyState`. §12 59 tests. |
| 2026-09-14 | Version 1.1 | Author (with Claude) | Notes, Home, Settings and the forecast built. §4 rewritten: **notes are a JSON file, not Room (D-020)** — Room's four benefits are all for a scale this will not reach, and KSP is a version lock to every Kotlin release. §2.3 corrected: notes were the expected trigger for a ViewModel and turned out not to be one. §2.4 persistence row settled. §3.6 `NoteRepository` as built. §3.7 `Prefs`, §3.8 `ForecastSource` added. §6.2 export deleted — the store *is* the export. §7.1 four screens. §12 26 tests. |
| 2026-09-12 | Version 1.0 | Author (with Claude) | Rewritten against SRS v1.0 and SCHEMA v2. §2.2 replaced with the real package tree. §3.2 `CorpusRepository` → `CorpusLoader` + `CorpusSource`. §3.3 `ReminderRepository` removed (D-014). §3.5 `Timeline` added. §4 Room deferred to notes only. §5.3 parser rewritten for four shapes. §5.4 gear train added. §6.1 HTTP → SAF. §7 rebuilt around Today/Timeline/Reader + the dial. §8.3 hosting deleted. §10 forecast rides the sync. |
| 2026-09-11 | Version 0.3 | Author (with Claude) | §10 corrected per D-009. §3.2, §6.1, §8 flagged UNDER REVIEW per D-010. |
| 2026-08-27 | Version 0.2 | Author (with Claude) | §3.1 and §5.1 corrected per D-007 — the cycle seed removed. |
| 2026-08-27 | Version 0.1 | Author (with Claude) | Initial design from SRS v0.1. |

---

## 1. Introduction

### 1.1 Purpose

Specify module structure, data design, algorithms, interfaces and error behaviour sufficient to continue implementation without further design decisions in the critical path.

### 1.2 Design drivers

- **[NFR5] Longevity.** Every dependency is a future upgrade obligation, so the design is deliberately dependency-light — no DI framework, no navigation library, no markdown library, no JSON library, no HTTP client, no `androidx.documentfile`.
- **[NFR3] No outbound user data, verifiable by inspection.** v0.3 achieved this with a GET-only client. v1.0 achieves it by having **no network code at all** — the manifest is the proof.
- **[P5] / [R2.1] The app records nothing about its user** except what the user deliberately writes. No activity store, no daily-pick table, no analytics.
- **[P6] Nothing should make the user hurry.** This is a design driver, not a style note: it is why [R3.9] forbids a reading time in the notification and why the dial retreats while reading (§7.5).

---

## 2. Architectural Design

### 2.1 Layering and the dependency rule

Ports-and-adapters, with one strictly enforced rule:

> **`domain` imports nothing from Android, Room, Compose, or the network. Everything else depends inward on `domain`; `domain` depends on nothing.**

This makes selection ([R2.1]–[R2.6]) and timeline reconstruction ([R5.3]) pure functions testable on the JVM in milliseconds, and it is what allows the same algorithm to be re-implemented in Python and diff-tested for off-device preview ([R2.6]).

```
            ┌─────────────────────────────────────────┐
            │                  ui                     │  Compose
            └──────────────┬──────────────────────────┘
                           │
   ┌───────────────────────┼───────────────────────┐
   │                       ▼                       │
┌──┴────────┐      ┌───────────────┐      ┌────────┴────────┐
│  alarm    │─────▶│    domain     │◀─────│      data       │
│ (adapter) │      │  pure Kotlin  │      │  files + prefs  │
└───────────┘      └───────────────┘      └─────────────────┘
```

v0.3's `ambient` box is built as `domain/model/Forecast.kt` plus `data/ForecastSource.kt`; §10 covers it.

### 2.2 Package structure — as built

```
com.christianh.daily/
├── MainActivity.kt              // activity + composition only          §14.2
├── AlarmScheduler.kt            // scheduleDailyQuote(), nextOccurrence()
├── AlarmReceiver.kt             // fires the notification, reschedules tomorrow
├── BootReceiver.kt              // BOOT_COMPLETED → reschedule
├── Notifications.kt             // ensureChannel(), CHANNEL_DAILY
├── domain/
│   ├── DailySelector.kt         // orderOf, indexFor, entryFor, EPOCH  [R2.1]–[R2.8]
│   ├── StableHash.kt            // FNV-1a 64                           §5.1
│   ├── Derived.kt               // passDay, cycleFraction, forecastLine §14.2
│   ├── Timeline.kt              // Day, Timeline.days(), tagsOf()      [R5.1]–[R5.3]
│   ├── Inline.kt                // inline emphasis spans               D-025
│   ├── FlickDetector.kt         // wrist-flick discrimination          D-026
│   ├── NoteMerge.kt             // merge two note sets, newest per day D-028
│   └── model/
│       ├── Entry.kt             // Entry, Section, Block               SCHEMA §3
│       ├── Note.kt              // the only non-disposable data        [R10.1]
│       └── Forecast.kt          // Condition, DayForecast, AmbientState
├── data/
│   ├── DailyEnv.kt              // the port the screens see + AndroidEnv §14.2
│   ├── CorpusLoader.kt          // LoadedCorpus, load(), parse(), versionOf()
│   ├── CorpusSource.kt          // SAF tree URI (read+write), siblingJson(), child()
│   ├── ForecastSource.kt        // forecast.json, same folder           [R9.2b]
│   ├── NoteStore.kt             // notes.json + VERSION + migrate()     D-020
│   ├── NoteRepository.kt        // the storage boundary; restore()      §3.6
│   ├── NoteBackup.kt            // daily-notes.json in the folder       D-028, [R8.x]
│   ├── BackupStatus.kt          // backup/restore outcomes + their lines D-028
│   └── Prefs.kt                 // notification time, forecast override, flick
└── ui/
    ├── DailyState.kt            // Screen, SECTIONS, all screen state   §7.2
    ├── Dial.kt                  // the mechanical dial                 §7.5
    ├── EntryReader.kt           // EntryView / SectionView / BlockView §7.3
    ├── TimelineScreen.kt        // timeline + tag filter               §7.4
    ├── NoteEditor.kt            // NoteEditor / NotePreview            [R10.x]
    ├── EntryPage.kt             // one entry + its note (Today and Reader)
    ├── NavBar.kt                // the four sections as words
    ├── RotationFlick.kt         // gyroscope adapter for FlickDetector D-026
    ├── HomeScreen.kt            // forecast atmosphere                 [R9.1], [R9.2]
    ├── SettingsScreen.kt        // time, sky, flick, corpus, backup    [R9.2d], [R8.5]
    └── theme/ Color.kt · Theme.kt · Type.kt
```

**The two inconsistencies this section used to record are fixed (2026-10-10).** The package is `domain`, lowercase, and `DailySelector.kt` and `StableHash.kt` moved out of `model/` into the directory their `package` line always named. It was a case-only rename on a case-insensitive filesystem, done as two `git mv` steps through a temporary name so git records a rename rather than nothing. The tree above had also drifted: `Inline`, `FlickDetector` and `RotationFlick` were missing from it.

### 2.3 Dependency injection — one interface, constructed by hand

v0.3's `AppContainer` was never built and is not proposed. `DailySelector`, `Timeline` and `Derived` are pure; `CorpusLoader`, `CorpusSource`, `ForecastSource`, `NoteStore` and `Prefs` are objects taking a `Context` parameter.

**v1.1 said "there is nothing to wire". That was already false when it was written.** D-020 replaced Room with a JSON file and gave `NoteRepository` a `loaded` flag and a cached map — process-wide mutable state with no owner — and the sentence justifying the absence of wiring was never revisited. The document drifted from the code for three days and nothing caught it, because nothing above `domain` had a test that could. §14 is the result of measuring instead.

**There is now exactly one thing to wire: `DailyEnv`.** It is the set of calls the composition root makes and nothing else — corpus, forecast, notes, folder, prefs. `AndroidEnv` is the only implementation that ships; `DailyState` takes it as a constructor parameter, so a test supplies its own. Still no container, still no Hilt: one interface, constructed once in `setContent`.

### 2.4 Third-party dependency budget

| Need | Choice | Why not the usual pick |
|---|---|---|
| UI | Jetpack Compose | — |
| Persistence | A JSON file + `SharedPreferences` | **Room rejected (D-020):** 999 notes at the ceiling, one writer, no joins. Its four benefits are all for scale; its cost is KSP, version-locked to every Kotlin release |
| JSON | `org.json` | Ships with Android; `kotlinx.serialization` is a plugin and a version to keep aligned |
| HTTP | **none** | There is no network path (D-010) |
| Navigation | none — a sealed `Screen` and a `when` | Four destinations plus a reader |
| Document access | `DocumentsContract` directly | `androidx.documentfile` is ~20 lines of saving against a dependency |
| Markdown | none | The publish step emits structured blocks; the client never parses markdown |
| Fonts | `FontFamily.Serif` | A bundled face is a strict upgrade touching one file; the system serif needs no asset |
| DI | none | — |

The app's entire runtime dependency list is Compose, `activity-compose`, `core-ktx` and `lifecycle-runtime-ktx`. On the test classpath there is one more: `org.json:json`, because `org.json` is stubbed in the JVM test runtime and every call returns null without a real implementation — that is what makes `CorpusLoader.parse` and `NoteStore` testable off-device.

---

## 3. Component Design

### 3.1 `domain.DailySelector`

Pure. No clock, no context, no I/O — the date is a parameter.

```kotlin
object DailySelector {
    fun orderOf(corpus: List<Entry>): List<Entry> =
        corpus.sortedWith(compareBy({ StableHash.of(it.id) }, { it.id }))

    fun indexFor(date: LocalDate, size: Int, epoch: LocalDate = EPOCH): Int =
        Math.floorMod(ChronoUnit.DAYS.between(epoch, date).toInt(), size)

    fun entryFor(date: LocalDate, corpus: List<Entry>, epoch: LocalDate = EPOCH): Entry? {
        if (corpus.isEmpty()) return null
        return orderOf(corpus)[indexFor(date, corpus.size, epoch)]
    }
}
```

Design notes:

- **One permutation, not one per pass** (D-007). Seeding the sort on a cycle number produced repeats three days apart at the seam. The ordering is fixed; freshness comes from the corpus growing.
- `floorMod` rather than `%`, so a pre-epoch date does not produce a negative index.
- The secondary sort on `id` makes the ordering **total**, so a hash collision cannot make the result depend on input order.
- **`orderOf` and `indexFor` are split out** so the timeline sorts once for 255 days instead of 255 times. `entryFor` is composed from them and behaves identically — pinned by the test in §12.

### 3.2 `data.CorpusSource`

Resolves where `corpus.json` comes from ([R7.1], [R7.2], [R7.7]).

```kotlin
object CorpusSource {
    fun saveTree(context: Context, uri: Uri)        // + takePersistableUriPermission, read+write (D-028)
    fun treeUri(context: Context): Uri?
    fun treeLabel(context: Context): String?
    fun syncedJson(context: Context): String?       // null on any failure, never throws
    fun siblingJson(context: Context, name: String): String?
    fun canWrite(context: Context, tree: Uri): Boolean
    fun assetJson(context: Context): String
    internal fun child(context: Context, tree: Uri, name: String): Uri?   // NoteBackup writes through it
}
```

Three deliberate choices:

- **`takePersistableUriPermission`**, because the alarm fires with no activity in the foreground and the grant must survive reboot ([R7.7]).
- **`syncedJson` cannot throw.** Eight in the morning is not the time. Every failure path returns null and the caller falls back.
- **`DocumentsContract` is used directly** rather than `androidx.documentfile` — a child-by-name lookup is about twenty lines, and the dependency is permanent.

### 3.3 `data.CorpusLoader`

```kotlin
data class LoadedCorpus(val entries: List<Entry>, val version: String, val fromSyncedFolder: Boolean)

object CorpusLoader {
    fun load(context: Context): LoadedCorpus
    fun versionOf(json: String): String
    fun parse(json: String): List<Entry>            // pure — no Android types
}
```

`load()` prefers the synced folder and falls back to the bundled asset on any failure *or an empty result*. **A corpus that half-parses is treated as no corpus**: `publish.py` is the gate ([R6.4]), so a broken file on the device means the sync was caught mid-write, not that an entry was bad.

`parse()` takes a `String` and returns domain types, with no Android import — which makes it unit-testable off-device, once `org.json` is added as a test dependency (§13).

**`LoadedCorpus` carries `version` and `fromSyncedFolder` for [R7.6].** They exist so a stale corpus is visible; without them the failure is silent.

### 3.4 `alarm` — adapters and receivers

| Component | Responsibility |
|---|---|
| `AlarmScheduler.kt` | `scheduleDailyQuote()` and `nextOccurrence()` over `AlarmManager` |
| `AlarmReceiver` | Fires the daily notification, then schedules tomorrow's |
| `BootReceiver` | `BOOT_COMPLETED` → reschedule |

v0.3's `ReminderReceiver` and `TIMEZONE_CHANGED` handling are gone with reminders (D-014). `TIMEZONE_CHANGED` should return if a user-configurable notification time ever lands.

### 3.5 `domain.Timeline`

```kotlin
data class Day(val date: LocalDate, val entry: Entry)

object Timeline {
    fun days(newest: LocalDate, oldest: LocalDate, corpus: List<Entry>): List<Day>
    fun tagsOf(corpus: List<Entry>): List<String>
}
```

**This component stores nothing.** Selection is a pure function of the date, so running it backwards reconstructs the entire history — the statelessness chosen for picking tomorrow yields the whole past as a side effect. That was unplanned and is the reason [R5.3] is satisfiable at all.

The drift caveat in [R5.8] is a property of this design, not a defect in it: the permutation derives from corpus size, so a row for 3 September may show a different entry than was seen. Notes pin the days that matter.

### 3.6 `data.NoteRepository` and `data.NoteStore`

The only non-disposable data in the app ([R10.1]–[R10.6]).

```kotlin
object NoteRepository {
    fun all(context: Context): Map<LocalDate, Note>
    fun forDate(context: Context, date: LocalDate): Note?
    fun put(context: Context, date: LocalDate, entryId: String, text: String)  // + NoteBackup.enqueue
    fun backUpNow(context: Context)
    fun restore(context: Context, json: String?): RestoreResult              // NoteMerge, D-028
}
```

A note carries **both** `date` and `entryId`: the date is what it attaches to ([R10.1]), the entry id is what pins the row against drift ([R5.8]).

**Blank text deletes the note** rather than storing an empty one. An empty note is indistinguishable from no note to a reader, and keeping it would put a meaningless row under a timeline day.

`NoteStore` is the file underneath — see §4. `NoteRepository` is the boundary, which is what makes the storage choice reversible in one file.

### 3.7 `data.Prefs`

`SharedPreferences` over the "daily" file, sharing it with `CorpusSource`'s tree-URI key. Holds the notification hour and minute and the forecast override. **No user content** — notes are in `NoteStore`.

`AlarmScheduler` reads it rather than constants, so changing the time in Settings and rescheduling is one call.

### 3.8 `data.ForecastSource`

`forecast.json`, read through `CorpusSource.siblingJson` from the same picked folder as the corpus ([R9.2b]). Never throws; a missing file is the normal case and yields null, which degrades the ambient layer to time-of-day.

**`CorpusSource.syncedJson` was generalised to `siblingJson(name)`** to serve it. The forecast rides the pipe that already exists rather than getting a channel of its own — which is a privacy requirement, not tidiness (§10).

## 4. Data Design

### 4.1 Stores

**No database.** Five stores, four disposable:

| Store | Durability | On schema change |
|---|---|---|
| `corpus.json` in the synced folder | Disposable — regenerated by `publish.py` | Re-read |
| `assets/corpus.json` in the APK | Disposable — first-run snapshot and fallback | Rebuild |
| `forecast.json` in the synced folder | Disposable — written by the desktop | Re-read; absent is normal |
| `notes.json` in app-private storage | **Durable. The only unrecoverable data in the app.** | `VERSION` + `migrate()` |
| `daily-notes.json` in the chosen folder | The off-device copy (D-028), rewritten on every note write | Same format; read through `NoteStore.decode` |
| `SharedPreferences` | Disposable — time, override, folder URI | Defaults |

Corpus data is parsed on load and held in memory; at 27 entries and 186 KB that is free, and at 2,000 it is still well inside a phone's budget.

### 4.2 Notes are a JSON file, not Room — D-020

Room is Google's ORM over the SQLite that ships in Android, and it buys four things: **compile-time-checked SQL**, **`Flow` observability** that pushes on every write, **versioned migrations** with a `MigrationTestHelper`, and **query pushdown** into an indexed database.

None of the four applies here. **The design ceiling is 999 notes** — the user's stated realistic maximum. At a few hundred words each that is under a megabyte, read into memory in a single pass, with one writer, one process, no joins and no partial loads. Every "query" is a map lookup.

*(An earlier draft cited "~365 rows a year". That was the wrong figure: a note every day is an ideal rather than a floor or a cap, and what governs the design is the lifetime total.)*

The cost does apply. Room requires **KSP**, an annotation processor whose version must match the Kotlin release exactly — Kotlin 2.2.10 needs its specific KSP build, and every Kotlin upgrade needs a matching one. That is the recurring upgrade obligation [NFR5] exists to refuse, on an app meant to still build in five years unattended. Plus annotation processing in every build, a committed schema JSON, and four dependency lines.

**The migration argument was the real one, and it is answered in §4.3.**

**A side effect: it makes export unnecessary.** [R8.1] specified "a plain, human-readable format". The store *is* that format. *(Wrong about the location, and reversed by D-028: see §6.2.)*

**Trigger for revisiting:** passing 999 by a wide margin, notes needing search or sort in the database, concurrent writers, or paged loading. `NoteRepository` is the boundary, so the swap is one file.

### 4.3 Migration policy — the concentrated risk

```
{ "version": 1, "notes": [ { date, entryId, text, createdAt, updatedAt } ] }
```

`NoteStore.migrate()` moves a document forward one step per version bump. Two rules:

- **An unrecognised future version is read as-is, never discarded.** A newer file written by a newer build must not destroy the user's writing because an older build opened it.
- **A malformed row is skipped, not fatal.** One unparseable date must not take the rest of the file with it — asserted by test.

Writes go through a temp file and a rename, so a half-written `notes.json` — the one failure in this app that loses something unrecoverable — cannot happen.

[NFR8] concentrates schema risk here deliberately. It moved from `reminders` to `notes` without changing shape, and it is still about twenty lines.

### 4.4 Preferences

`SharedPreferences`: corpus folder tree URI (owned by `CorpusSource`), notification hour and minute, forecast override, flick navigation, and the backup's bookkeeping: last write time and count, and which folder this install has written to (the first-write guard). No user content.

## 5. Algorithm Design

### 5.1 `StableHash` — why not `hashCode()` or `Random(seed)`

Both are wrong here, for the same reason:

- `Random(seed).shuffled()` depends on Kotlin's PRNG implementation, which is not contractually frozen. A stdlib change would silently reshuffle every future day.
- `String.hashCode()` is specified and stable, but distributes short similar strings poorly — and entry ids are short similar strings by convention, so clustering is likely rather than hypothetical.

Since [R2.6] requires the same result in the publish script, the hash is **specified rather than borrowed**: FNV-1a 64-bit, ten lines, identical in Kotlin and Python.

`corpus/tools/fixtures_hash.json` is the contract between the two implementations, asserted by both suites.

### 5.2 Next-occurrence scheduling

Repeating alarms are not used. Each alarm is a **self-rescheduling one-shot**: on fire, the receiver computes the next occurrence and schedules it. More robust across reboots and DST than `setRepeating`.

Delivery uses `setAndAllowWhileIdle()`.

**Correction carried from D-011.** An earlier version of this document implied `setExactAndAllowWhileIdle` would improve timing under Doze. It does not — the documented ~9-minute per-app throttle applies to **both** calls. Only `setAlarmClock` escapes it, and that shows a system alarm icon and is a stronger claim on the device than a daily reading prompt deserves. [R3.5]'s "inexact is fine" is therefore not a concession; it is the whole of what is available short of `setAlarmClock`.

Per D-013 the acceptance bar is **delivery, not punctuality**: an entry arriving at 08:09 is the product working.

### 5.3 Corpus markdown parser (publish-side)

A line-oriented parser over one entry file ([SCHEMA](SCHEMA.md) §2). Deliberately not a markdown library.

```
frontmatter            → id, type, title, tagline, tags[], related[]
'## <heading>'         → begin a section
'**Thinkers:** A · B'  → section metadata, removed from the body
'> ...'                → a quote block; a final line starting '—' is its attribution
blank line             → flush the current block
otherwise              → accumulate a paragraph
```

Validation then enforces, per shape: every required section present and non-empty; a known type; unique, well-formed ids; no duplicate quote text across the corpus; and the word floor ([R6.3]).

`words` and `minutes` are computed here and never authored ([R6.8]).

**Ambiguity is resolved by failing, never by guessing.** Content before the first heading, an unknown type, or a missing required section is a validation error, and the build publishes nothing ([R6.4]).

### 5.4 The gear train **(§7.5)**

One scalar drives the whole dial. Given a section position `f ∈ [0,1]`:

```
drive        = f · 120°                       // a full sweep turns the main gear a third of a turn
satellite_i  = −drive · (N_main / N_i)        // meshing gears counter-rotate by tooth ratio
centre_i     = polar(centre, r_main + r_i, bearing_i)
```

Radii are proportional to tooth counts, so `r_main + r_i` places the centres exactly where real gears of those sizes would mesh. Tooth counts are 40, 16 and 10.

**The clock is deliberately inaccurate; the mechanism is not.** That inversion is what makes it read as an object rather than an animation, and it costs one number in and everything else derived ([DESIGN](DESIGN.md) §2).

---

## 6. Interface Design

### 6.1 Corpus read

**Replaces v0.3's conditional GET entirely.** There is no request, no ETag, no timeout.

| Property | Value |
|---|---|
| Source | `corpus.json` in a user-picked folder, kept current by an existing folder sync |
| Access | SAF tree URI, persisted; `DocumentsContract` child lookup by name |
| Success | Parse; if the result is non-empty, use it |
| Missing file / revoked grant / malformed JSON / empty result | Silent fall back to the bundled asset ([R7.2]) |
| Staleness | Surfaced in the UI as version + entry count + source ([R7.6]), never as an error |

**`corpus.json` lives in the vault beside the markdown it was generated from**, so the existing sync job carries it with no new configuration. `publish.py` also accepts extra destination paths for a narrower share, which is unnecessary in the current setup.

### 6.2 Export — built in v1.8 (D-028)

v0.3 designed a `SafExportSink`. D-020 made it unnecessary: `notes.json` is already the plain, human-readable JSON [R8.1] asked for, and it is one file in app-private storage.

The v0.3 design note is still worth keeping for whenever the file has to *leave* the app: `getExternalFilesDir()` is **not** usable, because `/Android/data/<pkg>/` is unreadable by other apps on modern Android, so the sync client could not see it. The SAF mechanism in §6.1 is the way.

**Corrected 2026-10-10 (D-027): this section was wrong, and the reason it gave is the reason it was wrong.** "One file in app-private storage" is exactly what stops `notes.json` serving as [R8.1]'s export: [R8.2] requires the export to sit where the sync client can see it, and app-private storage is the one place it cannot. The requirement was marked satisfied by a file that fails it. Until 2026-10-10 the only off-device copy was Google Auto Backup, there by template default and against D-003. D-027 excluded notes from it (`res/xml/data_extraction_rules.xml`). **Notes now have no off-device copy at all.** The fix is the one this section already describes: write `notes.json` through the §6.1 tree URI into the synced folder, on the same debounce as the store, and read it back for [R8.5] restore. §13 item 15.

**Built the same day (v1.8, D-028).** `data.NoteBackup` writes `daily-notes.json` into the chosen folder:

- **When:** every `NoteRepository.put` enqueues a snapshot of the whole map, and choosing a folder enqueues one too.
- **How:** a single daemon thread, latest-wins, so a burst of debounced saves is one write. It uses `createDocument` the first time, then `openOutputStream(doc, "wt")`, because some providers do not truncate on `"w"`.
- **Not atomic, and doesn't need to be:** `notes.json` is untouched, so a torn copy is repaired by the next save.

**Restore** is `NoteRepository.restore`, over the pure `domain.NoteMerge`: per day, the newest edit wins, and nothing is ever deleted. It runs from a Settings button, and automatically when a folder is chosen. That automatic run is what makes a reinstall safe: the folder's copy is merged in before anything can write over it.

**The first-write guard** protects the case automation can't. Until this install has written to *this* folder once, a write that `NoteMerge.wouldLose` anything is refused, and Settings says "Restore first". After the first write, the phone is the truth and deletions propagate.

**The permission was the hidden cost.** `CorpusSource.saveTree` had only ever persisted *read*. It now persists read+write, falling back to read alone if the provider refuses write. Every existing install must choose its folder again once before the backup can write. Settings reports this as its own state (`BackupStatus.ReadOnly`) rather than as a generic failure.

### 6.3 Notification intents

One `PendingIntent` with `FLAG_IMMUTABLE`, targeting `MainActivity`. One channel, `CHANNEL_DAILY`.

Content: title, tagline, and `BigTextStyle` carrying tagline + hook. **No reading time** ([R3.9], [P6]).

**Open:** [R3.2] requires the notification to land on the entry rather than the homepage. It currently opens `MainActivity`, which starts on Today — correct in effect today, but it needs an intent extra naming the destination before Home could ever become the start screen (§13).

### 6.4 Forecast

`forecast.json` beside `corpus.json`, read through `CorpusSource.siblingJson`, several days ahead. The phone makes no request and asks for no location ([R9.2b], [R9.2c]).

```json
{ "days": [ { "date": "2026-09-14", "condition": "rain", "high": 31, "low": 26 } ] }
```

An unknown condition falls back to `CLEAR` rather than failing; a missing file yields null, which degrades the ambient layer to time-of-day alone.

---

## 7. UI Design

### 7.1 Screens

| Screen | Contents | Requirements |
|---|---|---|
| **Home** | Forecast rendered as sky — gradient by time of day, cloud mass, static rain | [R9.1], [R9.2] |
| **Today** | The day's entry, then the note editor | [R3.3], [R10.2] |
| **Timeline** | Tag filter, then days newest-first with month headers and note previews | [R5.1], [R5.2], [R5.7] |
| **Reader** | One past day — its entry and its note | [R5.1], [R10.2] |
| **Settings** | Notification time, forecast override, flick-to-turn, corpus folder and diagnostics, notes backup and restore | [R9.2d], [R7.6], [R8.5] |

Navigation is a sealed `Screen` type and a `when` — four destinations do not justify a navigation library ([NFR5]). `Reader` is not on the dial's arc; it inherits the timeline's position.

**`Reader` carries a `Day`, not an `Entry`.** A note attaches to a day ([R10.1]), and the same entry returning in 27 days is a different day with its own note.

**Home is barred from the morning path** ([R3.2]). Opening the app cold lands on Today; the notification will deep-link to the entry directly.

### 7.2 State

State lives in **`ui.DailyState`**, a `@Stable` class holding `screen`, `corpus`, `folder`, `notes`, `hour`, `minute`, `forecastOverride` and `forecast` as `mutableStateOf` properties with private setters. `MainActivity` remembers one instance and reads it. There are still no ViewModels.

**It owns the data and the changes, not the clock.** Time-of-day is passed in to `ambientAt()` and `forecastLineAt()` rather than read inside, so the sky still follows recomposition exactly as it did when the logic was inline — and the class stays testable without anyone having to fake a clock.

**Having no ViewModel remains a deliberate deferral.** Nothing survives process death that matters: the corpus re-reads in milliseconds, notes are written through on a debounce, and the selected screen is not worth restoring. What `DailyState` buys is not persistence across process death but a seam: a name to hold the state, and a constructor to hand it a fake. See §14.2.

Because selection is pure, the entry recomputes when either the corpus or the date changes — including the app being left open across midnight.

### 7.3 Entry rendering

`EntryView` walks `entry.sections` in order and dispatches each block by kind.

**Styling is driven by block kind, never by matching heading text** ([R3.10]). Headings are authored freely — `Idea Two — The Cold Monster` is a real one — so any match on heading strings breaks the first time an entry is written slightly differently. An unknown block kind is skipped rather than fatal ([R3.11]).

Quotes render with a left rule and their attribution beneath; `meta.thinkers` renders as a separated line under the heading.

### 7.7 The note editor

An `OutlinedTextField` beneath the entry, saving on a **700 ms debounce** rather than a button. A save button is one more thing between a thought and writing it down, and there is nothing here to submit to.

Keyed on the date, so navigating between days replaces the buffer rather than carrying the previous day's text into it.

**One note per day, appended to** ([R10.7]). Returning to a day loads what is already there and puts the cursor after it — the append behaviour is the editor's default rather than a feature, which is why it needed recording as a decision rather than left as an implementation detail.

---

**v0.3's §7.3 markdown renderer is not built and should not be.** The publish step now emits structured blocks, so the client has no markdown to parse — the ~60-line parser it proposed was made unnecessary by moving the work to publish time, which is the better place for it.

### 7.4 Timeline rendering

`LazyColumn` over a pre-flattened `MonthHeader | DayRow` list. Flattened deliberately: `stickyHeader` requires an `@OptIn` on an experimental API, and experimental annotations are a poor trade for a header that scrolls.

Tag filter is a horizontally scrolling row; tapping the active tag clears it.

### 7.5 The dial

Per [DESIGN](DESIGN.md) §2 and [R11.4]–[R11.7].

- **Half a circle**, centre exactly on the bottom edge, so precisely the upper half shows.
- **Transparent ground.** Each gear is one `Path` with `PathFillType.EvenOdd`, so the bore and lightening holes are genuinely transparent — the page shows through the metal. This is the reason the dial can sit in front of content rather than taking layout from it.
- **Two hands.** Fast = section, following the swipe live. Slow = position in the corpus rotation, one step per day. Tick marks are the corpus, one per entry.
- **Gesture: an ordinary horizontal swipe**, tracked live into the dial so the gears turn with the finger, settling on release past 18% of screen width. Horizontal so it never argues with the vertical scroll of an 800-word entry; the vertical axis is reserved for pulling Home up later ([R11.7]).
- **Retreat while reading** ([R11.6]): the band's height animates to zero once the reading surface scrolls past a threshold. **Scroll rather than a timer** — a timer would hide it while the user sits still thinking, which is the opposite of the intent.

### 7.6 Theme

`DailyTheme` provides a hand-picked light and dark scheme, the serif typography, and a `LocalBrass` composition local carrying the three metal stops the Material scheme has no slot for.

**Dynamic colour is off** ([R11.1]). It derived the accent from the wallpaper, which cannot coexist with a chosen palette, and it was an Android Studio default rather than a decision.

`enableEdgeToEdge()` is called explicitly. targetSdk 37 draws edge-to-edge regardless; calling it is what flips the status-bar icons to contrast with the app's own background ([R11.3]).

---

## 8. Publish Pipeline Design

### 8.1 Repository layout

The authoring notes are **not** in this repository; `corpus/` (below) lives with them. The Android project is its own repository, **public since 2026-10-10** at `github.com/Christian-H8/Daily` (D-027). The public repo carries only the corpus snapshot bundled in `app/src/main/assets/corpus.json`; the authoring tools (`publish.py`, `forecast.py`) stay in the vault. `forecast.py` hard-codes the forecast location, so it needs that parameterised before it could ever move into the repo.

```
corpus/
├── entries/<id>.md              # one file per entry
├── corpus.json                  # the published artefact, in the synced vault
└── tools/
    ├── publish.py               # parse → validate → emit
    └── fixtures_hash.json       # the contract with the Kotlin StableHash test
```

**The design docs are published too, as cleaned copies.** `tools/publish_docs.py <repo>/docs` copies SRS, SDS, DECISIONS, ARCHITECTURE, DESIGN and SCHEMA into the repository's `docs/`. It rewrites the passages that carry personal context and converts wikilinks to relative links. It fails, writing nothing, if a rewrite rule no longer matches its source or a forbidden term survives. These originals stay the source of truth: edit here, then rerun it.

### 8.2 Pipeline stages

```
write an entry → publish.py → parse all entries → validate → emit corpus.json
                                     │
                                     └── any error → exit 1, publish nothing
```

Validation rules are [R6.3] verbatim. The build fails rather than warns: a warning that can be ignored is not a guard ([R6.4]).

**No CI.** v0.3 proposed a GitHub Action because publication meant an upload. With D-010 there is nothing to upload — the script writes into a folder that is already synced, and a CI job would only add a place for it to break.

### 8.3 Hosting

**Deleted.** There is none, and there is no plan for one. [NFR6] is satisfied by construction.

The one thing hosted anywhere is the APK, as a GitHub Release asset (§8.4). It is a static file served by GitHub, not a service this project runs.

### 8.4 APK release pipeline — added in v1.7 (D-027)

```
git tag v1.2.3 → push → .github/workflows/release.yml
    resolve version (tag minus "v"; MAJOR.MINOR.PATCH, minor and patch < 100)
    → decode keystore from secrets   ── secret missing → fail, publish nothing
    → testDebugUnitTest assembleRelease   (DAILY_VERSION, DAILY_KEYSTORE_* in env)
    → apksigner verify --print-certs   (logs the certificate SHA-256)
    → daily-v1.2.3.apk + SHA256SUMS.txt → GitHub Release (+ workflow artifact)
```

- **The signing config exists only on the runner.** `app/build.gradle.kts` creates `signingConfigs.release` only when `DAILY_KEYSTORE_FILE` is set; otherwise `release` falls back to the debug config. Because the secret *is* the author's debug keystore, both paths produce the same signature.
- **The failure the pipeline exists to prevent** is a silent one: a runner without the secret would sign with its own throwaway debug key, and the published APK would refuse to install over any existing copy. The user's way out of that error is an uninstall, which deletes their notes. The workflow therefore fails rather than falls back.
- **`--no-configuration-cache` in CI.** The signing passwords are read at configuration time, and the configuration cache would serialise them to disk.
- **`versionCode` = MAJOR·10000 + MINOR·100 + PATCH**, computed in the build script from `DAILY_VERSION`, so it can only rise as tags do. Local builds keep 1.0 / 1. Every release tag is ≥ v1.0.0 → 10000, above the soak install's 1.
- **Manual runs** (`workflow_dispatch`) build and upload an artifact without publishing a Release, which makes a pipeline change testable without a tag.
- **Unit tests run as `testDebugUnitTest`.** AGP 9 creates unit-test tasks only for the tested build type by default, so `testReleaseUnitTest` does not exist, which the first CI run found. The code under test is identical across variants.
- **No `setup-android` step, and the runner is pinned to `ubuntu-24.04`.** GitHub's Ubuntu images ship the Android SDK with licences accepted, and AGP fetches the missing platform. The extra action failed on the first run and added nothing. Pinning stops a silent image upgrade (the `ubuntu-latest` label moved to Ubuntu 26 in October 2026) from changing what is preinstalled.

---

## 9. Error Handling and Degradation

The governing principle: **a failure in a secondary system must never prevent the day's entry from appearing.**

| Failure | Behaviour | Req |
|---|---|---|
| No folder chosen yet | Render the bundled corpus; the footer says "bundled copy" | [R1.1], [R7.6] |
| Folder grant revoked | Silent fall back to the bundled copy | [R7.2] |
| `corpus.json` missing or mid-write | Silent fall back; never partially apply | [R7.2] |
| Malformed JSON | Discard entirely, keep the bundled copy | [R6.4] |
| Unknown block kind | Skip that block, render the rest | [R3.11] |
| Corpus empty | "No entries yet". Unreachable given [R1.1] | [R2.7] |
| Notification permission denied | App fully usable on open | [R3.1] |
| Exact-alarm permission denied | Irrelevant — inexact is the only path (§5.2) | [R3.5] |
| `notes.json` unreadable or malformed | Falls back to empty rather than crashing; a malformed *row* is skipped and the rest of the file kept | [R10.5] |
| `notes.json` written by a newer version | Read as-is, never discarded — an older build must not destroy newer writing | [R10.5] |
| `forecast.json` missing | Null condition; the ambient layer degrades to time-of-day | [R9.2] |
| Folder chosen before D-028 (read-only grant) | Backup skipped; Settings says to choose the folder again | [R8.2] |
| Backup write fails | Settings shows "Backup failed: …"; `notes.json` is untouched | [R8.1] |
| Folder's copy has notes this install lacks | First write refused; Settings says "Restore first" | [R8.5] |
| Folder's copy unreadable on restore | "Nothing was changed"; no backup is written over it | [R8.5] |
| Device clock moved backwards | Selection is a pure function of the date; an earlier date yields that date's entry. No corruption possible. | [R2.1] |

That last row is worth noticing: because nothing is stored about selection, whole categories of state-corruption bug cannot exist.

---

## 10. The ambient layer

```kotlin
enum class Condition { CLEAR, CLOUD, RAIN, STORM, FOG, SNOW }
enum class PartOfDay { DAWN, DAY, DUSK, NIGHT }
data class AmbientState(val partOfDay: PartOfDay, val condition: Condition?)
```

`partOfDay` comes from the device clock — no network, no permission. `condition` comes from `forecast.json` in the synced folder, or from the Settings override, and is **nullable** so a missing forecast degrades to time-of-day rather than failing.

**Reading it is the theming layer's job, never the entry renderer's** ([R9.3]). `EntryView` takes an `Entry` and nothing else — no date, no forecast, no theme state beyond `MaterialTheme`. That one-way dependency is what keeps a weather-reactive palette an additive change rather than a rewrite.

**Why the forecast rides the sync, restated because it is the important part.** A weather API would mean a network call and a location permission — and the network is the exact condition under which journaling was excluded (D-003). Notes only exist because D-010 removed it. **Notes and live weather cannot coexist, and notes win.** The forecast being *predetermined* is what makes that resolvable at all: a forecast written last night is still right this morning, where an observation would be wrong.

**Deliberately unanimated.** [R9.4] — falling rain and lightning are the most expensive piece and the most likely to look cheap. `HomeScreen` draws sky, cloud mass and static rain, and stops there.

## 11. Traceability

| Requirement | Design element |
|---|---|
| [R1.1] bundled corpus | `assets/corpus.json`; §3.3 fallback path |
| [R1.2] today is the landing screen | §7.1 — `Screen.Today` is the initial state |
| [R1.3] destinations | §7.1 sealed `Screen` + `when`, no nav library |
| [R1.4] no account | No auth component exists |
| [R1.5] minimal permissions | §6.1 SAF grant instead of storage permission; no `INTERNET` |
| [R2.1]–[R2.5], [R2.7], [R2.8], [R2.10] selection | §3.1 `DailySelector`, §5.1 `StableHash` |
| [R2.6] off-device reproducibility | §5.1 `fixtures_hash.json`, asserted by both suites |
| [R2.9] local timezone | `LocalDate.now()` resolves in the device zone |
| [R3.1], [R3.2] daily notification | §6.3 |
| [R3.3], [R3.4] entry presentation | §7.3 `EntryView` |
| [R3.5] inexact alarms | §5.2, with the D-011 correction |
| [R3.6] reboot survival | §3.4 `BootReceiver` |
| [R3.8] no engagement messaging | §6.3 — one channel; nothing else emits |
| [R3.9] notification content | §6.3 `bigTextFor` |
| [R3.10], [R3.11] kind-driven rendering | §7.3 |
| [R5.1]–[R5.3], [R5.8] timeline | §3.5 `Timeline`, §7.4 |
| [R5.4] no usage values | No activity source exists to read from |
| [R5.6] no search, no graph | §7.4 — neither is implemented, by requirement |
| [R6.1]–[R6.9] authoring and publish | §5.3, §8.1, §8.2 |
| [R7.1], [R7.7] synced-folder read | §3.2 `CorpusSource`, persisted tree URI |
| [R7.2] silent degradation | §3.3, §9 |
| [R7.3] no network | §2.4 — no HTTP dependency, no `INTERNET` |
| [R7.4] disposable corpus | §4.1 — held in memory, re-read |
| [R7.6] staleness visible | §3.3 `LoadedCorpus`, §7.1 footer |
| [R8.x] export | §6.2 — built in v1.8 (D-028): `NoteBackup` writes `daily-notes.json` into the chosen folder; `NoteRepository.restore` + `NoteMerge` for [R8.5]. v1.7 had it **unmet** |
| [R9.1]–[R9.3] ambient | §3.8 `ForecastSource`, §6.4, §10 |
| [R9.2d] forecast override | §3.7 `Prefs`, §7.1 Settings |
| [R9.4] rendered weather | Deferred — §10 |
| [R10.x] notes | §3.6 `NoteRepository`, §4.2 the storage choice, §4.3 migration, §7.7 the editor |
| [R11.1]–[R11.3] palette, type, edge-to-edge | §7.6 |
| [R11.4]–[R11.7] the dial | §5.4, §7.5 |
| [R11.8] no durations | §6.3, §7.3 — no element renders one |
| [R12.1] audio *(deferred)* | Not designed |
| [NFR1] notification reliability | §5.2; acceptance is on-device over four days (§12) |
| [NFR2] offline-first | §6.1 — there is nothing to be offline from |
| [NFR3] no outbound data | §2.4 — verifiable in the manifest |
| [NFR4] corpus never dead-ends | §3.1 — cycling is intrinsic, not a fallback |
| [NFR5] longevity | §2.3, §2.4 — four dependencies total |
| [NFR6] cost | §8.3 — none |
| [NFR7] morning legibility | §7.3, §7.6, and §7.5's retreat behaviour |
| [NFR8] concentrated schema risk | §4.3 |
| [NFR9] authoring friction | §8.2 — write the file, run the script |
| [NFR10] shape variety | Not a code requirement; enforced by the content backlog sequencing |

---

## 12. Test Strategy

| Layer | Approach | Status |
|---|---|---|
| `DailySelector` | Stability within a day; **every window of n consecutive days is a complete permutation** (the property D-007 exists to guarantee, and the test that caught the original design); pre-epoch dates; empty corpus | **6 tests, passing** |
| `Timeline` | Ordering and range; inclusive endpoints; inverted range; empty corpus; tag dedup; **and that `Timeline.days` agrees with `DailySelector.entryFor` on every day back to EPOCH** | **7 tests, passing** |
| `CorpusLoader.parse` | Sections and blocks; quote attribution; section `meta.thinkers`; **an unknown block kind is skipped, not fatal**; absent optional fields | **7 tests, passing** |
| `NoteStore` | Round trip; the version is written; a malformed date is skipped without losing the file; missing fields; unicode and quotes | **6 tests, passing** |
| Cross-implementation | `fixtures_hash.json` asserted by both Kotlin and Python | **passing** |
| Publish parser | Python tests over golden entry files, including every failure in [R6.3] | Manual negative tests run; not automated |
| `DailyState` | Construction reads the corpus and forecast exactly once; **`writeNote` takes its map back from the store rather than patching it in memory**, proved by a fake whose `putNote` drops the write; `overrideForecast` and `setTime` reach the environment, which is what reschedules the alarm; an override beats the forecast file and Auto gives it back; back-from-reader returns to the timeline; a swipe past either end stays on the arc | **18 tests, passing** |
| `NoteMerge` | Adds missing days; the later edit wins a shared day; a tie keeps the phone's note; **never deletes a note the copy lacks**; a blank incoming note is no note; `wouldLose` counts exactly what a merge would bring in, is zero once the phone has everything, and is the whole copy for an empty phone (the reinstall case) | **8 tests, passing** |
| `BackupStatus` | The Settings wording: a saved copy says when and how many; singular; a refused first write says why and what to do; read-only and no-folder are told apart; restore distinguishes nothing-new from an empty copy | **5 tests, passing** |
| UI (screens) | Minimal; the screens are nearly stateless and Compose-level tests remain unwritten (§14.7) | Not written |
| [NFR1] | **Manual, on the real device, over four days including a reboot and a battery-saver cycle, on one uninterrupted install** | **Not started — and now unblocked.** The `.dev` variant means the release install can sit untouched while work continues (§13). This is the acceptance gate for the whole project. |

| `Derived` | `passDay` counts from one and agrees with the selector over every corpus size 1…40; the dial's hand never leaves 0…1; **the latest note is the latest date, not the latest edit**; all four branches of the forecast diagnostic, in the order the chain breaks | **16 tests, passing** |

**109 `@Test` methods on disk as of 2026-09-15** (108 real, plus the template `ExampleUnitTest`). **The last count actually observed green was 77**, on 2026-09-14; `Inline` (15), `FlickDetector` (14) and two added `DailyState` cases have not been run here. The distinction is kept deliberately — a count taken by reading files is not a count taken off a green bar, and conflating the two is exactly what §14.9 exists about.

**124 unit tests, observed passing on 2026-10-10.** That is every JVM test file except the template `ExampleUnitTest`, and includes the 13 above plus three new `DailyState` cases for restore and the backup line. **How it was run matters:** not through Gradle, which could not reach Google Maven from where the work was done, but with `kotlinc` 2.2.10 against a stub `android.jar` (API 33), a minimal Compose-runtime shim for `DailyState`, and `org.json` 20240303 (the version `testImplementation` pins). So it proves the code compiles and the tests pass. It does not prove the Gradle build is green. The first CI run (§8.4) is the check that does.

**77 unit tests, observed passing on 2026-09-14.** The figure is stated that way on purpose: the "59" this line previously carried was never observed, because the tree it described did not compile (§14.1, D-023). This one was read off a green run.

The 18 `DailyState` cases are new in v1.3 and are what §14.2 was missing. The extraction created a seam; until these, nothing had been put through it — which is precisely how a `DailyState.kt` that could not compile sat on disk unnoticed. The class is now on the test classpath, so that particular failure is a red bar within seconds rather than a build error days later.

The 16 `Derived` cases arrived in v1.2 for the same reason one level down: the values Home prints and Settings diagnoses used to be reachable only by installing the APK and reading the screen, which is how *no weather* stayed three problems wearing one face for as long as it did.

**Compose state needs no test harness.** `DailyState`'s eight `mutableStateOf` properties construct and mutate under plain JUnit — the snapshot system is pure JVM and touches no `Looper` — so no Robolectric, and no `unitTests.isReturnDefaultValues`. Worth recording against [NFR5]: testing the composition layer's state cost zero dependencies.

The two that matter most: the `Timeline`-vs-`DailySelector` agreement test, because two implementations of one algorithm is the D-007 failure shape; and `CorpusLoaderTest`'s unknown-block-kind case, because that property is what lets the corpus ship ahead of the client.

## 13. Open Items Carried Forward

1. **(DONE)** ~~A dev build variant~~ — `applicationIdSuffix = ".dev"` on debug. Two packages, two alarms, two battery exemptions. **The four-day soak can now run on an untouched release install while work continues on `.dev`** — D-012's conflict is removed rather than scheduled around.
2. **(DONE)** ~~`testImplementation("org.json:json")`~~ — added; `CorpusLoader.parse` and `NoteStore` are now tested off-device.
3. **(DONE)** ~~Package naming~~ — `domain`, and both files moved to match their package (§2.2, 2026-10-10).
4. **(DONE)** ~~Whether the repo is meant to be read~~ — public, with a README, licences and a release pipeline (D-027, §8.4).
5. **(OPEN)** The notification does not yet deep-link to the entry ([R3.2]) — it opens `MainActivity`, which lands on Today. Correct today because Today *is* the entry; it needs an intent extra once Home could ever be the start destination.
6. **(DEFERRED)** Rendered weather animation [R9.4]; audio [R12.1].
7. **(DONE → D-028)** Export. D-020 called `notes.json` the export, but it is app-private, so it was not one (§6.2, corrected v1.7). Built in v1.8.
8. **(OPEN)** The technical debt in §14 that was recorded rather than fixed: `NoteRepository`'s hidden global (§14.3), main-thread file I/O (§14.4), `CorpusSource`'s two jobs (§14.5), and the ambient boundary being thinner than [R9.3] claims (§14.6). Each carries the condition that should trigger it.
9. **(OPEN)** `rememberNow()` (D-023) fixes a stale clock that showed **yesterday's entry past midnight**, on the morning path. It compiles; the rollover has not been observed on the device. Unverified is not fixed.
10. **(DONE)** ~~`DailyState` has no test~~ — `DailyStateTest`, 18 cases, green on 2026-09-14. The seam §14.2 built now has something through it, and the class is on the test classpath, which is the part that would have caught D-023's clash.
11. **(OPEN — correctness, not debt)** **A note's `entryId` is discarded before it reaches the timeline**, so a past day renders whatever the *current* corpus computes for that date rather than what was actually read. §8 specifies the opposite. See §14.11.
12. **(OPEN)** The moon should sit below the date and above the day's entry on Home, rather than wherever the arc puts it. Raised 2026-09-15, not yet looked at.
13. **(OPEN)** The folder sync is now load-bearing. D-010 removed the backend by putting the corpus and the forecast on a sync the phone already ran — which moved the single point of failure to a third-party app that can pause itself quietly, as it did on 2026-09-13. **It needs the same battery-optimisation exemption the Daily App needs**, and it is not covered by [NFR1]'s soak because the soak only watches this app.
14. **(OPEN)** **`AlarmReceiver` is a second composition root, on the morning path.** It reaches `CorpusLoader` and `DailySelector` directly rather than through `DailyEnv`, and `bigTextFor` — which decides what the notification quotes — is untested. It is the one place the dependency rule that holds everywhere else is not applied, and it runs at 7am when nobody is watching. Raised by §14.12's DIP pass, 2026-09-15.
15. **(BUILT, not yet observed on the device)** ~~Notes have no off-device copy~~ — D-028, §6.2. What remains is seeing `daily-notes.json` appear in the folder, and seeing a restore on a fresh install bring the notes back. Until then, treat it as an assumption. *Original item:* **Notes have no off-device copy.** §6.2's correction: `notes.json` is app-private, cloud backup is now excluded (D-027), and [R8.x] export was never built. A lost or broken phone loses every note. Fix: write through the §6.1 tree URI into the synced folder, plus [R8.5] restore. This is also what would make any future signing-key change survivable without key rotation.
16. **(OPEN)** **The bundled corpus is a snapshot, and it goes stale.** `assets/corpus.json` held 27 entries on 2026-10-10 while the vault held 41. For the author this does not matter, since the synced folder wins. For anyone installing from Releases, the bundled copy is the whole corpus. Refresh it from `publish.py`'s output before each tag.

## 14. Technical Debt

**Recorded 2026-09-14, from measurement rather than impression.** The architecture was evaluated against SOLID and GRASP; this is what the count found, what was fixed the same day, and what was left alone on purpose with the condition that should change that. It lives here rather than in [DECISIONS](DECISIONS.md) because none of it is a decision — it is the distance between what §2.1 claims and what the code does.

### 14.1 The measurement

| Layer | Lines | Test cases | Verdict |
|---|---|---|---|
| `domain` | ~246 | 43 | A− |
| `data` | 458 | 13, across two of six files | B− |
| `ui` + `MainActivity` | ~1,718 | 0 | D |

**The bottom of the architecture is excellent, the middle adequate, the top was absent.** The dependency rule is real and checkable, not asserted: `domain` imports nothing from Android, Compose or the network. The port boundary has been cashed in twice — D-010 turned an HTTP client into a SAF reader, D-020 turned Room into a JSON file — each touching one class, which is the whole return on drawing it. Statelessness paid four separate times: no pick table, no corruption state to get wrong, an off-device preview in Python, and the timeline for free.

**87% of the code had no test seam**, and all of it was the part the user actually looks at.

> **Correction, 2026-09-14 (D-023).** The test counts above were not observable against the code they were recorded for. `DailyState.kt` carried a platform declaration clash — `var forecastOverride` with a `private set` and `fun setForecastOverride` compile to the same JVM signature — so `compileDebugKotlin` failed and `gradlew test` never reached a test. The figures were true of an earlier state of the file. **The `0` in the third row is the number that survived.** It is no longer 0: `DailyStateTest` (18 cases, green 2026-09-14) put the class on the test classpath, and the observed project total is 77. See §12 and §14.9.

### 14.2 `MainActivity` was a God object — **fixed**

424 lines, grown from about 180 in a single day, doing four jobs at once: Android lifecycle, navigation, data loading, and every derived value the screens print. As a GRASP Controller it was bloated; under SRP it had four reasons to change. The reason it had no test was not that testing it was hard in principle but that every piece of state lived in a `remember` inside `setContent`, where nothing outside a running phone could reach it.

Split four ways:

| New | Layer | What moved into it |
|---|---|---|
| `domain.Derived` | pure | `passDay`, `cycleFraction`, `lastNote`, the four-branch forecast diagnostic |
| `ui.DailyState` | composition | `screen`, `corpus`, `folder`, `notes`, `hour`, `minute`, `forecastOverride`, `forecast`, and every change to them |
| `data.DailyEnv` / `AndroidEnv` | port | the five singletons the composition used to reach into directly |
| `ui.EntryPage`, `ui.NavBar` | view | Today and Reader were the same page written twice; the nav bar now reads `SECTIONS` |

`MainActivity` is 271 lines, of which ~55 are imports and ~15 a doc comment. It holds the permission request, the notification channel, the alarm, and the wiring — which is what an Activity is for. `Derived` arrived with 16 tests (§12).

**Nothing about the app's behaviour changed.** Every field was already a `var … by remember`; every method was already a lambda. They have only stopped being anonymous.

### 14.3 `NoteRepository` is hidden global state — **open**

An `object` with a `loaded` flag and a cached map: process-wide mutable state with no owner, and a DIP violation for every caller that reached it. It is the change D-020 introduced, and it is what silently falsified §2.3.

**Half-fixed.** `DailyState` no longer knows it exists — it goes through `DailyEnv`, so a test supplies a map without touching a file. The singleton itself remains.

**Why it was not finished.** Making it a class needs an owner whose lifetime outlives the composition, because the cache is precisely what stops the notes file being re-read every time the Activity is recreated. That owner is the `Application`, and an `AppContainer` is the thing §2.3 has twice declined to build for a handful of objects. **Trigger to revisit:** a second consumer of notes outside the composition — a widget, or the notification builder wanting to show whether the day was written on.

### 14.4 File I/O on the main thread — **open, and deliberately**

Every read and write is synchronous on the main thread: `CorpusLoader.load` over SAF, `ForecastSource.load` on each hour change, `NoteStore.write` on every debounce. `corpus.json` is 186 KB today.

Nothing has dropped a frame, but *yet* is doing the work in that sentence, and SAF reads are not fast. **Trigger:** a corpus past roughly a megabyte, or a note write that visibly stutters the editor. **The fix is now cheap and was not before** — `withContext(Dispatchers.IO)` inside `AndroidEnv`, behind an interface nothing else has to learn about.

### 14.5 `CorpusSource` does two jobs — **open**

It owns the SAF tree URI *and* serves arbitrary files out of that folder (`siblingJson`). Those are folder ownership and file serving, and the second exists only because `ForecastSource` needed to reach through the first. Low cohesion, and the reach-through is the symptom.

Not split because the split is an interface and two classes for an 84-line file. **Trigger:** a third reader of the synced folder.

### 14.6 The ambient boundary is thinner than [R9.3] claims — **open**

[R9.3] describes the ambient layer as a boundary. In practice `skyColors()` — the policy mapping condition and part-of-day onto two colours, including how far each condition dims the sky — is a private function inside `HomeScreen.kt`. That is view code holding policy.

It is also the only consumer, so nothing is actually wrong today; what is wrong is that the requirement and the code describe different structures. Recorded so they at least disagree on paper. **Trigger:** a second surface wanting the same palette — a widget, or the notification.

### 14.7 Deliberately not done

- **ViewModels.** §7.2 stands: nothing survives process death that is worth restoring. What was missing was a seam, and a seam is not a ViewModel.
- **Hilt or any DI framework.** One interface, constructed once.
- **UI tests.** `DailyState` is now constructible with a fake `DailyEnv`, so screen-level state can be tested. None is written; the screens remain nearly stateless and [NFR1] — four real days on a real phone — is the gate that actually matters.
- ~~**The `domain` vs `domain` rename**~~ — done 2026-10-10 (§2.2, §13.3).

### 14.8 The process finding

The most useful thing the measurement turned up was not in the code. **§2.3 was wrong for three days and no one noticed**, because a design document is only checked against reality when someone goes looking. The code was measured this time only because the question was asked directly. A document that describes code cannot be trusted to have aged well; it has to be re-measured, and this section is the first entry in that habit.


### 14.9 The measurement was itself unmeasured

**Recorded 2026-09-14, one day after §14.8.** §14.8 concluded that a document describing code cannot be trusted to have aged well, and that re-measuring has to be scheduled rather than assumed. It aged badly in a day: the section stating that lesson reports a test count taken against a tree that does not build (§14.1's correction, D-023).

Two structural things follow, neither of them about the clash itself.

**A seam is not coverage.** §14.2 presents the `DailyState` / `Derived` / `DailyEnv` extraction as the fix for the composition layer's D grade. What it actually delivered is the *possibility* of a test. `Derived` got 16; `DailyState` got none. The grade §14.2 implies has not been earned, and the honest entry for `ui` + composition is still D with a seam, not C.

**The cheapest check was also the one that would have caught this.** Any `DailyStateTest` — `overrideForecast` writing through to the env, `writeNote` re-reading the map from the store, `setTime` — puts `DailyState.kt` on the test classpath. A file that does not compile then fails as a red bar in the same minute, rather than as a build error three days later when somebody finally tries to run it.

**Trigger:** none. This one is not conditional. It is the smallest test file in the project and it closes the gap that produced two of the last three findings.

**Closed the same day.** `DailyStateTest` — 18 cases, green. The `ui` + composition row is no longer untested at its state-holding centre, though the screens themselves still are, so the honest grade is C, not the A− that `domain` earns. What the writing of it turned up, and what is worth keeping: the composition layer needed **no test dependency at all**. Compose's snapshot state runs under plain JUnit, so the reason this code went untested for its whole life was never cost. It was that nothing had a seam, and after §14.2 gave it one, nobody used it. **A seam is only worth what is put through it, and the interval between building one and using it is exactly where this class of defect lives.**

### 14.10 Second measurement, 2026-09-15

**Taken because it was due, not because something broke** — which is the habit §14.8 asked for. Code lines only; comments and KDoc excluded, so these are not comparable line-for-line with §14.1, which counted differently.

| Layer | Files | Code lines | Share | Tests | Verdict |
|---|---|---|---|---|---|
| `domain` | 9 | 229 | 10% | 66 | **A** |
| `data` | 7 | 353 | 15% | 22 | B |
| `ui` | 13 | **1,481** | **63%** | 20 | C− |
| app (Activity, alarm) | 3 | 288 | 12% | 0 | C |

**`domain` has got better and smaller at the same time**, which is the finding worth having. It gained two whole features this week — `FlickDetector` and `Inline` — and is still only a tenth of the codebase, with 66 of the tests. Both new files went in pure-first and neither needed a device to develop. The dependency rule has not been violated once; `domain` still imports nothing from Android, Compose or the network.

**`ui` is the whole problem and the number understates it.** 63% of the code with 20 tests, all 20 of which are `DailyState`. The screens themselves — `HomeScreen` 311, `Dial` 212, `TimelineScreen` 170, `SettingsScreen` 162, `EntryReader` 151 — are about a thousand lines with nothing on them.

**But most of that is drawing, and drawing does not want unit tests.** `Dial` and the ambient half of `HomeScreen` are Canvas work where a wrong value is immediately visible and an assertion about a pixel is worth very little. Excluding drawing, the untested view code is perhaps 400 lines — which is the honest figure, and a very different problem from a thousand.

**The real finding is what is hiding in those 400 lines.** §14.6 recorded one pure function living in a view (`skyColors`) and set the trigger at "a second surface wanting the same palette". That trigger never fired, and the debt grew anyway: `HomeScreen` now holds **four** — `skyColors`, `clearness`, `spread`, `arcPosition` — and `TimelineScreen` holds `rowsFor`, which decides month headers and the "earlier" marker. None of it can be tested where it sits.

**That matters more than it did yesterday, because a bug was found in exactly that neighbourhood** (§14.11). The trigger in §14.6 was the wrong trigger. It should not have been "a second consumer"; it should have been **"a second pure function", which is the point at which a view has started accumulating a layer rather than holding an exception.** That threshold passed unnoticed some time this week.

### 14.11 The field that exists to prevent the bug is thrown away before the bug happens

**Found 2026-09-15, by the user, on the first corpus update since the notes feature shipped.** Reported as: *notes retain their position according to the day and not the content.*

**It is real and the design already forbids it.** [SCHEMA](SCHEMA.md) §8 states the rule: selection is a pure function of the date, so publishing new entries changes what a past date computes to — and *"a note carries both its date and its `entryId`, so any day the user responded to is anchored to the truth; the timeline should render that pairing where a note exists and the computed one otherwise."*

`Timeline.view` does not do that. Its last line is `dates.map { Day(it, order[DailySelector.indexFor(it, order.size)]) }` — every day, noted or not, gets the freshly computed entry. It takes `noted: Set<LocalDate>` and uses it only to decide which old dates survive into the list.

**The structural reason it was never implemented is the part worth recording.** `Timeline.view` could not honour the rule even if it wanted to: the `entryId` is not available to it. `DailyState.noteTexts` is `notes.mapValues { it.value.text }`, so the id is discarded one layer above, and `TimelineScreen` receives `Map<LocalDate, String>`. **The field that exists specifically to prevent this failure is thrown away by the layer that sits between the store and the screen.**

So this is not a missing feature. It is a requirement that was written down, designed for in the schema, given a field in the data model — and then quietly dropped in a `mapValues` that looked like a simplification.

**Severity: wrong, not destructive.** Notes keep their `entryId` on disk; `NoteStore` has never dropped it. Nothing is lost and the corpus can be republished freely. What is wrong is the pairing the timeline shows, and it gets wronger every time the corpus grows — which it just did, from 27 entries to 35.

**The fix, for whenever it is picked up.** `Timeline.view` takes the notes rather than a set of dates, and for a noted day resolves the entry by `id` from the corpus, falling back to the computed one when that id is no longer published. That is a `domain` change with an obvious test: build a corpus, note a day, grow the corpus, assert the noted day still shows what was read. The wiring above it is `DailyState` exposing ids alongside text, which is the `mapValues` being undone.

**Not done tonight on purpose.** It is a pure change with a clean test, but it moves a UI contract, and shipping an unvalidated contract change at the end of a long session is the specific thing that produced D-023.

### 14.12 The architecture against each principle, one at a time

**Written 2026-09-15.** §14 has been asserting principle adherence since v1.2 — "as a GRASP Controller it was bloated", "a DIP violation for every caller" — without ever showing the working for the principles it *wasn't* complaining about. This is that working: every SOLID and GRASP principle, what the code does about it, and the evidence. It is deliberately not a scorecard. Two of the entries below are findings precisely because the principle turned out to be a non-event, and one of the most useful results is that a principle this project honours everywhere has exactly one hole, on the morning path, that nobody had named.

#### SOLID

**S — Single Responsibility: mixed, and degrading from the top down.**

Clean: `StableHash` (one function, one reason to change — never), `DailySelector`, `Inline`, `FlickDetector`. Each of the two new `domain` files this week does one thing and has no second reason to change.

Violations, all in the upper layers:

- `CorpusSource` owns the SAF tree URI *and* serves arbitrary files out of that folder. Already recorded as §14.5; worth noting here that the second job exists only because `ForecastSource` reached through the first, so the violation is a coupling symptom rather than a design choice.
- `HomeScreen` renders *and* holds `skyColors`, `clearness`, `spread`, `arcPosition` — four policy functions. §14.10.
- **`MainActivity` is re-accumulating, and this is the finding.** D-022 cut it 424 → 271 lines. It is now 263 code lines, but with three jobs it did not have then: the clock ticker (`rememberNow`), the dial-visibility policy (four coupled effects deciding when the dial retreats), and the flick wiring. The line count went down while the responsibility count went up, which is why line count is a bad proxy. **A God object does not re-form by one large addition; it re-forms by three reasonable ones.**

**O — Open/Closed: strong exactly where it was designed for.**

`Entry.sections` is a `List<Section>` rather than named fields, so a new entry shape is a corpus change and nothing in the client moves — this is what lets the corpus ship ahead of the app (§12, the unknown-block-kind test). Adding a nav section is `SECTIONS` plus one `when` branch.

Deliberate exception: `Block` is a sealed interface, so a new block kind breaks every exhaustive `when` at compile time. That is not a violation being tolerated — it is the compiler enumerating the places that must change, which is worth more here than extensibility, because a block nobody renders is worse than a build error.

Real friction, small: adding a `Condition` means editing `when` blocks in two view files. That is §14.6's debt wearing an OCP hat.

**L — Liskov Substitution: a non-event, and that is the finding.**

There is essentially no inheritance in the project. One interface (`DailyEnv`) with two implementations, both of which satisfy it trivially because it is a set of unrelated operations rather than a behavioural contract with invariants. Sealed types are closed hierarchies over data, not substitutable behaviour.

So LSP is never tested here. Recorded because "we comply" and "the situation never arises" are different facts, and the second one is the true one. Composition throughout is why.

**I — Interface Segregation: respected, and tested once.**

`DailyEnv` has thirteen methods and exactly one client, so by construction no caller depends on a method it does not use. That is compliance by there being nothing to segregate — not much of a result on its own.

It got a real test when the gyroscope arrived. The obvious move was a fourteenth member on `DailyEnv`; the reason it was not taken is that **a subscription is a different shape from a synchronous call**, and adding it would have forced `FakeEnv` — and every future fake — to implement a stream nobody in the tests wants. `RotationFlick` is a separate adapter registered by the composition instead, and `FlickDetector` stays pure. See §14.7's reasoning applied one level out.

**D — Dependency Inversion: the strongest thing in the project, with one hole.**

`domain` imports nothing from Android, Compose or the network. Not once, across every file, since the rule was written. It is checkable in seconds, which is most of why it has held.

Cashed in three times: D-010 (HTTP client → SAF reader), D-020 (Room → JSON file), D-022 (untestable composition → `DailyEnv` seam). Each touched one class. Three returns on one boundary is the whole argument for drawing it.

**The hole: `AlarmReceiver`.** Lines 23–24 call `CorpusLoader.load(context)` and `DailySelector.entryFor(LocalDate.now(), corpus.entries)` directly, through no seam, and it has no test. That makes it **a second composition root that nobody named** — `MainActivity` is documented as the one place that wires concrete things together, and this is another, on the path that runs at 7am when nobody is watching. It is the one place where the rule that has held everywhere else simply is not applied.

#### GRASP

**Information Expert — this is the notes bug, stated as a principle violation.**

`Timeline.view` decides which entry a past day shows. The expert on that question is the `Note`, which holds `entryId`. The decision was made by the component **without** the knowledge, using a computation, while the component **with** the knowledge sat one layer away and had its answer discarded in a `mapValues` (§14.11).

Worth having as a principle statement rather than a bug report, because "the deciding component is not the knowing component" is a shape that can be looked for, and "the timeline shows the wrong entry" is not.

**Pure Fabrication — used deliberately, and it is where the quality is.**

`Derived`, `Inline`, `FlickDetector`, `StableHash`, `Timeline`, `DailyEnv`. No domain expert would name any of them; none corresponds to a thing in the corpus. Between them they hold 66 of the tests in 229 lines. Every single one exists because pulling a computation out of the place it was needed made it testable, and every one of them got tested.

The pattern is consistent enough to be a rule for this project: **fabrications get tests; the things named after real concepts get grades.**

**Controller — split once, drifting back.** D-022's extraction was a textbook Controller reduction. See SRP above for where it went.

**Creator — correct throughout, without anyone deciding to be.** `NoteRepository.put` creates the `Note`, because it holds the existing note and therefore the `createdAt` that must be preserved — the creator is the holder of the initialising data, which is the rule. `MainActivity` creates `DailyState` and `AndroidEnv` as composition root. `CorpusLoader` creates `Entry` from the JSON it alone parses.

**Low Coupling / High Cohesion — excellent below `ui`, poor inside it.** `domain` has no outward dependencies at all. `data` depends on Android and on `domain`, never on `ui`. Inside `ui` the two low-cohesion files are the two named above, and the coupling that matters is `ForecastSource`→`CorpusSource`'s `siblingJson`, which is the one reach-through in the project.

**Polymorphism — traded away on purpose, and the trade is the dependency rule.** `when` over sealed types rather than polymorphic `render()` methods. Putting rendering on `Block` would drag Compose into `domain`, which is the one rule that has never been broken. The cost is the two-file `when` edit noted under OCP. Given that the alternative is the dependency rule, this is not close.

**Indirection — one layer, justified; one thin.** `DailyEnv` is the single indirection and it has paid for itself three times. `NoteRepository` is thin enough to look like ceremony, and would have been, except D-020 swapped the storage underneath it without the layer above noticing — which is the only evidence that ever settles this question.

**Protected Variations — this is where the evening ties together.**

Protected and *proven* protected, by an actual swap rather than an argument: storage (twice), corpus source (once), entry schema (every corpus update since v2).

**Not protected: the selection permutation.** `order = corpus.sortedBy(StableHash)` means the permutation is a function of the corpus *contents*, so adding an entry rewrites what every past date computes to. The variation was known — [SCHEMA](SCHEMA.md) §8 names it explicitly — and the shield was specified: `Note.entryId`. It was given a field in the data model, written into the schema doc, persisted correctly by `NoteStore`, and never wired up.

**So Protected Variations was not violated by ignorance. It was designed for, documented, given storage, and dropped in transit.** That is a worse failure mode than missing it, and a different one: no amount of further design work would have caught it, because the design was right. Only running the thing and growing the corpus caught it, which took three weeks and a user.

#### What the pass actually concluded

The bottom of the architecture is genuinely good, and the evidence for that is not the grades — it is that three separate large substitutions each cost one class, and that both features added this week went in pure-first and never needed a device.

Every problem is in one place, and they are the same two problems wearing different names:

1. **Policy leaking upward into views.** SRP's `HomeScreen`, GRASP's low cohesion, §14.6's `skyColors`, §14.10's four functions — and `bigTextFor` in `AlarmReceiver`, which decides what the notification quotes (quote first, else first paragraph, [R3.9]'s no-duration rule) and is a pure function of an `Entry` sitting in a `BroadcastReceiver` file. One trend, six sightings, none of them individually unreasonable.
2. **The composition root re-accumulating jobs.** SRP's `MainActivity`, GRASP's Controller. Already fixed once; already returning.

Neither is self-announcing. Both are gradual, and both are the kind of thing that only shows up when someone counts — which is the §14.8 habit, now on its second measurement and first full principle pass.

## Related
- [SRS](SRS.md) — the requirements this design implements
- [SCHEMA](SCHEMA.md) — authoring format, publish format, the four shapes
- [DESIGN](DESIGN.md) — visual and interaction design
- [DECISIONS](DECISIONS.md) — D-001 … D-028
- the scope document — the reasoning behind the cuts
- the software development lifecycle — this is the Design-phase artifact
