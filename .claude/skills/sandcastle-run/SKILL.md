---
name: sandcastle-run
description: Trigger the Sandcastle sequential-reviewer loop (`.sandcastle/main.mts`) to autonomously work through `.scratch/` issues with a chosen LLM routed via local CCR. Use when the user asks to "run sandcastle", "让 sandcastle 干活", "跑 RALPH", "处理 scratch issue", or wants to pick a specific model (Kimi/k3, GLM-5.3, ModelRouter Kimi) for the autonomous run.
---

## What This Does

Starts the Sandcastle sequential-reviewer pipeline defined in `.sandcastle/main.mts`:

1. Implementer agent picks one open issue from `.scratch/` and commits a fix on a worktree branch.
2. Reviewer agent reviews the diff on the same branch.
3. Host merges the branch back only if both phases pass per-subproject verification gates.
4. Loop repeats up to `MAX_ITERATIONS` (currently 15) or until backlog is empty.

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

2. **Pre-flight checks** (run all three, fail fast with a clear message):
   - Working tree clean: `git status --porcelain --untracked-files=all` must be empty. Sandcastle refuses to run on a dirty tree.
   - CCR reachable: `curl -sf http://127.0.0.1:3456/health` returns `{"status":"ok"}`.
   - `.sandcastle/.env` exists and contains `ANTHROPIC_BASE_URL` + `ANTHROPIC_API_KEY`. Do NOT print the key.

3. **Confirm scope with the user before launching**, because a run can burn significant tokens and modify many files: "Will run sandcastle with model `<value>` for up to 15 iterations on `.scratch/` backlog. OK?" Only skip this confirmation if the user explicitly said "just run it" / "直接跑".

4. **Launch in the background** so the user keeps the terminal:
   ```
   SANDCASTLE_MODEL=<value> npx tsx .sandcastle/main.mts
   ```
   Use `run_in_background: true`. Log line `Using agent model: <value>` at the start of iteration 1 confirms the env var took effect — check for it in the early output.

5. **Report back**: the background task ID, the chosen model, and where to watch progress (`.sandcastle/logs/` for per-phase verification logs; the branch name pattern `sandcastle/sequential-reviewer/<ts>` for inspecting intermediate commits).

## Abort / Inspect

- Stop a run: `TaskStop` on the background task ID, or `pkill -f "tsx .sandcastle/main.mts"` from the shell.
- Inspect what an iteration committed: `git log --oneline sandcastle/sequential-reviewer/<ts>` (branch names are printed in the run output).
- If a merge conflict blocked the loop, the script exits non-zero and prints manual-resolve steps — surface them verbatim to the user.

## Notes

- Do not edit `.sandcastle/.env` to "switch model" — `SANDCASTLE_MODEL` env var on the command line is the supported mechanism; `.env`'s `ANTHROPIC_MODEL` is only a fallback for ad-hoc container shells.
- Verification gates in `main.mts` are path-based (`main/manager-api/`, `main/manager-web/`, `main/egg-miniprogram/`, `main/xiaozhi-server/`). A run that touches none of these paths will log "No subproject checks apply" and merge without running tests — flag this to the user if it happens unexpectedly.
