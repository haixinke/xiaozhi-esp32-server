import { execFileSync } from 'node:child_process';
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.env.TEST_REPO;
const record = (event) => appendFileSync(join(root, 'events.jsonl'), JSON.stringify(event) + '\n');
const git = (cwd, ...args) => execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
let iteration = 0;
let finalReviews = 0;
let batchBaseSha;

const issueIds = () => readdirSync(join(root, '.scratch/test-batch/issues'))
  .filter((name) => name.endsWith('.md'))
  .sort()
  .map((name) => `test-batch/issues/${name.slice(0, -3)}`);
const issuePath = (cwd, id) => join(cwd, '.scratch', `${id}.md`);
const nextOpenIssue = (cwd) => issueIds().find((id) => !readFileSync(issuePath(cwd, id), 'utf8').includes('**Status:** resolved'));
const reportOutput = (report) => `<review_report>${JSON.stringify(report)}</review_report>`;
// 判断当前 issue 是否匹配 TEST_FAIL_REVIEW_ISSUE 指定的短 ID（如 01-example）
const failSubprojectMatch = (flagValue, issueId) => {
  if (!flagValue || !issueId) return false;
  return issueId.endsWith(`/${flagValue}`) || issueId.includes(`/${flagValue}`);
};
const verification = (promptArgs) => {
  const checks = JSON.parse(promptArgs?.VERIFICATION_RESULTS ?? '[]');
  return checks.length ? checks.map((check) => ({ command: check.command, status: check.status, evidence: check.logPath }))
    : [{ command: 'manual review', status: 'passed', evidence: 'reviewed fixture change' }];
};

export const claudeCode = () => ({});
export function docker(options) {
  record({ type: 'docker', options });
  return {};
}

