# Product Requirements Documents — Index

This directory holds one PRD per brownfield enhancement, matching the same epic-scoped organization used in `docs/stories/{EPIC-ID}/`. Each PRD is self-contained; this index is the single entry point for finding the current one.

| Epic ID | PRD | Covers | Status |
|---------|-----|--------|--------|
| ADMIN-1 | [ADMIN-1.md](ADMIN-1.md) | Admin dashboard sidebar restructure, GOVMAN XML import, US Code import, unified API search/import UI pattern | Draft |
| ES-1 | [ES-1.md](ES-1.md) | Evidence Store Foundation — persisted article storage, entity-to-article linkage, real bias/fallacy signal via the reasoning service, grounded-query interface | Draft |

## Conventions

- Filename matches the epic ID it covers (e.g., `ES-1.md` for epic `ES-1`)
- Corresponding stories live in `docs/stories/{EPIC-ID}/`
- Corresponding architecture documents (where they exist) live at `docs/architecture-{slug}.md` or within the sharded `docs/architecture/` directory for cross-cutting topics
