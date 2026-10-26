import assert from 'node:assert/strict';
import { test } from 'node:test';
import { assertIssueReviewFixes, assertReviewPass, parseReviewReport } from './review-contract.mts';

const report = () => ({
  baseSha: 'a'.repeat(40),
  reviewedSha: 'b'.repeat(40),
  issueIds: ['feature/issues/01-first'],
  verdict: 'pass',
  requirements: [{ id: 'feature/issues/01-first', status: 'met', evidence: 'src/a.ts:12 and tests/a.test.ts:20' }],
  findings: [],
  verification: [{ command: 'npm test', status: 'passed', evidence: 'runner log 1' }],
});

const tagged = (value: unknown) => `<review_report>${JSON.stringify(value)}</review_report>`;

test('a valid report certifies only its exact commit and issue scope', () => {
  const value = parseReviewReport(tagged(report()));
  assert.doesNotThrow(() => assertReviewPass(value, {
    baseSha: 'a'.repeat(40), reviewedSha: 'b'.repeat(40), issueIds: ['feature/issues/01-first'],
  }));
  assert.throws(() => assertReviewPass(value, {
    baseSha: 'a'.repeat(40), reviewedSha: 'c'.repeat(40), issueIds: ['feature/issues/01-first'],
  }), /reviewedSha/);
});

test('missing, duplicated and malformed tagged reports fail closed', () => {
  assert.throws(() => parseReviewReport('done'), /review_report/);
  assert.throws(() => parseReviewReport(tagged(report()) + tagged(report())), /review_report/);
  assert.throws(() => parseReviewReport('<review_report>{bad}</review_report>'), /JSON/);
});

test('missing requirement evidence or incomplete requirements blocks approval', () => {
  const missing = { ...report(), requirements: [{ id: 'first', status: 'missing', evidence: '' }] };
  assert.throws(() => assertReviewPass(parseReviewReport(tagged(missing)), {
    baseSha: report().baseSha, reviewedSha: report().reviewedSha, issueIds: report().issueIds,
  }), /requirement/);
});

test('blocking findings and skipped verification block approval', () => {
  const blocked = {
    ...report(),
    findings: [{ severity: 'blocking', location: 'src/a.ts:12', trigger: 'bad input', impact: 'data loss' }],
  };
  const skipped = {
    ...report(),
    verification: [{ command: 'npm test', status: 'skipped', evidence: 'not installed' }],
  };
  const expected = { baseSha: report().baseSha, reviewedSha: report().reviewedSha, issueIds: report().issueIds };
  assert.throws(() => assertReviewPass(parseReviewReport(tagged(blocked)), expected), /blocking/);
  assert.throws(() => assertReviewPass(parseReviewReport(tagged(skipped)), expected), /verification/);
});

test('a final report omitting a frozen spec criterion cannot pass', () => {
  const value = parseReviewReport(tagged(report()));
  assert.throws(() => assertReviewPass(value, {
    baseSha: report().baseSha,
    reviewedSha: report().reviewedSha,
    issueIds: report().issueIds,
    requirementIds: ['feature/issues/01-first', '.scratch/feature/spec.md#1'],
  }), /requirement/);
});

test('a pass report with no verification evidence cannot pass', () => {
  const empty = { ...report(), verification: [] };
  assert.throws(() => assertReviewPass(parseReviewReport(tagged(empty)), {
    baseSha: report().baseSha, reviewedSha: report().reviewedSha, issueIds: report().issueIds,
  }), /verification/);
});

const implemented = 'b'.repeat(40);
const repair = 'c'.repeat(40);
// 无修复报告绑定实现 SHA；有修复报告绑定修复 commit
const fix = () => ({
  location: 'src/a.ts:30',
  problem: 'off-by-one drops the last row',
  resolution: 'bounded the loop by rows.length',
  commit: repair,
  evidence: 'tests/a.test.ts:40 fails before, passes after',
});
const fixed = () => ({ ...report(), reviewedSha: repair, fixes: [fix()] });
const commits = () => ({ implementedSha: implemented, reviewedSha: repair, repairCommits: [repair] });

