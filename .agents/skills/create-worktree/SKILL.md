---
name: create-worktree
description: Create a new git worktree from a specified base branch for isolated feature development
version: 1.0.0
source: custom
---

# Create Worktree from Branch

Use this skill when the user wants to create a new git worktree based on a specific branch, typically for isolated new-feature development.

## When to Use

- User says: "用 xxx 分支建一个 worktree"
- User says: "基于 dev/main 创建新 worktree"
- User says: "给我开一个新 worktree，从 yyy 分支切出来"
- Any request that combines **branch selection** + **worktree creation**

## Preconditions

1. Run git isolation detection first:
   ```bash
   GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
   GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
   BRANCH=$(git branch --show-current)
   SUPERPROJECT=$(git rev-parse --show-superproject-working-tree 2>/dev/null)
   ```
2. If `GIT_DIR != GIT_COMMON` and `SUPERPROJECT` is empty, the session is already in a linked worktree — do NOT create another worktree; report this and stop.
3. If in a submodule (`SUPERPROJECT` non-empty), treat as a normal repo and proceed.

## Workflow

### Step 1: Identify Base Branch and New Branch Name

Extract from user input or ask for clarification:

- **BASE_BRANCH**: the branch to base the new worktree on (e.g. `main`, `dev`, `codex/egg-v1`)
- **NEW_BRANCH**: the new feature branch to create (e.g. `feature/xxx`)

If either is missing, ask the user before proceeding.

### Step 2: Determine Worktree Location

Follow this priority:

1. User explicitly specified a directory → use it.
2. Project has `.worktrees/` directory → use `.worktrees/$NEW_BRANCH`
3. Project has `worktrees/` directory → use `worktrees/$NEW_BRANCH`
4. Default → `.worktrees/$NEW_BRANCH` at the project root

If the chosen location is inside the project tree, verify it is ignored:

```bash
git check-ignore -q "$LOCATION" 2>/dev/null
```

If NOT ignored, add `$LOCATION/` to `.gitignore`, commit that change, then proceed.

### Step 3: Update Base Branch

```bash
git fetch origin "$BASE_BRANCH"
git worktree add "$LOCATION" -b "$NEW_BRANCH" "origin/$BASE_BRANCH"
```

If `origin/$BASE_BRANCH` does not exist, fall back to the local branch:

```bash
git worktree add "$LOCATION" -b "$NEW_BRANCH" "$BASE_BRANCH"
```

### Step 4: Run Project Setup

Change into the new worktree and run setup based on detected project type:

```bash
cd "$LOCATION"

# Java / Maven
if [ -f pom.xml ]; then mvn clean install -DskipTests; fi

# Node.js
if [ -f package.json ]; then npm install; fi

# Python
if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
if [ -f pyproject.toml ]; then poetry install; fi

# Go
if [ -f go.mod ]; then go mod download; fi

# Rust
if [ -f Cargo.toml ]; then cargo build; fi
```

### Step 5: Verify Clean Baseline

Run the project-appropriate test command:

```bash
# Java
mvn test

# Node.js
npm test

# Python
pytest

# Go
go test ./...

# Rust
cargo test
```

If tests fail, report failures and ask whether to proceed.

## Output Format

On success, report:

```
Worktree created at <full-path>
Branch: <NEW_BRANCH> based on <BASE_BRANCH>
Setup completed: <dependency manager>
Tests: <N> tests, 0 failures
Ready to implement <feature-name>
```

## Example Commands

```bash
# Based on main
git worktree add ../xiaozhi-esp32-server-feature-xxx -b feature/xxx main

# Based on dev
git worktree add .worktrees/feature-yyy -b feature/yyy dev

# Based on remote branch
git worktree add .worktrees/feature-zzz -b feature/zzz origin/main
```

## Safety Rules

- Never create a worktree inside another worktree.
- Never use `git worktree add` when the platform provides a native worktree tool (e.g. `EnterWorktree`) — prefer native tools.
- Always ensure project-local worktree directories are gitignored.
- Never auto-merge or auto-push without explicit user consent.
- Do not proceed with failing tests unless the user explicitly agrees.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Branch already has a worktree | Report and ask for a different branch name |
| Directory already exists | Check if it's an existing worktree; if not, ask before overwriting |
| Base branch not found | Fetch from origin or ask user to confirm branch name |
| Worktree directory not ignored | Add to `.gitignore` and commit before creating worktree |
