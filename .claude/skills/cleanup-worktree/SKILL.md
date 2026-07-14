---
name: cleanup-worktree
description: Safely remove a git worktree and its associated local/remote branches after confirming the code has been merged
version: 1.0.0
source: custom
---

# Cleanup Worktree and Branch

Use this skill when the user wants to remove a git worktree and delete its associated branches (local and remote), after confirming the code has been merged into the target branch.

## When to Use

- User says: "清理 worktree"
- User says: "删除 worktree 和分支"
- User says: "这个 worktree 没用了，删掉吧"
- User says: "把 xxx 分支的 worktree 清理掉"
- Any request to remove a worktree and/or its branch

## Safety First

This skill performs **destructive** operations. Always:

1. Check whether the branch has been merged
2. Warn the user if uncommitted changes exist
3. Ask for explicit confirmation before deleting anything
4. Never auto-delete remote branches without a second confirmation

## Workflow

### Step 1: List Available Worktrees

Run:

```bash
git worktree list
```

If the user specified a worktree path or branch name, match it against the list.
If not specified, present the list and ask the user to choose.

### Step 2: Identify the Target Branch

From the selected worktree, determine its branch:

```bash
git -C "$WORKTREE_PATH" branch --show-current
```

If the worktree is in detached HEAD state, warn the user and ask whether to proceed with worktree removal only (no branch deletion).

### Step 3: Check Merge Status

Check whether the branch has been merged into common base branches:

```bash
BRANCH=$(git -C "$WORKTREE_PATH" branch --show-current)

# Check against main
git branch --merged main | grep -q "^\*\?\s*$BRANCH$"

# Check against origin/main
git branch -r --merged origin/main | grep -q "origin/$BRANCH$"

# Check against current default branch
git branch --merged HEAD | grep -q "^\*\?\s*$BRANCH$"
```

Also check for open pull requests if `gh` CLI is available:

```bash
gh pr list --head "$BRANCH" --state open
```

### Step 4: Report Status and Ask for Confirmation

Present a summary:

```
Worktree: /path/to/worktree
Branch:   feature/xxx
Status:   [已合并到 main / 未合并 / 未知]
Open PR:  [无 / #123]
Uncommitted changes: [无 / 有]

This will:
1. Remove the worktree directory
2. Delete local branch feature/xxx
3. Optionally delete remote branch origin/feature/xxx

⚠️  WARNING: If the branch is not merged, deleting it may cause data loss.

Have you merged the code? (yes/no/abort)
```

If the branch is **not merged**, add an extra warning:

```
⚠️  Branch feature/xxx does NOT appear to be merged into main.
Are you sure you want to delete it?
```

### Step 5: Handle Uncommitted Changes

Check for uncommitted changes in the worktree:

```bash
git -C "$WORKTREE_PATH" status --short
```

If changes exist:

```
The worktree has uncommitted changes:
M  src/main/java/...
?? temp.txt

Please commit, stash, or discard these changes first.
Aborting cleanup.
```

Stop and ask the user to resolve before retrying.

### Step 6: Remove Worktree

After confirmation, remove the worktree:

```bash
git worktree remove "$WORKTREE_PATH"
```

If removal fails due to uncommitted changes or other issues, try with force (only after user confirms):

```bash
git worktree remove --force "$WORKTREE_PATH"
```

### Step 7: Delete Local Branch

```bash
git branch -D "$BRANCH"
```

Use `-d` instead of `-D` if the branch is confirmed merged:

```bash
git branch -d "$BRANCH"
```

### Step 8: Delete Remote Branch (Optional, Requires Extra Confirmation)

Ask separately:

```
Also delete remote branch origin/feature/xxx? (yes/no)
```

If yes:

```bash
git push origin --delete "$BRANCH"
```

### Step 9: Verify Cleanup

Run:

```bash
git worktree list
git branch -a | grep "$BRANCH"
```

Confirm the worktree and branch no longer exist.

## Output Format

On success:

```
Cleanup complete.
- Removed worktree: /path/to/worktree
- Deleted local branch: feature/xxx
- Deleted remote branch: origin/feature/xxx [if applicable]
```

On abort:

```
Cleanup aborted. No changes were made.
```

## Example Commands

```bash
# List worktrees
git worktree list

# Remove worktree only
git worktree remove ../xiaozhi-esp32-server-feature-xxx

# Remove worktree and delete local branch
git worktree remove ../xiaozhi-esp32-server-feature-xxx
git branch -D feature/xxx

# Delete remote branch
git push origin --delete feature/xxx
```

## Safety Rules

- Always ask before deleting.
- Never delete a branch that appears unmerged without explicit user confirmation.
- Never delete a remote branch without a second explicit confirmation.
- Never force-remove a worktree without warning the user about uncommitted changes.
- If unsure about merge status, prefer to abort and ask the user to verify manually.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Deleting an unmerged branch | Always check merge status first |
| Forgetting remote branch | Ask separately before deleting `origin/*` |
| Losing uncommitted changes | Check `git status --short` before removal |
| Removing a worktree that is currently in use | Warn user and abort |
| Confusing worktree path with branch name | Match against `git worktree list` output |