export async function createSandbox(options) {
  iteration += 1;
  batchBaseSha ??= git(root, 'rev-parse', 'HEAD');
  const startSha = git(root, 'rev-parse', 'HEAD');
  // 跨重启 fixture：同目录下可能残留上次 runner 的 worktree-N，挑选一个未使用序号
  let worktreeIndex = iteration;
  while (true) {
    const candidate = join(root, `worktree-${worktreeIndex++}`);
    if (!existsSync(candidate)) { var worktreePath = candidate; break; }
  }
  git(root, 'worktree', 'add', '-b', options.branch, worktreePath);
  record({ type: 'sandbox', branch: options.branch, startSha });
  let currentIssueId;
  let lastAgent;
  const change = (relativePath, text) => {
    const path = join(worktreePath, relativePath);
    mkdirSync(join(path, '..'), { recursive: true });
    writeFileSync(path, text);
  };
  return {
    branch: options.branch,
    worktreePath,
    async run({ name, promptArgs, completionSignal }) {
      lastAgent = name;
      record({ type: 'agent', name, startSha: git(worktreePath, 'rev-parse', 'HEAD'), promptArgs, completionSignal });
      if (name === 'final-reviewer') {
        finalReviews += 1;
        const rejected = process.env.TEST_FINAL_ALWAYS_REJECT === '1' ||
          (process.env.TEST_FINAL_REVIEW_RETRY === '1' && finalReviews === 1);
        const missing = process.env.TEST_FINAL_MISSING_REQUIREMENT === '1';
        const requiredIds = (promptArgs?.REQUIREMENT_IDS ?? '').split(',').filter(Boolean);
        const coveredIds = process.env.TEST_NUMBERED_SPEC === '1' || process.env.TEST_OMIT_SPEC_CRITERION === '1'
          ? issueIds() : requiredIds;
        const report = {
          baseSha: batchBaseSha,
          reviewedSha: git(worktreePath, 'rev-parse', 'HEAD'),
          issueIds: issueIds(),
          verdict: rejected ? 'changes_required' : 'pass',
          requirements: coveredIds.map((id, index) => ({
            id,
            status: missing && index === 0 ? 'missing' : 'met',
            evidence: 'main/manager-api/Example.java and verification log',
          })),
          findings: rejected ? [{ severity: 'blocking', location: 'main/manager-api/Example.java', trigger: 'batch review', impact: 'behavior incomplete' }]
            : process.env.TEST_SECURITY_FINDING === '1' ? [{ severity: 'advisory', location: 'main/manager-api/Example.java', trigger: 'token value exposed', impact: 'security review needed' }] : [],
          verification: verification(promptArgs),
        };
        return { commits: [], stdout: reportOutput(report) };
      }
      if (name === 'implementer' && process.env.TEST_BLOCKED_WITH_COMMIT === '1') {
        currentIssueId = promptArgs?.ISSUE_ID ?? nextOpenIssue(worktreePath);
        change('main/manager-api/Example.java', `implementation ${currentIssueId}\n`);
        change(`.scratch/${currentIssueId}.md`, readFileSync(issuePath(worktreePath, currentIssueId), 'utf8').replace('**Status:** open', '**Status:** resolved'));
        git(worktreePath, 'add', '.');
        git(worktreePath, 'commit', '-m', name);
        return { commits: [{ sha: git(worktreePath, 'rev-parse', 'HEAD') }], stdout: '', completionSignal: '<promise>BLOCKED</promise>' };
      }
      if (name === 'implementer' && process.env.TEST_BLOCKED_CLEAN === '1' && (promptArgs?.ISSUE_ID ?? '').includes('01-example')) {
        return { commits: [], stdout: 'blocked: external dependency unavailable', completionSignal: '<promise>BLOCKED</promise>' };
      }
      if (name === 'implementer' && process.env.TEST_NO_COMMITS === '1') {
        if (process.env.TEST_DIRTY === '1') change('uncommitted.txt', 'unfinished\n');
        return { commits: [], stdout: '' };
      }
      if (name === 'implementer') {
        currentIssueId = promptArgs?.ISSUE_ID ?? nextOpenIssue(worktreePath);
        if (!currentIssueId) return { commits: [], stdout: '' };
        if (process.env.TEST_MUTATE_SPEC === '1') change('.scratch/test-batch/spec.md', '# Weakened spec\n');
        if (process.env.TEST_NON_JAVA === '1') {
          change('README.md', 'implementation\n');
        } else if (process.env.TEST_SUBPROJECT) {
          change(`${process.env.TEST_SUBPROJECT}placeholder.txt`, 'implementation\n');
        } else {
          change('main/manager-api/Example.java', `implementation ${currentIssueId}\n`);
        }
        change(`.scratch/${currentIssueId}.md`, readFileSync(issuePath(worktreePath, currentIssueId), 'utf8').replace('**Status:** open', '**Status:** resolved'));
        if (process.env.TEST_CLOSE_OTHER_ISSUE === '1') {
          const other = issueIds().find((id) => id !== currentIssueId);
          if (other) change(`.scratch/${other}.md`, readFileSync(issuePath(worktreePath, other), 'utf8').replace('**Status:** open', '**Status:** resolved'));
        }
      } else if (name === 'fixer') {
        if (process.env.TEST_FINAL_FIX_EMPTY_COMMIT !== '1') {
          change('main/manager-api/Example.java', `implementation with final fix ${iteration}\n`);
          change('main/manager-api/ExampleTest.java', `regression coverage ${iteration}\n`);
        }
        if (process.env.TEST_FINAL_FIX_ISSUE_BODY === '1') {
          const id = issueIds()[0];
          change(`.scratch/${id}.md`, `${readFileSync(issuePath(worktreePath, id), 'utf8')}\nWeakened acceptance criteria\n`);
        }
        if (process.env.TEST_FINAL_FIX_MUTATE_SPEC === '1') change('.scratch/test-batch/spec.md', '# Weakened spec\n');
        if (process.env.TEST_FIX_REOPENS_ISSUE === '1') {
          const id = issueIds()[0];
          change(`.scratch/${id}.md`, readFileSync(issuePath(worktreePath, id), 'utf8').replace('**Status:** resolved', '**Status:** open'));
        }
      } else {
        if (process.env.TEST_REBASE === '1') git(worktreePath, 'reset', '--hard', 'HEAD~2');
        if (name === 'reviewer' && process.env.TEST_REVIEW_JAVA === '1') change('main/manager-api/Example.java', 'reviewer unexpectedly modified Java\n');
        if (name === 'reviewer' && process.env.TEST_REVIEW_MUTATE_OTHER === '1') {
          const other = issueIds().find((id) => id !== currentIssueId);
          if (other) change(`.scratch/${other}.md`, `${readFileSync(issuePath(worktreePath, other), 'utf8')}\nReviewer mutation\n`);
        }
        if (name === 'reviewer' && process.env.TEST_REVIEW_REOPEN_CURRENT === '1') {
          change(`.scratch/${currentIssueId}.md`, readFileSync(issuePath(worktreePath, currentIssueId), 'utf8').replace('**Status:** resolved', '**Status:** open'));
        }
        if (name === 'reviewer' && process.env.TEST_REVIEW_MALFORMED_CURRENT === '1') {
          change(`.scratch/${currentIssueId}.md`, '# Missing status\n');
        }
        if (name === 'reviewer' && process.env.TEST_REVIEW_MUTATE_SPEC === '1') change('.scratch/test-batch/spec.md', '# Weakened by reviewer\n');
        if (name === 'reviewer' && process.env.TEST_REVIEW_CLOSE_OTHER_ISSUE === '1') {
          const other = issueIds().find((id) => id !== currentIssueId);
          if (other) change(`.scratch/${other}.md`, readFileSync(issuePath(worktreePath, other), 'utf8').replace('**Status:** open', '**Status:** resolved'));
        }
        if (name === 'reviewer' && process.env.TEST_REVIEW_REOPEN_ISSUE === '1') {
          change(`.scratch/${currentIssueId}.md`, readFileSync(issuePath(worktreePath, currentIssueId), 'utf8').replace('**Status:** resolved', '**Status:** open'));
        }
        if (name === 'reviewer' && ['TEST_REVIEW_MUTATE_OTHER', 'TEST_REVIEW_REOPEN_CURRENT', 'TEST_REVIEW_MALFORMED_CURRENT', 'TEST_REVIEW_MUTATE_SPEC', 'TEST_REVIEW_CLOSE_OTHER_ISSUE', 'TEST_REVIEW_REOPEN_ISSUE']
          .some((flag) => process.env[flag] === '1')) {
          git(worktreePath, 'add', '.');
          git(worktreePath, 'commit', '-m', 'invalid reviewer scope mutation');
        }
        if (name === 'reviewer' && ['TEST_ISSUE_REVIEW_FIX', 'TEST_FIX_REJECT', 'TEST_REVIEW_UNDECLARED_FIX', 'TEST_REVIEW_TWO_COMMITS', 'TEST_REVIEW_WRONG_FIX_SHA']
          .some((flag) => process.env[flag] === '1')) {
          const repairPath = process.env.TEST_NON_JAVA === '1' ? 'README.md' : 'main/manager-api/Example.java';
          const regressionPath = process.env.TEST_NON_JAVA === '1' ? 'REGRESSION.md' : 'main/manager-api/ExampleTest.java';
          change(repairPath, `implementation with issue review fix ${currentIssueId}\n`);
          change(regressionPath, `regression coverage for ${currentIssueId}\n`);
          git(worktreePath, 'add', repairPath, regressionPath);
          git(worktreePath, 'commit', '-m', `fix: resolve review findings for ${currentIssueId}`);
          if (process.env.TEST_REVIEW_TWO_COMMITS === '1') git(worktreePath, 'commit', '--allow-empty', '-m', 'unexpected second review commit');
          // 模拟 reviewer 在修复提交后实际执行补丁自检，而非复用实现阶段 host 结果。
          git(worktreePath, 'diff', '--check', promptArgs.BASE_SHA, 'HEAD');
        }
      }
      if (name === 'implementer' || name === 'fixer') {
        git(worktreePath, 'add', '.');
        git(worktreePath, 'commit', '--allow-empty', '-m', name);
      }
      const withCompletion = (value) => ({
        ...value,
        completionSignal: (name === 'reviewer' || name === 'fixer')
          ? '<promise>COMPLETE</promise>' : undefined,
      });
      if (name === 'reviewer' && process.env.TEST_DIRTY === '1') change('uncommitted.txt', 'uncommitted\n');
      if (name === 'reviewer' && process.env.TEST_HOST_MOVED === '1') {
        git(root, 'commit', '--allow-empty', '-m', 'concurrent host change');
      }
      if (name === 'fixer') {
        const reviewedSha = git(worktreePath, 'rev-parse', 'HEAD');
        if (process.env.TEST_FINAL_FIX_TWO_COMMITS === '1') git(worktreePath, 'commit', '--allow-empty', '-m', 'unexpected second final fix');
        if (process.env.TEST_FINAL_FIX_DIRTY === '1') change('uncommitted.txt', 'unfinished repair\n');
        if (process.env.TEST_FINAL_FIX_MISSING_REPORT === '1') return withCompletion({ commits: [{ sha: reviewedSha }], stdout: '' });
        if (process.env.TEST_FINAL_FIX_MALFORMED_REPORT === '1') return withCompletion({ commits: [{ sha: reviewedSha }], stdout: '<review_report>{invalid}</review_report>' });
        const findings = JSON.parse(promptArgs.FINDINGS ?? '[]');
        const fixes = findings.filter((finding) => finding.severity === 'blocking').map((finding) => ({
          location: finding.location,
          problem: process.env.TEST_FINAL_FIX_MISSING_COVERAGE === '1' ? 'unrelated defect' : finding.trigger,
          resolution: 'corrected implementation and added regression coverage',
          commit: reviewedSha,
          evidence: 'post-fix patch check passed',
        }));
        git(worktreePath, 'diff', '--check', promptArgs.REVIEWED_SHA, 'HEAD');
        return withCompletion({ commits: [{ sha: reviewedSha }], stdout: reportOutput({
          baseSha: promptArgs.REVIEWED_SHA,
          reviewedSha: process.env.TEST_FINAL_FIX_WRONG_SHA === '1' ? promptArgs.REVIEWED_SHA : git(worktreePath, 'rev-parse', 'HEAD'),
          issueIds: issueIds(),
          verdict: 'pass',
          requirements: (promptArgs.REQUIREMENT_IDS ?? issueIds().join(',')).split(',').filter(Boolean).map((id) => ({ id, status: 'met', evidence: 'repair regression coverage' })),
          fixes: process.env.TEST_FINAL_FIX_MISSING_FIXES === '1' ? [] : fixes,
          findings: process.env.TEST_FINAL_FIX_BLOCKING === '1' ? findings : [],
          verification: [{ command: `git diff --check ${promptArgs.REVIEWED_SHA} HEAD`, status: 'passed', evidence: 'post-fix patch check passed' }],
        }) });
      }
      let stdout = '';
      if (name === 'reviewer') {
        if (process.env.TEST_REVIEW_MISSING_REPORT !== '1') {
          // TEST_REVIEW_REJECT_FIRST: 仅第一个独立 issue 返回未解决 blocking
          const isFirstIssue = currentIssueId && currentIssueId.includes('01-example');
          const rejected = process.env.TEST_REVIEW_REJECT === '1' ||
            (process.env.TEST_REVIEW_REJECT_FIRST === '1' && isFirstIssue);
          const fixRejected = process.env.TEST_FIX_REJECT === '1';
          const implementedSha = name === 'reviewer' ? promptArgs?.IMPLEMENTED_SHA ?? promptArgs?.REVIEWED_SHA ?? startSha : startSha;
          const reviewedSha = git(worktreePath, 'rev-parse', 'HEAD');
          const hasRepairCommit = name === 'reviewer' && reviewedSha !== implementedSha;
          const declaresFix = name === 'reviewer' && (process.env.TEST_ISSUE_REVIEW_FIX === '1' ||
            process.env.TEST_FIX_REJECT === '1' ||
            process.env.TEST_REVIEW_TWO_COMMITS === '1' || process.env.TEST_REVIEW_WRONG_FIX_SHA === '1' ||
            process.env.TEST_REVIEW_MISSING_FIX === '1' || process.env.TEST_REVIEW_MUTATE_OTHER === '1' ||
            process.env.TEST_REVIEW_REOPEN_CURRENT === '1' || process.env.TEST_REVIEW_MALFORMED_CURRENT === '1' ||
            process.env.TEST_REVIEW_MUTATE_SPEC === '1' || process.env.TEST_REVIEW_CLOSE_OTHER_ISSUE === '1' ||
            process.env.TEST_REVIEW_REOPEN_ISSUE === '1');
          // TEST_SECURITY_FIX: fixes 文本包含敏感样例，验证落盘脱敏
          const securityFix = process.env.TEST_SECURITY_FIX === '1';
          const fixes = declaresFix ? [{
            location: 'main/manager-api/Example.java:1',
            problem: securityFix ? 'password=secret123 exposed in code' : 'confirmed blocking fixture defect',
            resolution: 'minimal implementation correction with regression coverage',
            commit: process.env.TEST_REVIEW_WRONG_FIX_SHA === '1' ? implementedSha : reviewedSha,
            evidence: securityFix ? 'api_key=sk-ant-xxx verified' : 'reviewer regression test and post-fix git diff check passed',
          }] : [];
          const advisory = name === 'reviewer' && process.env.TEST_ISSUE_REVIEW_ADVISORY === '1';
          const verdict = fixRejected ? 'changes_required' : rejected ? 'changes_required' : 'pass';
          stdout = reportOutput({
            baseSha: startSha,
            reviewedSha,
            issueIds: [currentIssueId],
            verdict,
            requirements: ([currentIssueId]).map((id) => ({
              id, status: 'met', evidence: 'issue implementation and verification log',
            })),
            fixes,
            findings: (rejected || fixRejected) ? [{ severity: 'blocking', location: 'main/manager-api/Example.java', trigger: 'issue review', impact: 'behavior incomplete' }]
              : advisory ? [{ severity: 'advisory', location: 'main/manager-api/Example.java:1', trigger: 'optional readability improvement', impact: 'no behavior impact' }] : [],
              verification: process.env.TEST_REPORT_BOGUS_CHECK === '1'
              ? [{ command: 'nonexistent check', status: 'passed', evidence: 'invented' }]
              : hasRepairCommit
                ? [{
                    command: `git diff --check ${promptArgs.BASE_SHA} HEAD`,
                    status: 'passed',
                    evidence: 'reviewer ran the post-fix patch check',
                  }, ...(process.env.TEST_ISSUE_REVIEW_SKIPPED_CHECK === '1' ? [{
                    command: 'full Maven verification',
                    status: 'skipped',
                    evidence: 'runner will verify the final SHA',
                  }] : [])]
                : verification(promptArgs),
          });
        }
      }
      return withCompletion({ commits: name === 'implementer' || name === 'fixer' ? [{ sha: git(worktreePath, 'rev-parse', 'HEAD') }] : [], stdout });
    },
    async exec(command) {
      record({ type: 'exec', command });
      if (command.includes('verify') && process.env.TEST_FAIL_PHASE === 'review' && lastAgent === 'reviewer') {
        return { exitCode: 1, stdout: '', stderr: 'review verification failed' };
      }
      if (command.includes('verify') && lastAgent === 'fixer' && process.env.TEST_FINAL_FIX_HOST_FAIL === '1') return { exitCode: 1, stdout: '', stderr: 'final fix verification failed' };
      // TEST_FAIL_REVIEW_ISSUE: 仅指定 issue 的 review 后 host check 返回非零；实现阶段及其他 issue 不受影响
      const failReviewIssue = process.env.TEST_FAIL_REVIEW_ISSUE;
      if (command.includes('verify') && failSubprojectMatch(failReviewIssue, currentIssueId) && lastAgent === 'reviewer') {
        return { exitCode: 1, stdout: '', stderr: 'review verification failed' };
      }
      if (command.includes('verify') && process.env.TEST_EXEC_ERROR === '1') throw new Error('container execution failed');
      if (command.includes('verify') && process.env.TEST_REVIEW_EXEC_ERROR === '1' && lastAgent === 'reviewer') throw new Error('review container execution failed');
      if (process.env.TEST_PREFLIGHT_FAIL === '1' && command.includes('java -version')) {
        return { exitCode: 127, stdout: '', stderr: 'java unavailable' };
      }
      // 多子项目门禁测试：指定子项目的关键命令失败
      const failSubproject = process.env.TEST_FAIL_SUBPROJECT;
      if (failSubproject) {
        if (failSubproject === 'manager-web' && command.includes('npm run test:unit')) {
          return { exitCode: 1, stdout: '', stderr: 'web tests failed' };
        }
        if (failSubproject === 'egg-miniprogram' && command.includes('verify-project.js')) {
          return { exitCode: 1, stdout: '', stderr: 'miniprogram check failed' };
        }
        if (failSubproject === 'xiaozhi-server' && command.includes('compileall')) {
          return { exitCode: 1, stdout: '', stderr: 'syntax error' };
        }
      }
      try {
        const stdout = execFileSync('sh', ['-c', command], {
          cwd: worktreePath,
          env: { ...process.env, PATH: `${join(root, 'bin')}:${process.env.PATH}` },
          encoding: 'utf8',
          stdio: ['ignore', 'pipe', 'pipe'],
        });
        if (command.includes('verify') && process.env.TEST_VERIFY_DIRTY === '1') change('uncommitted.txt', 'generated\n');
        if (command.includes('verify') && process.env.TEST_VERIFY_COMMIT === '1') git(worktreePath, 'commit', '--allow-empty', '-m', 'unexpected build commit');
        return { exitCode: 0, stdout, stderr: '' };
      } catch (error) {
        return { exitCode: error.status ?? 1, stdout: error.stdout ?? '', stderr: error.stderr ?? '' };
      }
    },
    async close() {
      record({ type: 'close' });
      if (process.env.TEST_CLOSE_FAIL === '1') throw new Error('cleanup failed');
    },
  };
}
