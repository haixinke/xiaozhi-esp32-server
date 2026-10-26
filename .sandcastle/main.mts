// Sequential Reviewer — implement-then-review loop
//
// Phase 1 (Implement): agent works on the assigned issue on a dedicated branch,
//                      commits, and closes that issue.
// Phase 2 (Review):    a single-issue agent reviews the committed branch, may
//                      commit one verified blocking fix, and emits a structured
//                      report bound to its final SHA; final review stays read-only.
// Phase 3 (Merge):     host 仅合并验证和审查都通过的 commit。
//                      下一个 iteration 的 worktree 从新 HEAD 切出，
//                      能看到前一个 issue 已 close，避免重复处理。
// Phase 4 (Final):     review the complete batch on merged HEAD; blocking
//                      findings may enter a bounded fix/re-review loop.
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
import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { assertIssueReviewFixes, assertReviewPass, parseReviewReport, type ReviewReport } from "./review-contract.mts";

const execFileAsync = promisify(execFile);

// 每个 outer loop 处理一个 issue；无 commit 视为 no_progress 并阻止批次完成。
// RALPH 完成信号。implementer 可成功(IMPLEMENTED)或受阻(BLOCKED)；
// reviewer/fixer 只读或修复，只认 COMPLETE。
const IMPLEMENT_SIGNALS = ["<promise>IMPLEMENTED</promise>", "<promise>BLOCKED</promise>"];
const REVIEW_SIGNALS = ["<promise>COMPLETE</promise>"];
const BLOCKED_SIGNAL = "<promise>BLOCKED</promise>";

const MAX_ITERATIONS = 15;
const MAX_FIX_ROUNDS = 2;
const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const mavenCache = join(homedir(), ".m2");

// 沙箱内 Claude Code 用的模型标识，须命中 CCR 路由表。
// 通过环境变量覆盖：SANDCASTLE_MODEL=ModelRouter/qwen/ZHIPU/GLM-5.3 npx tsx .sandcastle/main.mts
// 同时透传给容器 ANTHROPIC_MODEL，保证 agent 启动与子进程默认一致。
const AGENT_MODEL = process.env.SANDCASTLE_MODEL ?? "Kimi/k3";
const featureFilter = process.env.SANDCASTLE_FEATURE;
if (featureFilter && !/^[a-zA-Z0-9_-]+$/.test(featureFilter)) throw new Error("Invalid SANDCASTLE_FEATURE");
const logDir = join(repoRoot, ".sandcastle", "logs");

type Batch = {
  id: string;
  branch: string;
  baseSha: string;
  issueIds: string[];
  specPaths: string[];
  specHashes: Record<string, string>;
  specRequirements: string[];
  status: "running" | "complete";
  lastOutcome?: "no_progress" | "blocked" | "iteration_limit" | "repair_limit" | "failed";
  // 本批次中 agent 明确标记 BLOCKED 的 issue，重启后跳过，避免重复烧 token。
  blockedIssueIds?: string[];
};

function digest(value: string) {
  return createHash("sha256").update(value).digest("hex");
}

async function assertSpecsUnchanged(batch: Batch, cwd: string) {
  for (const path of batch.specPaths) {
    if (digest(await readFile(join(cwd, path), "utf8")) !== batch.specHashes[path]) {
      throw new Error(`Feature spec changed during batch: ${path}`);
    }
  }
}

function issuePath(id: string) {
  if (!/^[a-zA-Z0-9_-]+\/issues\/[a-zA-Z0-9_-]+$/.test(id)) throw new Error(`Invalid issue ID: ${id}`);
  return join(repoRoot, ".scratch", `${id}.md`);
}

async function issueStatus(cwd: string, id: string) {
  const path = join(cwd, ".scratch", `${id}.md`);
  const body = await readFile(path, "utf8");
  const status = body.match(/^\*?\*?Status:\*?\*?\s*(\S+)/im)?.[1];
  if (!status) throw new Error(`Missing Status in ${id}`);
  return { body, status };
}

