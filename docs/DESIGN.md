---
created: 2026-09-12
version: 1 — the brass direction
---

# Daily App — Visual and interaction design

> **Public copy.** Generated from the project's private design documents with personal context removed (see [README](README.md)). IDs such as `[R8.2]`, `D-028` and `§6.2` resolve within these six documents; references to the scope document and the content backlog point at documents that are not published.

What the app should feel like, and the decisions that follow from it. See [SCHEMA](SCHEMA.md) for the content model, [DECISIONS](DECISIONS.md) for the record, the scope document for what the app is for.

> Nothing here is built yet. This document exists because six or seven decisions were made in one conversation and would otherwise be invisible in a week.

---

## 1. The feeling

**Rustic, warm, mellow.** An object rather than an interface — brass, worn surfaces, lamplight. The opposite of a productivity app.

This is not decoration. The app's purpose is material you sit with while out and about (the scope document), and the surface decides whether that happens. A corpus nobody wants to look at is a corpus nobody reads.

**The governing instinct, stated once so the rest follows from it:** *nothing in this app should make you hurry.* The reading-time counter was removed for exactly this reason (SCHEMA §3) — a duration invites triage. Every subsequent decision is downstream of that.

---

## 2. The dial

**A mechanical dial, half revealed, pinned to the bottom edge of every screen.**

- **Half the circle.** Centre sits below the screen edge; the upper half is visible. Enough to read as an instrument rather than a horizon line.
- **Persistent, not a screen.** It is a band on every surface, never a destination. Putting a decorative screen between the notification and the entry would add friction to the one path that must stay frictionless.
- **Auto-hides.** After a period without touch it retreats, so a long entry gets the full screen. Returns on a touch near the bottom edge or on the summon gesture.
- **Not numbered. Not the time.** It shows *position*, and the fact that it refuses to be a clock is the point.

### Two hands, two meanings

A real clock has a fast hand and a slow one. So does this.

| Hand | Shows | Moves |
|---|---|---|
| **Fast** | Which section you are in | With the swipe — under a second, driven by the gesture |
| **Slow** | Where you are in the corpus rotation | One step per day; a full revolution is one full cycle of the corpus |

The slow hand is the more interesting of the two. With 27 entries a revolution is 27 days; as the corpus grows the revolution lengthens and the tick marks multiply. It makes D-007's rotation something you can see and feel rather than a fact in a document — *this one is coming back around soon.*

### The gears are real

Layers of brass gears behind the dial, meshing. **Tooth counts should actually mesh**, so ratios are correct and the small gears spin fast while the dial crawls — all derived from a single rotation value.

The clock does not tell the time and the position is not a time, but the *mechanism* is accurate. That inversion is what will make it read as an object rather than an animation, and it costs almost nothing: one number in, everything else derived.

---

## 3. Navigation — the dial is the handle

**Swipe; the gears follow.** The gesture is an ordinary swipe with no learning cost and full precision; the mechanism is *feedback*, not a control you have to aim. An earlier version had the user dragging the hands directly to navigate — rejected: no affordance, imprecise with the pivot off-screen, and slow on a path that should cost nothing.

**Dragging up from the dial pulls the homepage up over the current screen**, gears turning as it rises. Starting the gesture on the dial rather than on the text is what keeps it from fighting the vertical scroll of an 800-word entry.

Sections sit in a fixed order and the fast hand indicates the current one. Half a circle visible means 180° of travel to distribute them across.

**Home · Today · Timeline · Settings**

**There is no Notes section.** D-016 settled that notes appear under their entry and nowhere else. If a separate notes destination is ever wanted, that reverses a decision rather than extending one.

---

## 4. The homepage

The immersive surface. Forecast rendered rather than reported: dark clouds gathering at the top, droplets running down, flashes behind the scene when there is thunder.

**One hard rule: the homepage is never on the path from notification to entry.** Tapping the notification deep-links straight to the reading screen. Opening the app cold lands on Home. Atmosphere is somewhere you go, not something you wade through at 8am.

