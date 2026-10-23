---
name: sandcastle-run
description: Use when the user asks to run Sandcastle, 跑 RALPH, process scratch issues, or launch a specific feature with a CCR model.
---

## What This Does

Starts the Sandcastle sequential-reviewer pipeline defined in `.sandcastle/main.mts`:

1. Implementer agent picks one open issue from `.scratch/` and commits a fix on a worktree branch.
2. Reviewer agent reviews the diff on the same branch.
3. Host merges the branch back only after verification and a passing issue review.
4. The loop repeats up to `MAX_ITERATIONS` (currently 15). Once all target issues are resolved, the runner verifies and reviews their cumulative changes on host HEAD. Blocking findings can trigger up to two verified repair rounds. Only `Batch ... complete` means the batch passed.

The model used by both agents is controlled by the `SANDCASTLE_MODEL` env var. The value must exist in the local CCR routing table; the container reaches CCR via `host.docker.internal:3456`.

**The user MUST explicitly pick a model on every invocation — there is no default.** If the user did not name one, ask before doing anything else. Do not infer from context, do not fall back to what was used last time, do not pick one because it "seems right". Each run can burn hours of tokens on the wrong provider; an explicit choice is the only safeguard.

## Available Models

Only these three are known-good against the local CCR right now. Do not invent others — if the user names something outside this list, call it out and ask which of the three to use.

| Key | `SANDCASTLE_MODEL` value | When to pick |
|---|---|---|
| `kimi` | `Kimi/k3` | First choice. Direct Kimi coding plan. |
| `glm` | `ModelRouter/qwen/ZHIPU/GLM-5.3` | Kimi quota exhausted, or user wants GLM. |
| `kimi-via-router` | `ModelRouter/qwen/kimi-k3` | Kimi direct endpoint broken but ModelRouter upstream alive; backup Kimi channel. |

Aliases the user may say that map to the above:
- "kimi" / "k3" / "默认" → `Kimi/k3`
- "glm" / "glm5.3" / "智谱" / "zhipu" → `ModelRouter/qwen/ZHIPU/GLM-5.3`
- "kimi 备用" / "router kimi" / "modelrouter kimi" → `ModelRouter/qwen/kimi-k3`

## Steps

1. **Ask for the model — always.** If the user already named one in this invocation (including aliases from the table above), use it. Otherwise stop and ask with `AskUserQuestion`, presenting the three rows from the table as options. Do not proceed to step 2 until the user has picked one in this conversation turn. Even if they picked one last time, ask again — provider quota state changes between runs.

2. **Choose the issue scope.** If the user names a feature, set `SANDCASTLE_FEATURE` to its `.scratch/<feature>/` slug. If not, inspect `bash .sandcastle/bin/scratch-issues.sh list`. For one open feature, use that slug; for multiple open features, ask whether to run one feature or all. Leave `SANDCASTLE_FEATURE` unset only when the user chose all. An unset or empty value includes every open issue; a nonempty slug may contain only letters, digits, `_`, and `-`. The batch fixes its target issue list at start, so resuming must use the same feature and branch.

3. **Pre-flight checks** (run all three, fail fast with a clear message):
   - Working tree clean: `git status --porcelain --untracked-files=all` must be empty. Sandcastle refuses to run on a dirty tree.
   - CCR reachable: `curl -sf http://127.0.0.1:3456/health` returns `{"status":"ok"}`.
   - `.sandcastle/.env` exists and contains `ANTHROPIC_BASE_URL` + `ANTHROPIC_API_KEY`. Do NOT print the key.

4. **Check authorization for the selected external model.** The run sends relevant repository content to the selected CCR provider and can modify and merge files. Before launch, ensure the user has explicitly authorized that provider and scope in this request or earlier conversation. If either is missing, ask for it with the provider, feature scope, and maximum of 15 issue iterations stated. Do not repeat an approval already given. Respect an approval-system rejection; do not retry through another command path without the required authorization.

5. **Launch in the background** so the user keeps the terminal:
   ```
   SANDCASTLE_FEATURE=<feature-slug> SANDCASTLE_MODEL=<value> npm --prefix .sandcastle run sandcastle
   ```
   Omit `SANDCASTLE_FEATURE=...` for an explicitly selected all-feature run. Use `run_in_background: true` where the host supports it. `npm --prefix .sandcastle` uses the runner's local `tsx` dependency.

6. **Report back**: the background task ID (if available), feature scope, model, and `.sandcastle/logs/` path. Follow the process through its final exit: report `Batch ... complete` only on a zero exit, and include the final review report from `.sandcastle/logs/<batch-id>/`. On failure, report the saved batch outcome and log path. Intermediate commits use branches matching `sandcastle/sequential-reviewer/<ts>-<suffix>`.

## Abort / Inspect

- Stop a run: `TaskStop` on the background task ID, or `pkill -f "tsx .sandcastle/main.mts"` from the shell.
- Inspect what an iteration committed: `git log --oneline sandcastle/sequential-reviewer/<ts>` (branch names are printed in the run output).
- If a merge conflict blocked the loop, the script exits non-zero and prints manual-resolve steps — surface them verbatim to the user.

## Notes

- Do not edit `.sandcastle/.env` to "switch model" — `SANDCASTLE_MODEL` env var on the command line is the supported mechanism; `.env`'s `ANTHROPIC_MODEL` is only a fallback for ad-hoc container shells.
- Verification gates in `main.mts` are path-based (`main/manager-api/`, `main/manager-web/`, `main/egg-miniprogram/`, `main/xiaozhi-server/`). A run that touches none of these paths will log "No subproject checks apply" and merge without running tests — flag this to the user if it happens unexpectedly.
