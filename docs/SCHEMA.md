---
created: 2026-08-27
updated: 2026-10-10
version: 2 — rewritten around the "ponder" format (see DECISIONS D-015)
---

# Daily App — Data Model

> **Public copy.** Generated from the project's private design documents with personal context removed (see [README](README.md)). IDs such as `[R8.2]`, `D-028` and `§6.2` resolve within these six documents; references to the scope document and the content backlog point at documents that are not published.

Three representations of the same content, in the order it flows: what you write, what gets published, what the phone stores. See the scope document for why, [DECISIONS](DECISIONS.md) for what was settled.

> **v2 supersedes v1 entirely.** v1 modelled an entry as quote + attribution + significance. That is now one *component* of a day, not a day. The change came from an earlier long-form format the author had used and wanted to return to.

---

## The depth floor

One rule governs everything below:

> **A day must *develop* its material, not merely present it.**

A quote with a paragraph attached is not a day. Neither is a bare question. Whatever shape an entry takes, it earns its reading time by opening something up. This is a content requirement, not a length requirement — but in practice it means most entries run to several hundred words.

The shapes vary so the format doesn't go stale. The floor does not vary.

---

## 1. The four shapes

All four go deep on one thing. They differ in *how*.

| Type | Goes deep by | Shape |
|---|---|---|
| **collision** | tension | Two ideas that ruin each other; the reader has to resolve it |
| **expound** | unpacking | One line opened up — what it means, what it attacks, where it lands |
| **principle** | development | A claim built out and pressure-tested |
| **passage** | close reading | A substantial excerpt, then your reading of it |

**`collision`** is the format from *Two Weeks of Deep Thought* and is the most expensive to produce. **`expound`** is the Nietzsche case — "where the state ceaseth, there only commenceth the man who is not superfluous" carries a day on its own, but only when unpacked. **`principle`** is an earlier wiki-note template, developed to day length. **`passage`** is the cheapest, because it's curation plus commentary rather than composition.

**Questions and bare quotes are components, not types.** They appear *inside* entries — a pull quote serving a section, a question closing one — exactly as they do in the source format.

---

## 2. Authoring — one markdown file per entry

`corpus/entries/<id>.md`. One file per *entry*, not per theme: entries now run to a thousand words or more, and `tags` replaced themes as the grouping (§5).

### Common frontmatter — every type

```yaml
---
id: free-will-and-moral-luck     # permanent, [a-z0-9-]
type: collision                   # collision | expound | principle | passage
title: Free Will & Moral Luck     # library list + notification
tagline: Two ideas that quietly destroy each other
tags: [ethics, agency, luck]
related: [luck-is-an-obligation-not-an-entitlement, you-are-a-steward-not-an-owner]
---
```

`related` carries over from the `## Related` sections of the wiki notes. It is what makes the library a graph rather than a list — see §6.

### Required sections by type

Sections are `##` headings. Extra sections are allowed and rendered in order; only the required ones are validated.

**collision**

```markdown
## Surface
Accessible opening. Ends on a question.

## Idea One — Free Will
**Thinkers:** Kant · Sartre · Dennett

The case for it.

> We are nothing more or less than the cumulative biological and environmental luck, over which we had no control, that has brought us to any moment.
> — Robert M. Sapolsky, *Determined: A Science of Life Without Free Will* (2023)

## Idea Two — Moral Luck
**Thinkers:** Thomas Nagel · Bernard Williams

The opposing case.

## Collision
Where the two destroy each other.

## Implication
What it means for how you're living.

## Reframe
The turn. What to actually do with it.
```

**expound**

```markdown
## Quote
> Where the state ceaseth, there only commenceth the man who is not superfluous.
> — Friedrich Nietzsche, *Thus Spoke Zarathustra*, "Of the New Idol"

## Where it sits
Context in the work. What surrounds it.

## What it means
The unpacking. This is the bulk.

## The objection
What's wrong with it, or what it's been misused for. Required — an expounded quote
without a counterweight is advocacy, not thinking.

## Reframe
```