Elsewhere in the app the forecast is present only as a **background tint** — enough to colour the day, never enough to compete with text.

**Build the rain last, and on its own.** It is the most expensive piece and the most likely to look cheap.

---

## 5. Forecast, not weather

It is a **forecast** — predetermined, several days ahead — which means it does not need to be live, and that resolves what would otherwise be a serious problem.

**The problem.** Live weather means a network call and a location permission. The rule that notes may live on the phone at all was conditional: *no journaling if the app is internet-connected* ([DECISIONS D-003](DECISIONS.md)). Notes only returned in D-015 because nothing needed to touch the internet. A weather API would reintroduce exactly the condition that rule was written against.

**The resolution — it rides the pipe that already exists.** The desktop fetches the forecast and writes `forecast.json` into the synced folder beside `corpus.json`. The phone reads today's row like it reads everything else.

- No network call from the phone
- No location permission
- D-003 intact, notes still justified
- Stale by at most one sync interval, which for a tint is invisible

**Settings carries an override.** Forecasts are wrong often enough that the presentation must be changeable by hand — pick the condition, ignore the file.

---

## 6. Palette and type

### Dynamic colour is removed

`ui/theme/Theme.kt` line 40 currently sets `dynamicColor = true`, so the accent is derived from the wallpaper. That is incompatible with a deliberate palette and was never a decision anyone made — it is the Android Studio default.

### The palette

Warm throughout. **No pure white, no pure black** — both will fight everything else. Starting point, to be adjusted against real text:

| Role | Paper (light) | Lamplight (dark) |
|---|---|---|
| background | `#F2EADD` warm paper | `#14100D` |
| surface | `#EDE3D4` | `#1C1713` |
| onSurface | `#2B241E` ink | `#E8DCC9` |
| primary | `#9A6F3F` aged brass | `#C69A5E` |
| secondary | `#6E5A45` walnut | `#8C7358` |

### Type

Material's default is Roboto, which is cold and will undercut the whole direction. **A humanist serif for body text** will do more for the feeling than any amount of gear rendering — it is the thing actually looked at for four minutes.

Requires bundling a font into `res/font`. That is a resources change, not a dependency. Candidates are open-licensed and bundleable: Source Serif 4, Crimson Pro, EB Garamond.

Section headings and labels stay small, letterspaced and quiet — they are signposts, not furniture.

---

## 7. Beyond reading

**The goal is not reading.** Reading is the most effective way to get edifying material into a spare twenty minutes; it is not the point, and it fails in a large fraction of the situations the app was built for.

**The test for any addition: does it serve pondering in a situation where reading fails?** Most ideas are reading wearing a hat. One is not.

**Audio.** Android's `TextToSpeech` is built in, works offline, needs no dependency and no network. It covers walking, driving, the gym, the dishes — every case where "out and about" and "read 800 words" are incompatible. Not a feature bolted onto a reading app; the same content reaching you when the eyes are busy.

Ranked after notes, ahead of the rain.

---

## 8. Open questions

- What does the fast hand do at a boundary — stop, or wrap round? Wrapping makes the ring continuous; stopping makes position legible.
- Does the slow hand advance at midnight, or when the entry is first opened? The first is honest; the second is more satisfying.
- How much vertical space does the dial cost when visible, and does the reading screen reflow when it hides?
- Does the tint apply to the timeline too, or only Today and Home?
- Gears rendered as Compose `Canvas` paths, or as rotated vector drawables? Canvas gives correct meshing cheaply; drawables give better wear and texture.

---

## 9. Not being built

Restating so it does not get relitigated:

- **Search, and `related`-edge traversal** — D-016. The vault is the graph.
- **A separate notes view** — D-016. Notes sit under their entry.
- **Live weather** — §5. The forecast rides the sync.
- **A homepage on the morning path** — §4.