async function pendingIssues(cwd: string, ids: string[]) {
  const statuses = await Promise.all(ids.map(async (id) => ({ id, ...(await issueStatus(cwd, id)) })));
  return statuses.filter(({ status }) => status !== "resolved");
}

async function readyIssue(pending: Array<{ id: string; body: string }>) {
  for (const { id, body } of pending) {
    const value = body.match(/^\*?\*?Blocked by:\*?\*?\s*(.*)$/im)?.[1] ?? "";
    const blockers = [...value.matchAll(/[a-zA-Z0-9_-]+\/issues\/[a-zA-Z0-9_-]+|\b\d{2,3}\b/g)].map(([blocker]) => blocker);
    let blocked = false;
    for (const blocker of blockers) {
      let blockerId = blocker;
      if (!blocker.includes("/issues/")) {
        const feature = id.split("/")[0];
        const files = await readdir(join(repoRoot, ".scratch", feature, "issues"));
        const match = files.find((file) => file.startsWith(`${blocker}-`) && file.endsWith(".md"));
        if (!match) throw new Error(`Unknown blocker ${blocker} for ${id}`);
        blockerId = `${feature}/issues/${match.slice(0, -3)}`;
      }
      if ((await issueStatus(repoRoot, blockerId)).status !== "resolved") {
        blocked = true;
        break;
      }
    }
    if (!blocked) return pending.find((candidate) => candidate.id === id);
  }
  return undefined;
}

async function loadBatch(): Promise<Batch | undefined> {
  await requireClean(repoRoot);
  const branch = await git(repoRoot, ["symbolic-ref", "--short", "HEAD"]);
  const currentSha = await git(repoRoot, ["rev-parse", "HEAD"]);
  const statePath = join(logDir, `batch-${branch.replace(/[^a-zA-Z0-9_-]/g, "_")}-${featureFilter ?? "all"}.json`);
  let previous: Batch | undefined;
  try { previous = JSON.parse(await readFile(statePath, "utf8")); } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
  }
  if (previous?.status === "running") {
    if (previous.branch !== branch || !/^[0-9a-f]{40}$/.test(previous.baseSha) ||
        !Array.isArray(previous.issueIds) || !Array.isArray(previous.specRequirements) || !previous.specHashes) {
      throw new Error("Invalid batch manifest");
    }
    previous.issueIds.forEach(issuePath);
    await git(repoRoot, ["merge-base", "--is-ancestor", previous.baseSha, currentSha]);
    await Promise.all(previous.issueIds.map((id) => issueStatus(repoRoot, id)));
    await assertSpecsUnchanged(previous, repoRoot);
    return previous;
  }
  const { stdout } = await execFileAsync("bash", [".sandcastle/bin/scratch-issues.sh", "list"], { cwd: repoRoot });
  const issues: Array<{ id: string }> = JSON.parse(stdout);
  const issueIds = issues.map(({ id }) => id).filter((id) => !featureFilter || id.startsWith(`${featureFilter}/`));
  if (!issueIds.length) return undefined;
  issueIds.forEach(issuePath);
  const specPaths = [...new Set(issueIds.map((id) => `.scratch/${id.split("/")[0]}/spec.md`))];
  const specs = await Promise.all(specPaths.map(async (path) => ({ path, body: await readFile(join(repoRoot, path), "utf8") })));
  const specHashes = Object.fromEntries(specs.map(({ path, body }) => [path, digest(body)]));
  const specRequirements = specs.flatMap(({ path, body }) => {
    const items = body.split(/\r?\n/).flatMap((line, index) => {
      const number = line.match(/^\s*(\d+)\.\s+\S/);
      if (number) return [`${path}#${number[1]}`];
      const requirement = line.match(/^\s*(R\d+):\s+\S/i);
      if (requirement) return [`${path}#${requirement[1].toUpperCase()}`];
      if (/^\s*-\s*\[[ xX]\]\s+\S/.test(line)) return [`${path}#L${index + 1}`];
      return [];
    });
    return items.length ? [...new Set(items)] : [`${path}#document`];
  });
  const batch: Batch = { id: `${Date.now()}-${currentSha.slice(0, 8)}`, branch, baseSha: currentSha,
    issueIds, specPaths, specHashes, specRequirements, status: "running" };
  await mkdir(logDir, { recursive: true });
  await writeFile(statePath, JSON.stringify(batch, null, 2), { mode: 0o600 });
  return batch;
}

