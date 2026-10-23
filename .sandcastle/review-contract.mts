export type ReviewReport = {
  baseSha: string;
  reviewedSha: string;
  issueIds: string[];
  verdict: 'pass' | 'changes_required' | 'blocked';
  requirements: Array<{ id: string; status: 'met' | 'missing' | 'unverified'; evidence: string }>;
  findings: Array<{ severity: 'blocking' | 'advisory'; location: string; trigger: string; impact: string }>;
  verification: Array<{ command: string; status: 'passed' | 'failed' | 'skipped'; evidence: string }>;
};

type ReviewExpectation = Pick<ReviewReport, 'baseSha' | 'reviewedSha' | 'issueIds'> & {
  requirementIds?: string[];
  checkedCommands?: string[];
};

function record(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function nonempty(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function validSha(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{40}$/.test(value);
}

export function parseReviewReport(stdout: string): ReviewReport {
  const matches = [...stdout.matchAll(/<review_report>([\s\S]*?)<\/review_report>/g)];
  if (matches.length !== 1) throw new Error(`Expected one <review_report>, found ${matches.length}`);
  let value: unknown;
  try {
    value = JSON.parse(matches[0][1]);
  } catch {
    throw new Error('Invalid review report JSON');
  }
  if (!record(value) || !validSha(value.baseSha) || !validSha(value.reviewedSha) ||
      !Array.isArray(value.issueIds) || !value.issueIds.length || !value.issueIds.every(nonempty) ||
      !['pass', 'changes_required', 'blocked'].includes(String(value.verdict)) ||
      !Array.isArray(value.requirements) || !value.requirements.length ||
      !value.requirements.every((item: unknown) => record(item) && nonempty(item.id) &&
        ['met', 'missing', 'unverified'].includes(String(item.status)) && typeof item.evidence === 'string') ||
      !Array.isArray(value.findings) || !value.findings.every((item: unknown) => record(item) &&
        ['blocking', 'advisory'].includes(String(item.severity)) &&
        nonempty(item.location) && nonempty(item.trigger) && nonempty(item.impact)) ||
      !Array.isArray(value.verification) || !value.verification.every((item: unknown) => record(item) &&
        nonempty(item.command) && ['passed', 'failed', 'skipped'].includes(String(item.status)) &&
        typeof item.evidence === 'string')) {
    throw new Error('Invalid review report schema');
  }
  return value as ReviewReport;
}

export function assertReviewPass(report: ReviewReport, expected: ReviewExpectation): void {
  if (report.baseSha !== expected.baseSha) throw new Error('Review baseSha does not match baseline');
  if (report.reviewedSha !== expected.reviewedSha) throw new Error('Review reviewedSha does not match committed HEAD');
  const actualIds = [...new Set(report.issueIds)].sort();
  const expectedIds = [...new Set(expected.issueIds)].sort();
  if (actualIds.length !== report.issueIds.length || JSON.stringify(actualIds) !== JSON.stringify(expectedIds)) {
    throw new Error('Review issue scope does not match target issues');
  }
  for (const id of [...new Set(expected.requirementIds ?? expectedIds)]) {
    if (!report.requirements.some((item) => item.id === id && item.status === 'met' && nonempty(item.evidence))) {
      throw new Error(`Unmet requirement or missing evidence for ${id}`);
    }
  }
  if (report.requirements.some((item) => item.status !== 'met' || !nonempty(item.evidence))) {
    throw new Error('Review contains an unmet requirement or missing evidence');
  }
  if (report.findings.some((item) => item.severity === 'blocking')) throw new Error('Review contains blocking findings');
  if (!report.verification.length || report.verification.some((item) => item.status !== 'passed' || !nonempty(item.evidence))) {
    throw new Error('Review verification failed or was skipped');
  }
  for (const command of expected.checkedCommands ?? []) {
    if (!report.verification.some((item) => item.command === command && item.status === 'passed')) {
      throw new Error(`Review verification does not match runner check: ${command}`);
    }
  }
  if (report.verdict !== 'pass') throw new Error(`Review verdict is ${report.verdict}`);
}