test('a read-only report without fixes remains valid and normalizes to an empty list', () => {
  const value = parseReviewReport(tagged(report()));
  assert.deepEqual(value.fixes, []);
  assert.doesNotThrow(() => assertIssueReviewFixes(value, {
    implementedSha: implemented, reviewedSha: implemented, repairCommits: [],
  }));
});

test('malformed fix records fail closed', () => {
  assert.throws(() => parseReviewReport(tagged({ ...report(), fixes: 'none' })), /Invalid review report schema/);
  for (const key of ['location', 'problem', 'resolution', 'commit', 'evidence']) {
    const missingField = { ...report(), fixes: [{ ...fix(), [key]: '' }] };
    assert.throws(() => parseReviewReport(tagged(missingField)), /Invalid review report schema/);
  }
  const badSha = { ...report(), fixes: [{ ...fix(), commit: repair.toUpperCase() }] };
  assert.throws(() => parseReviewReport(tagged(badSha)), /Invalid review report schema/);
});

test('a declared fix does not hide a blocking finding', () => {
  const stillBlocked = { ...fixed(), findings: [{ severity: 'blocking', location: 'src/a.ts:12', trigger: 'bad input', impact: 'data loss' }] };
  const expected = { baseSha: report().baseSha, reviewedSha: repair, issueIds: report().issueIds };
  assert.throws(() => assertReviewPass(parseReviewReport(tagged(stillBlocked)), expected), /blocking/);
});

test('an issue review without fixes passes only when no repair commit exists', () => {
  const read = parseReviewReport(tagged(report()));
  assert.doesNotThrow(() => assertIssueReviewFixes(read, {
    implementedSha: implemented, reviewedSha: implemented, repairCommits: [],
  }));
  // 报告如实绑定最终 HEAD，但 SHA 已前进而 fixes 仍为空：代码变更未被声明
  const advanced = parseReviewReport(tagged({ ...report(), reviewedSha: repair }));
  assert.throws(() => assertIssueReviewFixes(advanced, {
    implementedSha: implemented, reviewedSha: repair, repairCommits: [repair],
  }), /fixes/);
  // SHA 未前进却收集到修复 commit：声明与提交历史不一致
  assert.throws(() => assertIssueReviewFixes(read, {
    implementedSha: implemented, reviewedSha: implemented, repairCommits: [repair],
  }), /fixes/);
});

test('a single fix requires exactly one repair commit equal to the final head', () => {
  const one = parseReviewReport(tagged(fixed()));
  assert.doesNotThrow(() => assertIssueReviewFixes(one, commits()));
  // 声明了 fix 但最终 HEAD 仍是实现 SHA：没有产生真实修复 commit
  const stale = parseReviewReport(tagged({ ...report(), fixes: [fix()] }));
  assert.throws(() => assertIssueReviewFixes(stale, {
    implementedSha: implemented, reviewedSha: implemented, repairCommits: [],
  }), /fixes/);
  // 两个修复 commit：不符合恰好一个的形态
  assert.throws(() => assertIssueReviewFixes(one, {
    implementedSha: implemented, reviewedSha: repair, repairCommits: [repair, 'd'.repeat(40)],
  }), /one/);
  // 修复 commit 不是最终 HEAD（夹带了其他 commit）
  assert.throws(() => assertIssueReviewFixes(one, {
    implementedSha: implemented, reviewedSha: repair, repairCommits: ['d'.repeat(40)],
  }), /commit/);
});

test('multiple fixes may share the single repair commit', () => {
  const many = parseReviewReport(tagged({ ...report(), reviewedSha: repair, fixes: [fix(), fix()] }));
  assert.doesNotThrow(() => assertIssueReviewFixes(many, commits()));
});

test('a fix record not pointing at the single repair commit is rejected', () => {
  // 唯一修复 commit 等于最终 HEAD，但 fix 记录指向其他 commit
  const mismatched = parseReviewReport(tagged({ ...report(), reviewedSha: repair, fixes: [{ ...fix(), commit: 'd'.repeat(40) }] }));
  assert.throws(() => assertIssueReviewFixes(mismatched, commits()), /commit/);
});