async function saveBatch(batch: Batch) {
  const path = join(logDir, `batch-${batch.branch.replace(/[^a-zA-Z0-9_-]/g, "_")}-${featureFilter ?? "all"}.json`);
  await writeFile(path, JSON.stringify(batch, null, 2), { mode: 0o600 });
}

// 通用审查产物沿用同一份脱敏规则；自检报告与 host 复验记录分开保存。
async function saveArtifact(batch: Batch, name: string, artifact: unknown) {
  const path = join(logDir, batch.id, `${name}.json`);
  await mkdir(dirname(path), { recursive: true });
  const sensitive = /password|passwd|secret|token|authorization|api[_-]?key|jdbc:|Bearer\s|:\/\/[^\s/]+:[^\s/]+@|eyJ[A-Za-z0-9_-]+\./i;
  const clean = JSON.stringify(artifact, (_key, value) =>
    typeof value === "string" && sensitive.test(value) ? "[redacted sensitive output]" : value, 2);
  await writeFile(path, clean, { mode: 0o600 });
}

async function saveReport(batch: Batch, name: string, report: ReviewReport) {
  await saveArtifact(batch, name, report);
}

type VerificationCheck = { command: string; label: string; logPath: string; status: "passed" };
type HostVerification = { phase: "issue-review"; baseSha: string; reviewedSha: string;
  issueIds: string[]; checks: VerificationCheck[] };

async function saveHostVerification(batch: Batch, reportName: string, record: HostVerification) {
  await saveArtifact(batch, `${reportName}-host-verification`, record);
}

// 仅用于可预期的单 issue 审查、提交形态和 host 复验门禁失败；基础设施异常不转换。
class ReviewGateError extends Error {
  constructor(issueId: string, reason: string, cause?: unknown) {
    super(`Issue review gate failed (${issueId}): ${reason}`, { cause });
    this.name = "ReviewGateError";
  }
}

async function reviewGate<T>(issueId: string, reason: string, check: () => Promise<T> | T): Promise<T> {
  try {
    return await check();
  } catch (error) {
    if (error instanceof ReviewGateError) throw error;
    if (error instanceof Error) throw new ReviewGateError(issueId, reason, error);
    throw error;
  }
}