**principle**

```markdown
## Claim
The idea, stated plainly.

## Development
Built out. Terms defined inline.

## Why it matters
What this unlocks or changes.

## Open questions
- Anything unresolved.
```

**passage**

```markdown
## Passage
> Several paragraphs, quoted.
> — Author, *Source*

## Reading
Your commentary. Required and substantial — without it this is a clipping, not an entry.
```

### Blockquote convention

A blockquote whose last line begins `— ` is parsed as an attributed quote: everything before is the text, the last line is the attribution. This is how pull quotes, the `expound` quote, and the `passage` excerpt are all expressed with one rule.

---

## 3. Publish format

One JSON object, as v1. Per entry:

```json
{
  "id": "free-will-and-moral-luck",
  "type": "collision",
  "title": "Free Will & Moral Luck",
  "tagline": "Two ideas that quietly destroy each other",
  "tags": ["ethics", "agency", "luck"],
  "related": ["luck-is-an-obligation-not-an-entitlement"],
  "words": 1240,
  "minutes": 6,
  "sections": [
    { "heading": "Surface", "blocks": [ { "kind": "p", "text": "…" } ] },
    { "heading": "Idea One — Free Will",
      "meta": { "thinkers": ["Kant", "Sartre", "Dennett"] },
      "blocks": [
        { "kind": "p", "text": "…" },
        { "kind": "quote", "text": "…", "attribution": "Robert M. Sapolsky, *Determined: A Science of Life Without Free Will* (2023)" }
      ] }
  ]
}
```

**Sections are a list, not named fields.** The renderer walks them in order and styles by heading and block kind. That means a new type — or an extra section inside an existing one — needs no client change. Given how much the content model has moved in one evening, that flexibility is worth the small cost in type safety.

### Derived fields

`words` and `minutes` are **computed at publish time**, never authored. `minutes` = words / 200, rounded up, minimum 1.

**Neither is displayed** (decided 2026-09-12). An earlier version of this document argued they answer "do I have time for this?" — but that is the wrong question to invite. A duration prompts triage, and the daily entry exists to be sat with, not fitted into a gap. They stay in the schema because they cost nothing and the timeline may yet want them; they are simply not rendered.

### Validation — fails the build

Carried from v1, plus type-specific rules:

- Frontmatter has `id`, `type`, `title`, `tags`; `id` matches `^[a-z0-9-]+$` and is unique
- `type` is one of the four
- **Every required section for the type is present and non-empty**
- Every `related` id exists in the corpus
- No duplicate quote text across entries
- **Depth floor:** warn under 400 words, fail under 150. A crude proxy for "develops rather than presents", but it catches the obvious violation.
- Warn if the whole corpus is under 30 entries

---

## 4. Notes — yours, dated, on-device

**Notes attach to the day you wrote them, not to the entry.**

```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,      // which entry prompted it
    val date: String,         // ISO local date — the day you wrote it
    val text: String
)
```

**Why dated rather than attached to the entry.** The rotation brings an entry back in roughly *n* days. Reading it again with your September note beneath it, and writing a different one in December, is the point — the corpus is fixed and you are not. Notes become the record of the difference. A single note per entry, overwritten, would destroy exactly the thing that makes repeats worth having.

**It also preserves [DECISIONS](DECISIONS.md) D-004.** The app still records nothing about you passively — no view counts, no "last seen", no streaks. But because notes carry dates, encounter history is *derivable from what you deliberately chose to write*. The record exists without the surveillance, and only for entries you cared enough to answer.

**This is journaling returning** (D-003, D-010). It is safe now because nothing leaves the phone — and it is the only data in the app that cannot be regenerated, which is what brings Room back after D-014 removed its justification.

---

## 5. Tags, not themes

Themes are gone. `tags` from the wiki frontmatter replace them.

A theme forced one entry into one grouping. A tag lets an entry sit in several — `[ethics, agency, luck]` — which is what a graph-shaped library wants. The tags already exist across ~129 archived notes, so the grouping is mostly authored already.

