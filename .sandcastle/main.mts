// Sequential Reviewer — implement-then-review loop
//
// Phase 1 (Implement): sonnet agent picks an open issue, works on it on a
//                      dedicated branch, commits, signals completion.
// Phase 2 (Review):    second sonnet agent reviews the branch diff, approves
//                      or corrects directly on the branch.
//
// Both phases share one sandbox via createSandbox() on a named branch.
//
// Usage:
//   npx tsx .sandcastle/main.mts
// Or from .sandcastle/:
//   npm run sandcastle

import * as sandcastle from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";

// 每个 outer loop 处理一个 issue；实现阶段没 commit 就停（backlog 空）。
const MAX_ITERATIONS = 10;

// 本仓不是 Node 项目：不跑 npm install / 不拷 node_modules。

for (let iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
  console.log(`\n=== Iteration ${iteration}/${MAX_ITERATIONS} ===\n`);

  const branch = `sandcastle/sequential-reviewer/${Date.now()}`;

  // implementer 和 reviewer 共享同一 sandbox + branch。
  const sandbox = await sandcastle.createSandbox({
    branch,
    sandbox: docker(),
  });

  try {
    // Phase 1: 实现。模型名必须在 CCR 路由表内。
    const implement = await sandbox.run({
      name: "implementer",
      maxIterations: 1,
      agent: sandcastle.claudeCode("anthropic/claude-sonnet-4.6"),
      promptFile: "./.sandcastle/implement-prompt.md",
    });

    if (!implement.commits.length) {
      console.log("Implementation agent made no commits. Stopping.");
      break;
    }

    console.log(`\nImplementation complete on branch: ${branch}`);
    console.log(`Commits: ${implement.commits.length}`);

    // Phase 2: review。
    await sandbox.run({
      name: "reviewer",
      maxIterations: 1,
      agent: sandcastle.claudeCode("anthropic/claude-sonnet-4.6"),
      promptFile: "./.sandcastle/review-prompt.md",
      promptArgs: {
        BRANCH: branch,
      },
    });

    console.log("\nReview complete.");
  } finally {
    await sandbox.close();
  }
}

console.log("\nAll done.");