// 多子项目校验矩阵：按 changed paths 分发到对应子项目的检查命令。
// 顺序即执行顺序，与 implement-prompt.md / issue-review-prompt.md 中的描述保持一致。
// 注意：sandbox.exec() 非零 exitCode 是返回不抛出，每个 check 必须显式判断。
const CHECKS: Array<{ prefix: string; label: string; cmd: string }> = [
  {
    prefix: "main/manager-api/",
    label: "manager-api (Java)",
    cmd: "mvn -B -ntp -f main/manager-api/pom.xml -Dmaven.repo.local=/home/agent/.m2/repository -DskipTests=false -Dmaven.test.skip=false -DfailIfNoTests=true -Dtest='!xiaozhi.modules.pdc.nfc.service.PdcNfcTrustReleaseIntegrationTest,!xiaozhi.modules.pdc.nfc.service.PdcNfcClaimInviteReplacementIntegrationTest,!xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultImportIntegrationTest' clean verify",
  },
  {
    prefix: "main/manager-web/",
    label: "manager-web (Vue2)",
    // node_modules 由 sandbox.onSandboxReady hook 预装
    cmd: "cd main/manager-web && npm run test:unit && npm run check:i18n && npm run check:template",
  },
  {
    prefix: "main/egg-miniprogram/",
    label: "egg-miniprogram (微信小程序)",
    cmd: "node main/egg-miniprogram/scripts/verify-project.js",
  },
  {
    prefix: "main/xiaozhi-server/",
    label: "xiaozhi-server (Python)",
    // 只做语法级检查；完整 pytest 依赖重（onnxruntime/powermem），留给 host 人工跑。
    cmd: "python3.12 -m compileall -q main/xiaozhi-server/core/ main/xiaozhi-server/config/ main/xiaozhi-server/plugins_func/",
  },
];

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
  const changedPaths = changed.split("\0").filter(Boolean);
  const applicable = CHECKS.filter((c) => changedPaths.some((p) => p.startsWith(c.prefix)));
  const checks: Array<{ command: string; label: string; logPath: string; status: "passed" }> = [];

  await mkdir(logDir, { recursive: true });

  if (!applicable.length) {
    console.log(`No subproject checks apply after ${phase}; verification skipped.`);
  }

  for (const check of applicable) {
    console.log(`Verifying ${check.label} after ${phase} (${sha})...`);
    const result = await sandbox.exec(check.cmd);
    const logPath = join(logDir, `${sha}-${phase}-${check.label.split(" ")[0]}-verify.log`);
    // Test output may contain application configuration or credentials.
    const output = `${result.stdout}\n${result.stderr}`.split("\n").map((line) =>
      /password|passwd|secret|token|authorization|api[_-]?key|jdbc:|Bearer\s|:\/\/[^\s/]+:[^\s/]+@|eyJ[A-Za-z0-9_-]+\./i.test(line)
        ? "[redacted sensitive output]" : line,
    ).join("\n");
    await writeFile(logPath, `commit=${sha}\ncommand=${check.cmd}\nexitCode=${result.exitCode}\n${output}`, { mode: 0o600 });
    // sandbox.exec() 非零 exitCode 是返回不抛出 —— 必须显式判断才能拦截 merge。
    if (result.exitCode !== 0) {
      throw new Error(`${check.label} verification failed after ${phase} (exit ${result.exitCode}); merge blocked. Log: ${logPath}`);
    }
    console.log(`  passed. Log: ${logPath}`);
    checks.push({ command: check.cmd, label: check.label, logPath, status: "passed" });
  }

  await requireClean(sandbox.worktreePath);
  if (await git(sandbox.worktreePath, ["rev-parse", "HEAD"]) !== sha) {
    throw new Error("Sandbox HEAD changed during verification; merge blocked.");
  }
  return { sha, checks };
}

