# Design documents

The project's requirements, design and decision record, as they were written while
building it. Code comments cite them by ID: `[R8.2]` is a requirement, `D-028` a
decision, `§6.2` a section of the design spec.

| Document | What it is |
|---|---|
| [SRS](SRS.md) | Software requirements: numbered `[Rn.x]` and `[NFRn]` requirements, each with a build status |
| [SDS](SDS.md) | Software design: module structure, data design, algorithms, error handling, test strategy, and a measured technical-debt record (§14) |
| [DECISIONS](DECISIONS.md) | The decision log, D-001 onward: what was chosen between, what was picked, what it cost, and the corrections when a call was wrong |
| [ARCHITECTURE](ARCHITECTURE.md) | Mermaid diagrams of the code as built: classes, data model, the morning path, the publish flow |
| [DESIGN](DESIGN.md) | Visual and interaction design: the brass direction, the dial, palette and type |
| [SCHEMA](SCHEMA.md) | The content model: the four entry shapes, the authoring format, and `corpus.json` |

**Where to start.** For *what* it does, read the SRS §1–2. For *how* it's built, read the
SDS §2 with ARCHITECTURE beside it. For *why*, read DECISIONS. The entries that changed the
design most are D-007 (one fixed permutation), D-010 (no backend), D-015 (the content model),
D-020 (notes as a JSON file) and D-028 (the notes backup).

**These are generated copies** of the project's working documents. The originals live
with the author's notes. Details of the personal setup (machines, folder paths, sync
configuration) are left out here. Edit the originals and regenerate: an edit made here is
overwritten by the next run.

**They are working documents, not marketing.** The record of mistakes is kept on purpose:
a test count that was never observed (D-023), a requirement marked satisfied by a file that
failed it (D-027), and advice that turned out to be wrong (D-011, D-012). The project was
built with an AI assistant (Claude). The revision tables credit it, and where its
suggestions were wrong, the log says so and records what replaced them.

Two documents they mention are not published: the original scope document, and the content
backlog.
