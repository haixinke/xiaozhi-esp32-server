// Sequential Reviewer — implement-then-review loop
//
// Phase 1 (Implement): sonnet agent picks an open issue, works on it on a
//                      dedicated branch, commits, signals completion.
// Phase 2 (Review):    second sonnet agent reviews the branch diff, approves
//                      or corrects directly on the branch.
// Phase 3 (Merge):     host 仅合并两轮验证通过的 commit。
//                      下一个 iteration 的 worktree 从新 HEAD 切出，
//                      能看到前一个 issue 已 close，避免重复处理。
//
// Both phases share one sandbox via createSandbox() on a named branch.
//
// Usage:
//   npx tsx .sandcastle/main.mts
// Or from .sandcastle/:
//   npm run sandcastle

import * as sandcastle from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";
import { execFile } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

// 每个 outer loop 处理一个 issue；实现阶段没 commit 就停（backlog 空）。
const MAX_ITERATIONS = 10;
const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const mavenCache = join(homedir(), ".m2");
const verifyCommand = "mvn -B -ntp -f main/manager-api/pom.xml -Dmaven.repo.local=/home/agent/.m2/repository -DskipTests=false -Dmaven.test.skip=false -DfailIfNoTests=true clean verify";

await mkdir(mavenCache, { recursive: true });

async function git(cwd: string, args: string[]) {
  const { stdout } = await execFileAsync("git", args, { cwd });
  return stdout.trim();
}

async function requireClean(cwd: string) {
  if (await git(cwd, ["status", "--porcelain", "--untracked-files=all"])) {
    throw new Error(`Uncommitted changes in ${cwd}; commit or stash them before verification/merge.`);
  }
}

async function verify(sandbox: sandcastle.Sandbox, baseSha: string, phase: string) {
  await requireClean(sandbox.worktreePath);
  const sha = await git(sandbox.worktreePath, ["rev-parse", "HEAD"]);
  try {
    await git(sandbox.worktreePath, ["merge-base", "--is-ancestor", baseSha, sha]);
  } catch {
    throw new Error("Sandbox no longer descends from the host baseline; merge blocked. Rebase and verify again.");
  }
  const changed = await git(sandbox.worktreePath, ["diff", "--name-only", "--no-renames", "-z", baseSha, sha]);
  if (changed.split("\0").some((path) => path.startsWith("main/manager-api/"))) {
    console.log(`Verifying manager-api after ${phase} (${sha})...`);
    const result = await sandbox.exec(verifyCommand);
    const logDir = join(repoRoot, ".sandcastle", "logs");
    await mkdir(logDir, { recursive: true });
    const logPath = join(logDir, `${sha}-${phase}-verify.log`);
    // Test output may contain application configuration or credentials.
    const output = `${result.stdout}\n${result.stderr}`.split("\n").map((line) =>
      /password|passwd|secret|token|authorization|api[_-]?key|jdbc:|Bearer\s|:\/\/[^\s/]+:[^\s/]+@|eyJ[A-Za-z0-9_-]+\./i.test(line)
        ? "[redacted sensitive output]" : line,
    ).join("\n");
    await writeFile(logPath, `commit=${sha}\ncommand=${verifyCommand}\nexitCode=${result.exitCode}\n${output}`, { mode: 0o600 });
    if (result.exitCode !== 0) {
      throw new Error(`manager-api verification failed after ${phase} (exit ${result.exitCode}); merge blocked. Log: ${logPath}`);
    }
    console.log(`Verification passed. Log: ${logPath}`);
  } else {
    console.log(`No manager-api changes after ${phase}; Java verification not applicable.`);
  }
  await requireClean(sandbox.worktreePath);
  if (await git(sandbox.worktreePath, ["rev-parse", "HEAD"]) !== sha) {
    throw new Error("Sandbox HEAD changed during verification; merge blocked.");
  }
  return sha;
}

for (let iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
  console.log(`\n=== Iteration ${iteration}/${MAX_ITERATIONS} ===\n`);

  const branch = `sandcastle/sequential-reviewer/${Date.now()}`;
  await requireClean(repoRoot);
  const baseSha = await git(repoRoot, ["rev-parse", "HEAD"]);
  const hostBranch = await git(repoRoot, ["symbolic-ref", "--short", "HEAD"]);

  // implementer 和 reviewer 共享同一 sandbox + branch。
  const sandbox = await sandcastle.createSandbox({
    branch,
    cwd: repoRoot,
    sandbox: docker({
      mounts: [{ hostPath: mavenCache, sandboxPath: "/home/agent/.m2", readonly: false }],
    }),
  });

  let verifiedSha: string;
  try {
    const preflight = await sandbox.exec('java -version && javac -version && mvn --version && test -w "$HOME/.m2"');
    if (preflight.exitCode !== 0) {
      throw new Error("Java/Maven or writable ~/.m2 unavailable; rebuild the Sandcastle image and check cache permissions.");
    }
    // Phase 1: 实现。模型名必须在 CCR 路由表内。
    const implement = await sandbox.run({
      name: "implementer",
      maxIterations: 1,
      agent: sandcastle.claudeCode("anthropic/claude-sonnet-4.6"),
      promptFile: join(repoRoot, ".sandcastle", "implement-prompt.md"),
    });

    if (!implement.commits.length) {
      await requireClean(sandbox.worktreePath);
      console.log("Implementation agent made no commits. Stopping.");
      break;
    }

    console.log(`\nImplementation complete on branch: ${branch}`);
    console.log(`Commits: ${implement.commits.length}`);
    await verify(sandbox, baseSha, "implement");

    // Phase 2: review。
    await sandbox.run({
      name: "reviewer",
      maxIterations: 1,
      agent: sandcastle.claudeCode("anthropic/claude-sonnet-4.6"),
      promptFile: join(repoRoot, ".sandcastle", "review-prompt.md"),
      promptArgs: {
        BRANCH: branch,
      },
    });

    console.log("\nReview complete.");
    verifiedSha = await verify(sandbox, baseSha, "review");
  } finally {
    await sandbox.close();
  }

  await requireClean(repoRoot);
  if (await git(repoRoot, ["rev-parse", "HEAD"]) !== baseSha ||
      await git(repoRoot, ["symbolic-ref", "--short", "HEAD"]) !== hostBranch) {
    throw new Error("Host branch changed while agents were running; merge blocked. Rebase and verify again.");
  }
  // Phase 3: host 侧 merge —— 让下一轮 worktree 基于含最新 close 的 HEAD。
  // --no-ff 保留分支历史，便于回溯每个 issue 的实现+review 边界。
  // 冲突时退出（不自动解），让人工介入；已做的 commit 仍在 branch 上不丢。
  try {
    const { stdout } = await execFileAsync("git", [
      "merge",
      "--no-ff",
      verifiedSha,
      "-m",
      `merge: ${branch}`,
    ], { cwd: repoRoot });
    console.log(`\nMerged ${branch}:`);
    console.log(stdout);
  } catch (err) {
    console.error(`\nMerge failed for ${branch}. Resolve manually:`);
    console.error(`  git status`);
    console.error(`  # fix conflicts, then: git merge --continue`);
    console.error(`  # or abort: git merge --abort && git branch -D ${branch}`);
    throw err;
  }
}

console.log("\nAll done.");
