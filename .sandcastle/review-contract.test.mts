import assert from 'node:assert/strict';
import { test } from 'node:test';
import { assertReviewPass, parseReviewReport } from './review-contract.mts';

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
