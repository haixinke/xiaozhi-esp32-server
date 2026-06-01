# AGENTS.md

This file provides coding rules and standards for the xiaozhi-esp32-server project.
For project overview and architecture, see [CLAUDE.md](./CLAUDE.md).

---

# Rules

## Structure

Rules are organized into a **common** layer plus **language-specific** directories:

```
rules/
├── common/          # Language-agnostic principles (always install)
│   ├── coding-style.md
│   ├── git-workflow.md
│   ├── testing.md
│   ├── performance.md
│   ├── patterns.md
│   ├── hooks.md
│   ├── agents.md
│   └── security.md
├── typescript/      # TypeScript/JavaScript specific
├── python/          # Python specific
├── java/          # Java specific
└── web/             # Web and frontend specific
```

- **common/** contains universal principles — no language-specific code examples.
- **Language directories** extend the common rules with framework-specific patterns, tools, and code examples. Each file references its common counterpart.

## Project Tech Stack Rules

This project has five sub-projects with different languages. Apply the corresponding rules based on which sub-project you are working on:

| Sub-project | Language | Key Rules |
|---|---|---|
| `main/xiaozhi-server/` | Python 3.12 | Python rules below |
| `main/manager-api/` | Java 21 / Spring Boot 3.4.3 | Java rules below |
| `main/manager-web/` | Vue.js 2 / JavaScript | Web + JS rules below |
| `main/manager-mobile/` | Uni-app / Vue 3 / Vite | Web + JS rules |
| `main/demo-web/` | HTML / CSS / JS / Vite | Web rules below |

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

---

## Git Workflow

### Commit Messages

```
<type>: <description>
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`

### Pull Requests

1. Analyze full commit history (not just latest commit)
2. Use `git diff [base-branch]...HEAD` to see all changes
3. Draft comprehensive PR summary
4. Include test plan with TODOs