This does not disturb [DECISIONS](DECISIONS.md) D-006: tags are for authoring and browsing, never for scheduling. Selection still draws from the whole corpus.

---

## 6. On-device

| Table | Contents | Disposable? |
|---|---|---|
| `entries` | id, type, title, tagline, words, minutes, `sections` as JSON | Yes — re-derived from the corpus |
| `tags` | entryId, tag | Yes |
| `related` | fromId, toId | Yes |
| `notes` | id, entryId, date, text | **No — the only non-disposable table** |
| `meta` | corpusVersion, lastSyncAt | Yes |

`sections` is stored as a JSON blob rather than normalised. It is only ever read whole to render one entry — normalising it would buy nothing and cost a migration every time the content model moves.

**Migration policy unchanged** ([SRS](SRS.md) R7.4/R7.5): everything except `notes` may be dropped and rebuilt from the published corpus. `notes` gets a real `Migration` and a `MigrationTestHelper` test. The risk is concentrated in one table again — it just isn't the table it used to be.

---

## 7. Selection — unchanged

[DECISIONS](DECISIONS.md) D-005 and D-007 still hold exactly. One fixed permutation over the whole corpus, indexed by the day, no stored state:

```
order       = corpus.sortedBy { hash(id) }
todaysEntry = order[daysSince(EPOCH) mod n]
```

Entry types do not affect selection. Every entry meets the depth floor, so every day is substantial — which is why there is no need to weight the rotation by type.

**The notification carries the title, the tagline, and the hook** — the entry's quote where it has one, otherwise its opening lines. No reading time; see §3.

---

## 8. The library — a timeline, and tags

**Settled 2026-09-12.** Two surfaces, and deliberately not a third.

**A timeline you scroll**, newest first. One row per day: the entry that appeared, and any note you wrote under it.

**Filter by tag.** That is the whole of the organising apparatus.

**Explicitly not built: full-text search, and `related`-edge traversal.** The user's reasoning, which is a product boundary rather than a shortcut:

> Search and related edges belong in the notes tool where the entries are written (Obsidian), not rebuilt in the app.

**The vault is the graph and the search; the app is the daily surface and the place notes get written.** Entries live in the vault as markdown with `wikilinks`, so Obsidian already provides traversal — duplicating it in the app would be building a worse version of a tool that is already open on the desktop. The `related` field stays in the schema because it serves the vault; the app simply does not render it as navigation.

This line is worth keeping for future scope requests, most of which will be some form of "what if it also…".

### The timeline needs no stored state

Selection is a pure function of the date (§7), so running it backwards reconstructs every past day:

```
for d in today downTo (today - 365):
    entry = DailySelector.entryFor(d, corpus)
    notes = noteDao.forDate(d)
```

Nothing is recorded to make this work. The statelessness chosen for picking *tomorrow* yields the entire history as a side effect.

**One caveat, inherited from D-007.** The permutation derives from corpus size, so publishing new entries changes what past dates compute to. A row for 3 September may show a different entry than the one actually seen that day.

**Notes pin the days that matter.** A note carries both its date and its `entryId`, so any day the user responded to is anchored to the truth; the timeline should render *that* pairing where a note exists and the computed one otherwise. Days scrolled past and forgotten will drift.

That is the right trade: accurate where the reader engaged, approximate where they didn't, and still nothing tracked.

### Notes appear under their entry only

No separate "everything I've written" view. Opening an entry shows its notes, dated, oldest first — so a repeat is met with what the reader thought last time sitting beneath it. The timeline renders them inline on their day.

---

## 9. Production model

**AI drafts; the author directs and edits.** Confirmed 2026-09-12.

The author directs what gets made and edits freely; nothing ships unexamined. This is unchanged from the original workflow assumption, and it is what makes the **collision** shape affordable — it is the expensive one, and the ~129 archived wiki notes are the raw material a draft can be generated from.

Still unquantified: the mix. Collisions are costly, passages cheap. Worth measuring against real effort before committing to a ratio.
