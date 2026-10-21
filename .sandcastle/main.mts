import { run, claudeCode } from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";

// Simple loop: an agent that picks open issues one by one and closes them.
// Run this with: npx tsx .sandcastle/main.mts
// Or add to package.json scripts: "sandcastle": "npx tsx .sandcastle/main.mts"

await run({
  // A name for this run, shown as a prefix in log output.
  name: "worker",

  // Sandbox provider — runs the agent inside an isolated container.
  // .scratch/ 已入 git，worktree 自带；agent close 落 worktree 副本，
  // merge-to-head 合并回 HEAD 时同步回 host 分支。
  sandbox: docker(),

  // 模型名必须在 CCR 路由表里（anthropic/claude-opus-4.6, anthropic/claude-sonnet-4.6, glm-5…），
  // 否则 CCR 报 "Model not configured for target provider"。
  agent: claudeCode("anthropic/claude-sonnet-4.6"),

  // Path to the prompt file. Shell expressions inside are evaluated inside the
  // sandbox at the start of each iteration, so the agent always sees fresh data.
  promptFile: "./.sandcastle/prompt.md",

  // Maximum number of iterations (agent invocations) to run in a session.
  // Each iteration works on a single issue. Increase this to process more issues
  // per run, or set it to 1 for a single-shot mode.
  maxIterations: 3,

  // Branch strategy — merge-to-head creates a temporary branch for the agent
  // to work on, then merges the result back to HEAD when the run completes.
  // This is required when using copyToWorktree, since head mode bind-mounts
  // the host directory directly (no worktree to copy into).
  branchStrategy: { type: "merge-to-head" },

  // 本仓根无 package.json（非 Node 项目），不跑 npm install / 不拷 node_modules。
});
