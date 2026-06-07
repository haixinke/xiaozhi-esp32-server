# AGENTS.md

This file provides coding rules and standards for the xiaozhi-esp32-server project.
For project overview and architecture, see [CLAUDE.md](./CLAUDE.md).

---

## General Coding Style (All Languages)

### KISS / DRY / YAGNI

- Prefer the simplest solution that actually works
- Extract repeated logic into shared functions; avoid copy-paste drift
- Do not build features or abstractions before they are needed

### File Organization

- Many small files > few large files
- 200-400 lines typical, 800 max per file
- Organize by feature/domain, not by type

### Error Handling

- Handle errors explicitly at every level
- Provide user-friendly messages in UI-facing code
- Log detailed context on the server side
- Never silently swallow errors

### Input Validation

- Validate all user input before processing
- Fail fast with clear error messages
- Never trust external data (API responses, user input, file content)

### Immutability

- Prefer immutable patterns; avoid in-place mutation
- Create new objects instead of modifying existing ones



