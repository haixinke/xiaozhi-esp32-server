import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const source = dirname(fileURLToPath(import.meta.url));
const git = (cwd: string, ...args: string[]) => execFileSync('git', args, {
  cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
}).trim();

function runFixture(flags: Record<string, string> = {}, fromConfigDir = false) {
  const root = mkdtempSync(join(tmpdir(), 'sandcastle-gate-'));
  try {
    const config = join(root, '.sandcastle');
    const pkg = join(config, 'node_modules/@ai-hero/sandcastle');
    mkdirSync(pkg, { recursive: true });
    mkdirSync(join(config, 'bin'), { recursive: true });
    mkdirSync(join(root, 'bin'));
    mkdirSync(join(root, 'home'));
    mkdirSync(join(root, 'main/manager-api'), { recursive: true });
    mkdirSync(join(root, '.scratch/test-batch/issues'), { recursive: true });
    copyFileSync(join(source, 'main.mts'), join(config, 'main.mts'));
    copyFileSync(join(source, 'bin/scratch-issues.sh'), join(config, 'bin/scratch-issues.sh'));
    for (const file of ['review-contract.mts', 'implement-prompt.md', 'issue-review-prompt.md', 'review-prompt.md', 'final-review-prompt.md', 'fix-review-prompt.md']) {
      const path = join(source, file);
      if (existsSync(path)) copyFileSync(path, join(config, file));
    }
    copyFileSync(join(source, 'test-support/sandcastle.mjs'), join(pkg, 'index.mjs'));
    writeFileSync(join(pkg, 'package.json'), JSON.stringify({
      type: 'module', exports: { '.': './index.mjs', './sandboxes/docker': './index.mjs' },
    }));
    for (const tool of ['java', 'javac', 'python3.12']) {
      writeFileSync(join(root, 'bin', tool), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
    }
    writeFileSync(join(root, 'bin/npm'), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
    writeFileSync(join(root, 'bin/node'), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
    writeFileSync(join(root, 'bin/mvn'), `#!/bin/sh
case "$*" in *--version*) exit 0 ;; esac
printf '%s\\n' "$*" >> "$TEST_REPO/maven-calls.txt"
case "$*" in *-DskipTests=false*) ;; *) exit 41 ;; esac
case "$*" in *-Dmaven.test.skip=false*) ;; *) exit 42 ;; esac
case "$*" in *-Dmaven.repo.local=/home/agent/.m2/repository*) ;; *) exit 45 ;; esac
case "$*" in *verify*) ;; *) exit 43 ;; esac
test -f main/manager-api/pom.xml || exit 44
case "$TEST_FAIL_PHASE" in
  implement) exit 1 ;;
  review) test ! -f review.txt || exit 1 ;;
esac
exit 0
`, { mode: 0o755 });
    writeFileSync(join(root, '.gitignore'), '.sandcastle/\nbin/\nhome/\nevents.jsonl\nmaven-calls.txt\nworktree-*/\n');
    writeFileSync(join(root, 'README.md'), 'fixture\n');
    writeFileSync(join(root, 'main/manager-api/pom.xml'), '<project/>\n');
    writeFileSync(join(root, '.scratch/test-batch/spec.md'), '# Batch spec\n\nR1: Complete the example change.\n');
    if (flags.TEST_NUMBERED_SPEC === '1') {
      writeFileSync(join(root, '.scratch/test-batch/spec.md'), '# Batch spec\n\n1. Complete the example change.\n');
    }
    writeFileSync(join(root, '.scratch/test-batch/issues/01-example.md'), '# Example change\n\n**Status:** open\n\nAcceptance: R1 is implemented.\n');
    if (flags.TEST_TWO_ISSUES === '1') {
      writeFileSync(join(root, '.scratch/test-batch/issues/02-follow-up.md'), '# Follow-up change\n\n**Status:** open\n\nAcceptance: R2 is implemented.\n');
      writeFileSync(join(root, '.scratch/test-batch/spec.md'), '# Batch spec\n\nR1: Complete the example change.\nR2: Complete the follow-up change.\n');
      if (flags.TEST_FIRST_BLOCKED === '1') {
        writeFileSync(join(root, '.scratch/test-batch/issues/01-example.md'), '# Example change\n\n**Blocked by:** 02\n\n**Status:** open\n\nAcceptance: R1 is implemented.\n');
      }
    }
    git(root, 'init', '-b', 'main');
    git(root, 'config', 'user.email', 'test@example.invalid');
    git(root, 'config', 'user.name', 'Sandcastle Test');
    git(root, 'config', 'commit.gpgsign', 'false');
    git(root, 'config', 'core.hooksPath', '/dev/null');
    git(root, 'add', '.');
    git(root, 'commit', '-m', 'initial');
    writeFileSync(join(root, 'host-baseline.txt'), 'host baseline\n');
    git(root, 'add', '.');
    git(root, 'commit', '-m', 'baseline');
    const result = spawnSync(process.execPath, ['--import', import.meta.resolve('tsx'), join(config, 'main.mts')], {
      cwd: fromConfigDir ? config : root,
      env: { ...process.env, HOME: join(root, 'home'), TEST_REPO: root, ...flags },
      encoding: 'utf8', timeout: 20_000,
    });
    const eventsPath = join(root, 'events.jsonl');
    const events = existsSync(eventsPath)
      ? readFileSync(eventsPath, 'utf8').trim().split('\n').filter(Boolean).map((line) => JSON.parse(line))
      : [];
    const logRoot = join(config, 'logs');
    const reportDirs = existsSync(logRoot) ? readdirSync(logRoot, { withFileTypes: true }).filter((entry) => entry.isDirectory()) : [];
    const savedArtifacts = reportDirs.flatMap((entry) => readdirSync(join(logRoot, entry.name))
      .filter((name) => name.endsWith('.json'))
      .map((name) => ({ name, ...JSON.parse(readFileSync(join(logRoot, entry.name, name), 'utf8')) })));
    const savedReports = savedArtifacts.map(({ name: _name, ...report }) => report);
    const batchStatePath = join(logRoot, 'batch-main-all.json');
    const batchState = existsSync(batchStatePath) ? JSON.parse(readFileSync(batchStatePath, 'utf8')) : undefined;
    return {
      status: result.status,
      output: result.stdout + result.stderr,
      events,
      merges: git(root, 'rev-list', '--merges', 'HEAD'),
      log: git(root, 'log', '--format=%s'),
      head: git(root, 'rev-parse', 'HEAD'),
      savedReports,
      savedArtifacts,
      batchState,
      home: join(root, 'home'),
    };
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

for (const phase of ['implement', 'review']) {
  test(`${phase} verification failure prevents automatic merge`, () => {
    const result = runFixture({ TEST_FAIL_PHASE: phase });
    assert.notEqual(result.status, 0, result.output);
    assert.equal(result.merges, '');
    assert.ok(result.events.some((event) => event.type === 'close'));
    if (phase === 'implement') assert.ok(!result.events.some((event) => event.name === 'reviewer'));
  });
}

test('successful Java changes are verified after implementation and read-only review, then merged', () => {
  const result = runFixture();
  assert.equal(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
  const steps = result.events.filter((event) => event.type === 'agent' || (event.type === 'exec' && event.command.includes('verify')));
  assert.deepEqual(steps.slice(0, 4).map((event) => event.name ?? 'verify'), ['implementer', 'verify', 'reviewer', 'verify']);
});

test('Maven and npm caches are mounted read-write from the host home', () => {
  const result = runFixture();
  assert.equal(result.status, 0, result.output);
  const options = result.events.find((event) => event.type === 'docker').options;
  assert.deepEqual(options?.mounts, [
    { hostPath: join(result.home, '.m2'), sandboxPath: '/home/agent/.m2', readonly: false },
    { hostPath: join(result.home, '.npm'), sandboxPath: '/home/agent/.npm', readonly: false },
  ]);
});

test('running from .sandcastle still merges into the repository root', () => {
  const result = runFixture({}, true);
  assert.equal(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
});

test('an uncommitted reviewer mutation is rejected before merge', () => {
  const result = runFixture({ TEST_NON_JAVA: '1', TEST_REVIEW_JAVA: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});

test('an issue reviewer can commit one declared fix and merge after host reverification', () => {
  const result = runFixture({ TEST_ISSUE_REVIEW_FIX: '1' });
  assert.equal(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
  const issueId = 'test-batch/issues/01-example';
  assert.ok(result.log.includes(`fix: resolve review findings for ${issueId}`));
  const report = result.savedArtifacts.find((artifact) => artifact.name === 'issue-1.json');
  assert.ok(report);
  assert.equal(report.fixes[0].commit, report.reviewedSha);
  const agents = result.events.filter((event) => event.type === 'agent');
  assert.deepEqual(agents.map((event) => event.name), ['implementer', 'reviewer', 'final-reviewer']);
  const reviewer = agents.find((event) => event.name === 'reviewer');
  assert.equal(reviewer.promptArgs.IMPLEMENTED_SHA, reviewer.startSha);
  assert.equal(reviewer.promptArgs.ISSUE_PATH, `.scratch/${issueId}.md`);
  assert.ok(!reviewer.promptArgs.REVIEWED_SHA);
  const verifyCount = result.events.filter((event) => event.type === 'exec' && event.command.includes('verify')).length;
  assert.equal(verifyCount, 3, 'two issue verifications plus the final batch verification');
});

test('an issue review without findings adds no fix commit', () => {
  const result = runFixture();
  assert.equal(result.status, 0, result.output);
  assert.ok(!result.log.includes('fix: resolve review findings for'));
  const report = result.savedArtifacts.find((artifact) => artifact.name === 'issue-1.json');
  assert.deepEqual(report?.fixes, []);
  assert.deepEqual(report?.findings, []);
});

test('an advisory-only issue review merges without a fix commit', () => {
  const result = runFixture({ TEST_ISSUE_REVIEW_ADVISORY: '1' });
  assert.equal(result.status, 0, result.output);
  assert.ok(!result.log.includes('fix: resolve review findings for'));
  const report = result.savedArtifacts.find((artifact) => artifact.name === 'issue-1.json');
  assert.deepEqual(report?.fixes, []);
  assert.equal(report?.findings[0].severity, 'advisory');
});

test('undeclared, multiple, missing, or mismatched review commits cannot merge', async (t) => {
  for (const flag of ['TEST_REVIEW_UNDECLARED_FIX', 'TEST_REVIEW_TWO_COMMITS', 'TEST_REVIEW_MISSING_FIX', 'TEST_REVIEW_WRONG_FIX_SHA']) {
    await t.test(flag, () => {
      const result = runFixture({ [flag]: '1' });
      assert.notEqual(result.status, 0, result.output);
      assert.equal(result.merges, '');
      assert.ok(!result.output.includes(' complete at '));
    });
  }
});

test('sandbox execution failures are not mislabeled as review gate failures', () => {
  const result = runFixture({ TEST_REVIEW_EXEC_ERROR: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
  assert.ok(result.output.includes('review container execution failed'));
  assert.ok(!result.output.includes('Issue review gate failed'), result.output);
});

test('issue review persists self review and final host verification for the same SHA', () => {
  const result = runFixture({ TEST_ISSUE_REVIEW_FIX: '1' });
  assert.equal(result.status, 0, result.output);
  const report = result.savedArtifacts.find((artifact) => artifact.name === 'issue-1.json');
  const host = result.savedArtifacts.find((artifact) => artifact.name === 'issue-1-host-verification.json');
  assert.ok(host);
  assert.equal(host.reviewedSha, report?.reviewedSha);
  assert.equal(host.phase, 'issue-review');
  assert.deepEqual(host.issueIds, ['test-batch/issues/01-example']);
  assert.deepEqual(host.checks.map((check: { command: string; status: string }) => ({ command: check.command, status: check.status })),
    result.events.filter((event) => event.type === 'exec' && event.command.includes('verify')).slice(1, 2)
      .map((event) => ({ command: event.command, status: 'passed' })));
  assert.ok(host.checks.every((check: { label: string; logPath: string }) => check.label === 'manager-api (Java)' && check.logPath.includes(report.reviewedSha)));
});

test('a fixed non-Java change does not invent host checks', () => {
  const result = runFixture({ TEST_NON_JAVA: '1', TEST_ISSUE_REVIEW_FIX: '1' });
  assert.equal(result.status, 0, result.output);
  const report = result.savedArtifacts.find((artifact) => artifact.name === 'issue-1.json');
  const host = result.savedArtifacts.find((artifact) => artifact.name === 'issue-1-host-verification.json');
  assert.ok(report?.verification.length);
  assert.ok(report.verification.every((check: { evidence: string }) => check.evidence));
  assert.deepEqual(host?.checks, []);
  assert.ok(!result.output.includes('mvn -B'));
});

test('non-Java changes do not require Maven verification', () => {
  const result = runFixture({ TEST_NON_JAVA: '1', TEST_FAIL_PHASE: 'implement' });
  assert.equal(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
  assert.ok(!result.events.some((event) => event.type === 'exec' && event.command.includes('verify')));
});

// 多子项目门禁：变更落在哪个 prefix 就跑哪个 check，失败则拦截 merge。
for (const [subproject, marker] of [
  ['main/manager-web/', 'npm run test:unit'],
  ['main/egg-miniprogram/', 'verify-project.js'],
  ['main/xiaozhi-server/', 'compileall'],
] as const) {
  test(`${subproject} changes trigger the matching check and pass`, () => {
    const result = runFixture({ TEST_SUBPROJECT: subproject });
    assert.equal(result.status, 0, result.output);
    assert.notEqual(result.merges, '');
    const checkExecs = result.events.filter((event) => event.type === 'exec' && event.command.includes(marker));
    assert.ok(checkExecs.length >= 3, `expected ${marker} after implement, review, and final merge`);
  });

  test(`${subproject} check failure blocks merge`, () => {
    const failKey = subproject.replace(/^main\//, '').replace(/\/$/, '');
    const result = runFixture({ TEST_SUBPROJECT: subproject, TEST_FAIL_SUBPROJECT: failKey });
    assert.notEqual(result.status, 0, result.output);
    assert.equal(result.merges, '');
  });
}

for (const flag of ['TEST_DIRTY', 'TEST_HOST_MOVED', 'TEST_CLOSE_FAIL', 'TEST_PREFLIGHT_FAIL', 'TEST_EXEC_ERROR', 'TEST_VERIFY_DIRTY', 'TEST_VERIFY_COMMIT', 'TEST_REBASE']) {
  test(`${flag} prevents merge`, () => {
    const result = runFixture({ [flag]: '1' });
    assert.notEqual(result.status, 0, result.output);
    assert.equal(result.merges, '');
  });
}

test('completion signals are configured for implementer and review phases', () => {
  const result = runFixture();
  assert.equal(result.status, 0, result.output);
  const implementer = result.events.find((event) => event.name === 'implementer');
  const reviewer = result.events.find((event) => event.name === 'reviewer');
  const finalReviewer = result.events.find((event) => event.name === 'final-reviewer');
  assert.deepEqual(implementer.completionSignal, ['<promise>IMPLEMENTED</promise>', '<promise>BLOCKED</promise>']);
  assert.deepEqual(reviewer.completionSignal, ['<promise>COMPLETE</promise>']);
  assert.deepEqual(finalReviewer.completionSignal, ['<promise>COMPLETE</promise>']);
});

test('review and fix prompts require the COMPLETE completion signal', () => {
  for (const file of ['issue-review-prompt.md', 'review-prompt.md', 'final-review-prompt.md', 'fix-review-prompt.md']) {
    assert.ok(readFileSync(join(source, file), 'utf8').includes('<promise>COMPLETE</promise>'), file);
  }
});

test('an implementation without commits stops without reviewing or merging', () => {
  const result = runFixture({ TEST_NO_COMMITS: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
  assert.ok(!result.events.some((event) => event.name === 'reviewer'));
  assert.equal(result.batchState.lastOutcome, 'no_progress');
});

test('uncommitted implementation is reported as failure rather than an empty backlog', () => {
  const result = runFixture({ TEST_NO_COMMITS: '1', TEST_DIRTY: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});

test('runner keeps the initial two-issue scope through both merges', () => {
  const result = runFixture({ TEST_TWO_ISSUES: '1' });
  assert.equal(result.status, 0, result.output);
  assert.equal(result.events.filter((event) => event.name === 'implementer').length, 2);
  assert.equal(result.events.filter((event) => event.name === 'reviewer').length, 2);
  assert.equal(result.merges.split('\n').filter(Boolean).length, 2);
  assert.ok(result.events.some((event) => event.name === 'final-reviewer'));
});

test('runner selects a ready issue before a lower-numbered blocked issue', () => {
  const result = runFixture({ TEST_TWO_ISSUES: '1', TEST_FIRST_BLOCKED: '1' });
  assert.equal(result.status, 0, result.output);
  const selected = result.events.filter((event) => event.name === 'implementer').map((event) => event.promptArgs.ISSUE_ID);
  assert.deepEqual(selected, ['test-batch/issues/02-follow-up', 'test-batch/issues/01-example']);
});

test('BLOCKED with a commit cannot merge', () => {
  const result = runFixture({ TEST_BLOCKED_WITH_COMMIT: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
  assert.equal(result.batchState.lastOutcome, 'blocked');
});

test('a clean BLOCKED issue is recorded and the runner continues with other issues', () => {
  const result = runFixture({ TEST_TWO_ISSUES: '1', TEST_BLOCKED_CLEAN: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.batchState.lastOutcome, 'blocked');
  assert.deepEqual(result.batchState.blockedIssueIds, ['test-batch/issues/01-example']);
  const implementerIds = result.events.filter((event) => event.name === 'implementer').map((event) => event.promptArgs.ISSUE_ID);
  assert.deepEqual(implementerIds, ['test-batch/issues/01-example', 'test-batch/issues/02-follow-up']);
  assert.equal(implementerIds.filter((id) => id === 'test-batch/issues/01-example').length, 1);
  assert.ok(result.log.includes('implementer'));
});

test('an implementer cannot close another target issue without its own review', () => {
  const result = runFixture({ TEST_TWO_ISSUES: '1', TEST_CLOSE_OTHER_ISSUE: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});

test('a rejected issue review cannot merge even when checks pass', () => {
  const result = runFixture({ TEST_REVIEW_REJECT: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});

test('an issue review without a structured report cannot merge', () => {
  const result = runFixture({ TEST_REVIEW_MISSING_REPORT: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});

test('final review runs against the merged head', () => {
  const result = runFixture();
  assert.equal(result.status, 0, result.output);
  const finalReview = result.events.find((event) => event.name === 'final-reviewer');
  assert.ok(finalReview, result.output);
  assert.equal(finalReview.startSha, result.head);
  assert.ok(result.events.some((event) => event.type === 'exec' && event.command.includes('verify')));
});

test('missing final requirement blocks completion after issue merge', () => {
  const result = runFixture({ TEST_FINAL_MISSING_REQUIREMENT: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
  assert.ok(result.events.some((event) => event.name === 'final-reviewer'));
});

test('omitting a numbered spec criterion from final report blocks completion', () => {
  const result = runFixture({ TEST_NUMBERED_SPEC: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.ok(result.events.some((event) => event.name === 'final-reviewer'));
});

test('omitting an R1 style spec criterion from final report blocks completion', () => {
  const result = runFixture({ TEST_OMIT_SPEC_CRITERION: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.ok(result.events.some((event) => event.name === 'final-reviewer'));
});

test('an implementer cannot weaken the frozen spec', () => {
  const result = runFixture({ TEST_MUTATE_SPEC: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});

test('a fabricated issue self-check cannot substitute for host reverification', () => {
  const result = runFixture({ TEST_REPORT_BOGUS_CHECK: '1', TEST_FAIL_PHASE: 'review' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});

test('a security finding remains parseable after report redaction', () => {
  const result = runFixture({ TEST_SECURITY_FINDING: '1' });
  assert.equal(result.status, 0, result.output);
  const finalReport = result.savedReports.find((report) => report.issueIds?.length && report.findings?.length);
  assert.ok(finalReport);
  assert.equal(finalReport.findings[0].trigger, '[redacted sensitive output]');
});

test('final fixes are verified and followed by a new review of the new head', () => {
  const result = runFixture({ TEST_FINAL_REVIEW_RETRY: '1' });
  assert.equal(result.status, 0, result.output);
  const finalReviews = result.events.filter((event) => event.name === 'final-reviewer');
  assert.equal(finalReviews.length, 2);
  assert.notEqual(finalReviews[0].startSha, finalReviews[1].startSha);
  assert.ok(result.events.some((event) => event.name === 'fixer'));
});

test('final fix-review remains read-only', () => {
  const result = runFixture({ TEST_FINAL_REVIEW_RETRY: '1', TEST_FIX_REVIEW_JAVA: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.ok(!result.output.includes(' complete at '));
  assert.ok(result.events.some((event) => event.name === 'fix-reviewer'), result.output);
});

test('a fixer that reopens an issue cannot complete the batch', () => {
  const result = runFixture({ TEST_FINAL_REVIEW_RETRY: '1', TEST_FIX_REOPENS_ISSUE: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.ok(!result.output.includes(' complete at '));
});

test('repeated final-review rejection exits after a bounded number of fixes', () => {
  const result = runFixture({ TEST_FINAL_ALWAYS_REJECT: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.ok(result.events.some((event) => event.name === 'fixer'));
  assert.ok(result.events.filter((event) => event.name === 'final-reviewer').length <= 3);
  assert.equal(result.batchState.lastOutcome, 'repair_limit', result.output);
});
