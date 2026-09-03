# AGENTS.md

This project keeps its knowledge in two files. There is no third copy, and nothing here is
duplicated, so neither file can drift from the other.

| File | What is in it |
|---|---|
| `CLAUDE.md` | The architecture map. Module and build structure, the Syncplay wire protocol, the sync algorithm, every subsystem, the expect/actual table, the player engines, the built in server, known gaps. Read this to understand how the app works. |
| `MASTER_LEDGER.md` | The engineering ledger. Every open defect with its status, the standing owner rulings you must not undo, the do-not-re-flag and refuted lists, the GitHub issue map, the roadmap and the test plan. Read this before changing anything. **It is gitignored, so it exists only on the maintainer's machine.** |

If you are an AI agent working in this repo: read `CLAUDE.md` first, then `MASTER_LEDGER.md` if it is present.
Read both in full. Do not skim.

This file used to be a full copy of `CLAUDE.md`. It went stale, so it became a pointer instead.