function branchName(phase: string) {
  return `sandcastle/${phase}/${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function createSandbox(branch: string) {
  return sandcastle.createSandbox({
    branch,
    cwd: repoRoot,
    sandbox: docker({
      mounts: [
        { hostPath: mavenCache, sandboxPath: "/home/agent/.m2", readonly: false },
        // manager-web 的 npm 依赖缓存挂 host，容器内 npm ci 复用 tar 包不重下
        { hostPath: join(homedir(), ".npm"), sandboxPath: "/home/agent/.npm", readonly: false },
      ],
      env: {
        // 容器内 127.0.0.1 指自己，经 host.docker.internal 访问 host 上的 OceanBase / Redis
        DB_HOST: "host.docker.internal",
        REDIS_HOST: "host.docker.internal",
        // 容器 Claude Code 默认模型。显式传入覆盖 .sandcastle/.env 里的静态值，
        // 保证 SANDCASTLE_MODEL 命令行切换对子进程同样生效。
        ANTHROPIC_MODEL: AGENT_MODEL,
      },
    }),
    hooks: {
      sandbox: {
        onSandboxReady: [
          // manager-web 变更走 npm test 门禁，依赖必须在沙箱起来后先装好。
          // 每次都装：worktree 是新的，npm ci 幂等。
          { command: "cd main/manager-web && npm ci --prefer-offline --no-audit --no-fund", timeoutMs: 600_000 },
        ],
      },
    },
  });
}

async function preflight(sandbox: sandcastle.Sandbox) {
  const result = await sandbox.exec('java -version && javac -version && mvn --version && python3.12 --version && test -w "$HOME/.m2"');
  if (result.exitCode !== 0) {
    throw new Error("Java/Maven/Python3.12 or writable ~/.m2 unavailable; rebuild the Sandcastle image and check cache permissions.");
  }
}

async function assertOtherIssuesUnchanged(batch: Batch, cwd: string, issueId: string, phase: string) {
  for (const otherId of batch.issueIds.filter((id) => id !== issueId)) {
    const relative = join(".scratch", `${otherId}.md`);
    if (await readFile(join(repoRoot, relative), "utf8") !== await readFile(join(cwd, relative), "utf8")) {
      throw new Error(`${phase} changed another target issue without review: ${otherId}`);
    }
  }
}

async function reviewIssue(
  sandbox: sandcastle.Sandbox,
  batch: Batch,
  options: { baseSha: string; implementedSha: string; issueId: string; reportName: string; checks: VerificationCheck[] },
) {
  await requireClean(sandbox.worktreePath);
  const result = await sandbox.run({
    name: "reviewer",
    maxIterations: 1,
    completionSignal: REVIEW_SIGNALS,
    agent: sandcastle.claudeCode(AGENT_MODEL),
    promptFile: join(repoRoot, ".sandcastle", "issue-review-prompt.md"),
    promptArgs: {
      BASE_SHA: options.baseSha,
      IMPLEMENTED_SHA: options.implementedSha,
      ISSUE_ID: options.issueId,
      ISSUE_PATH: `.scratch/${options.issueId}.md`,
      SPEC_PATHS: batch.specPaths.join(" "),
      VERIFICATION_RESULTS: JSON.stringify(options.checks),
    },
  });
  const reviewedSha = await git(sandbox.worktreePath, ["rev-parse", "HEAD"]);
  let report: ReviewReport;
  try {
    report = parseReviewReport(result.stdout);
  } catch (error) {
    throw new ReviewGateError(options.issueId, "invalid or missing self-review report", error);
  }
  // 无论 reviewer verdict 如何，先落盘可解析报告，便于人工检查部分修复。
  await saveReport(batch, options.reportName, report);
  const worktree = await git(sandbox.worktreePath, ["status", "--porcelain", "--untracked-files=all"]);
  if (worktree) throw new ReviewGateError(options.issueId, "reviewer left an unclean worktree");
  try {
    await git(sandbox.worktreePath, ["merge-base", "--is-ancestor", options.implementedSha, reviewedSha]);
    await git(sandbox.worktreePath, ["merge-base", "--is-ancestor", options.baseSha, reviewedSha]);
  } catch (error) {
    throw new ReviewGateError(options.issueId, "repair history must descend from implementation and baseline", error);
  }
  const repairList = await git(sandbox.worktreePath, ["rev-list", "--reverse", `${options.implementedSha}..${reviewedSha}`]);
  const repairCommits = repairList ? repairList.split("\n") : [];
  // 先验证真实提交和范围，再判断 pass，避免失败报告掩盖越权或伪造 SHA。
  await reviewGate(options.issueId, "declared fixes do not match repair commits", () =>
    assertIssueReviewFixes(report, { implementedSha: options.implementedSha, reviewedSha, repairCommits }));
  await assertOtherIssuesUnchanged(batch, sandbox.worktreePath, options.issueId, "Issue review");
  const currentIssue = await issueStatus(sandbox.worktreePath, options.issueId);
  if (currentIssue.status !== "resolved") throw new ReviewGateError(options.issueId, "current issue is not resolved");
  await assertSpecsUnchanged(batch, sandbox.worktreePath);
  // 自检只证明 reviewer 已执行的检查；修复后 host 结果由 runner 独立产生。
  await reviewGate(options.issueId, "self-review did not pass", () =>
    assertReviewPass(report, { baseSha: options.baseSha, reviewedSha, issueIds: [options.issueId] }));
  return { report, reviewedSha };
}

async function review(
  sandbox: sandcastle.Sandbox,
  batch: Batch,
  options: { name: string; promptFile: string; baseSha: string; issueIds: string[]; reportName: string;
    checks: Array<{ command: string; label: string; logPath: string; status: "passed" }>;
    requirementIds?: string[] },
) {
  const reviewedSha = await git(sandbox.worktreePath, ["rev-parse", "HEAD"]);
  await requireClean(sandbox.worktreePath);
  const result = await sandbox.run({
    name: options.name,
    maxIterations: 1,
    completionSignal: REVIEW_SIGNALS,
    agent: sandcastle.claudeCode(AGENT_MODEL),
    promptFile: join(repoRoot, ".sandcastle", options.promptFile),
    promptArgs: {
      BASE_SHA: options.baseSha,
      REVIEWED_SHA: reviewedSha,
      ISSUE_IDS: options.issueIds.join(","),
      ISSUE_PATHS: options.issueIds.map((id) => `.scratch/${id}.md`).join(" "),
      SPEC_PATHS: batch.specPaths.join(" "),
      REQUIREMENT_IDS: (options.requirementIds ?? options.issueIds).join(","),
      VERIFICATION_RESULTS: JSON.stringify(options.checks),
    },
  });
  await requireClean(sandbox.worktreePath);
  if (await git(sandbox.worktreePath, ["rev-parse", "HEAD"]) !== reviewedSha) {
    throw new Error(`${options.name} changed HEAD; read-only review required`);
  }
  const report = parseReviewReport(result.stdout);
  await saveReport(batch, options.reportName, report);
  return { report, reviewedSha };
}

async function mergeVerified(baseSha: string, branch: string, verifiedSha: string, hostBranch: string) {
  await requireClean(repoRoot);
  if (await git(repoRoot, ["rev-parse", "HEAD"]) !== baseSha ||
      await git(repoRoot, ["symbolic-ref", "--short", "HEAD"]) !== hostBranch) {
    throw new Error("Host branch changed while agents were running; merge blocked. Rebase and verify again.");
  }
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

async function runBatch(batch: Batch) {
  const hostBranch = batch.branch;
  const blockedIssueIds = new Set(batch.blockedIssueIds ?? []);
  let hitIterationLimit = true;
  for (let iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
    const pending = await pendingIssues(repoRoot, batch.issueIds);
    const actionable = pending.filter(({ id }) => !blockedIssueIds.has(id));
    if (!actionable.length) {
      hitIterationLimit = false;
      break;
    }
    const issue = await readyIssue(actionable);
    if (!issue) {
      hitIterationLimit = false;
      break;
    }
    console.log(`\n=== Issue ${iteration}/${MAX_ITERATIONS}: ${issue.id} ===\n`);
    const branch = branchName("sequential-reviewer");
    const baseSha = await git(repoRoot, ["rev-parse", "HEAD"]);
    const sandbox = await createSandbox(branch);
    let verifiedSha: string;
    let cleanBlocked = false;
    try {
      await preflight(sandbox);
      const implement = await sandbox.run({
        name: "implementer",
        maxIterations: 1,
        completionSignal: IMPLEMENT_SIGNALS,
        agent: sandcastle.claudeCode(AGENT_MODEL),
        promptFile: join(repoRoot, ".sandcastle", "implement-prompt.md"),
        promptArgs: { ISSUE_ID: issue.id, SPEC_PATH: batch.specPaths.find((path) => path.includes(`/${issue.id.split("/")[0]}/`)) ?? "" },
      });
      if (implement.completionSignal === BLOCKED_SIGNAL) {
        await requireClean(sandbox.worktreePath);
        if (implement.commits.length) {
          throw new Error(`Implementer reported BLOCKED with commits on ${issue.id}; merge blocked`);
        }
        if ((await issueStatus(sandbox.worktreePath, issue.id)).status === "resolved") {
          throw new Error(`Implementer reported BLOCKED but closed ${issue.id}; batch remains incomplete`);
        }
        blockedIssueIds.add(issue.id);
        batch.blockedIssueIds = [...blockedIssueIds];
        await saveBatch({ ...batch, blockedIssueIds: batch.blockedIssueIds, lastOutcome: "blocked" });
        console.log(`Issue ${issue.id} blocked; continuing with other issues.`);
        cleanBlocked = true;
        continue;
      }
      if (!implement.commits.length) {
        await requireClean(sandbox.worktreePath);
        throw new Error(`No progress on ${issue.id}; batch remains incomplete`);
      }
      if ((await issueStatus(sandbox.worktreePath, issue.id)).status !== "resolved") {
        throw new Error(`Implementation did not resolve ${issue.id}`);
      }
      await assertOtherIssuesUnchanged(batch, sandbox.worktreePath, issue.id, "Implementation");
      await assertSpecsUnchanged(batch, sandbox.worktreePath);
      const implemented = await verify(sandbox, baseSha, "implement");
      verifiedSha = implemented.sha;
      const reviewed = await reviewIssue(sandbox, batch, {
        baseSha, implementedSha: implemented.sha, issueId: issue.id,
        reportName: `issue-${iteration}`, checks: implemented.checks,
      });
      let hostVerification: Awaited<ReturnType<typeof verify>>;
      try {
        // 第二段通过判定：只有 reviewer 最终 SHA 的独立 host 复验成功才可合并。
        hostVerification = await verify(sandbox, baseSha, "review");
      } catch (error) {
        if (error instanceof Error && error.message.startsWith("Sandbox HEAD changed during verification;")) {
          throw new ReviewGateError(issue.id, "post-review host verification failed", error);
        }
        throw error;
      }
      if (hostVerification.sha !== reviewed.reviewedSha) {
        throw new ReviewGateError(issue.id, "host verification SHA changed");
      }
      verifiedSha = hostVerification.sha;
      await saveHostVerification(batch, `issue-${iteration}`, {
        phase: "issue-review", baseSha, reviewedSha: reviewed.reviewedSha,
        issueIds: [issue.id], checks: hostVerification.checks,
      });
    } finally {
      await sandbox.close();
    }
    if (cleanBlocked) continue;
    await mergeVerified(baseSha, branch, verifiedSha, hostBranch);
  }
  if (hitIterationLimit && (await pendingIssues(repoRoot, batch.issueIds)).length) {
    throw new Error(`Iteration limit reached with unresolved issues in batch ${batch.id}`);
  }
  const unresolved = (await pendingIssues(repoRoot, batch.issueIds))
    .filter(({ id }) => !blockedIssueIds.has(id));
  if (unresolved.length) {
    throw new Error(`All remaining issues are blocked in batch ${batch.id}: ${unresolved.map(({ id }) => id).join(", ")}`);
  }
  if (blockedIssueIds.size) {
    throw new Error(`Batch ${batch.id} incomplete: blocked issues: ${[...blockedIssueIds].join(", ")}`);
  }

  for (let round = 0; round <= MAX_FIX_ROUNDS; round++) {
    const head = await git(repoRoot, ["rev-parse", "HEAD"]);
    const sandbox = await createSandbox(branchName("final-review"));
    let report: ReviewReport;
    let finalChecks: string[] = [];
    try {
      await preflight(sandbox);
      await assertSpecsUnchanged(batch, sandbox.worktreePath);
      const verified = await verify(sandbox, batch.baseSha, `final-${round}`);
      finalChecks = verified.checks.map((check) => check.command);
      const reviewed = await review(sandbox, batch, {
        name: "final-reviewer", promptFile: "final-review-prompt.md", baseSha: batch.baseSha,
        issueIds: batch.issueIds, reportName: `final-${round}`, checks: verified.checks,
        requirementIds: [...batch.issueIds, ...batch.specRequirements],
      });
      report = reviewed.report;
      if (reviewed.reviewedSha !== head) throw new Error("Final review did not inspect host HEAD");
      await requireClean(sandbox.worktreePath);
    } finally {
      await sandbox.close();
    }
    if (await git(repoRoot, ["rev-parse", "HEAD"]) !== head ||
        await git(repoRoot, ["symbolic-ref", "--short", "HEAD"]) !== hostBranch) {
      throw new Error("Host HEAD changed during final review; report invalid");
    }
    if (report.verdict === "pass") {
      assertReviewPass(report, { baseSha: batch.baseSha, reviewedSha: head, issueIds: batch.issueIds,
        requirementIds: [...batch.issueIds, ...batch.specRequirements], checkedCommands: finalChecks });
      if ((await pendingIssues(repoRoot, batch.issueIds)).length) {
        throw new Error("Final review cannot complete while target issues remain open");
      }
      await saveBatch({ ...batch, status: "complete", lastOutcome: undefined });
      console.log(`Batch ${batch.id} complete at ${head}`);
      return;
    }
    if (report.verdict !== "changes_required" || !report.findings.some((finding) => finding.severity === "blocking")) {
      throw new Error(`Final review blocked at ${head}; inspect report in ${join(logDir, batch.id)}`);
    }
    if (round === MAX_FIX_ROUNDS) throw new Error(`Final repair limit reached for batch ${batch.id}`);

    const branch = branchName("final-fix");
    const fixSandbox = await createSandbox(branch);
    let verifiedSha: string;
    try {
      await preflight(fixSandbox);
      const fix = await fixSandbox.run({
        name: "fixer", maxIterations: 1, completionSignal: REVIEW_SIGNALS, agent: sandcastle.claudeCode(AGENT_MODEL),
        promptFile: join(repoRoot, ".sandcastle", "fix-review-prompt.md"),
        promptArgs: { REVIEWED_SHA: head, FINDINGS: JSON.stringify(report.findings), ISSUE_IDS: batch.issueIds.join(",") },
      });
      if (!fix.commits.length) throw new Error("Final fixer made no commits");
      await assertSpecsUnchanged(batch, fixSandbox.worktreePath);
      const fixed = await verify(fixSandbox, head, `fix-${round}`);
      verifiedSha = fixed.sha;
      const reviewed = await review(fixSandbox, batch, {
        name: "fix-reviewer", promptFile: "review-prompt.md", baseSha: head,
        issueIds: batch.issueIds, reportName: `fix-${round}`, checks: fixed.checks,
      });
      assertReviewPass(reviewed.report, { baseSha: head, reviewedSha: verifiedSha, issueIds: batch.issueIds,
        checkedCommands: fixed.checks.map((check) => check.command) });
      verifiedSha = (await verify(fixSandbox, head, `fix-review-${round}`)).sha;
    } finally {
      await fixSandbox.close();
    }
    await mergeVerified(head, branch, verifiedSha, hostBranch);
  }
}

const batch = await loadBatch();
if (batch) {
  try {
    await runBatch(batch);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    const lastOutcome = message.startsWith("No progress") ? "no_progress"
      : message.startsWith("All remaining issues are blocked") ? "blocked"
      : message.startsWith("Implementer reported BLOCKED") ? "blocked"
      : message.startsWith("Batch ") && message.includes("incomplete: blocked issues") ? "blocked"
      : message.startsWith("Iteration limit") ? "iteration_limit"
      : message.startsWith("Final repair limit") ? "repair_limit" : "failed";
    await saveBatch({ ...batch, lastOutcome });
    throw error;
  }
} else console.log("No open issues in scope; nothing to review.");
