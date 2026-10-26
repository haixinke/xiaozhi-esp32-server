// 已修复 blocking 的记录：commit 必须是产生该修复的 40 位 commit SHA
export type ReviewFix = {
  location: string;
  problem: string;
  resolution: string;
  commit: string;
  evidence: string;
};

export type ReviewReport = {
  baseSha: string;
  reviewedSha: string;
  issueIds: string[];
  verdict: 'pass' | 'changes_required' | 'blocked';
  requirements: Array<{ id: string; status: 'met' | 'missing' | 'unverified'; evidence: string }>;
  findings: Array<{ severity: 'blocking' | 'advisory'; location: string; trigger: string; impact: string }>;
  verification: Array<{ command: string; status: 'passed' | 'failed' | 'skipped'; evidence: string }>;
  fixes: ReviewFix[];
};

type ReviewExpectation = Pick<ReviewReport, 'baseSha' | 'reviewedSha' | 'issueIds'> & {
  requirementIds?: string[];
  checkedCommands?: string[];
  allowSkippedVerification?: boolean;
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

// fixes 为可选字段：只读 final/fix-review 报告可省略，解析时归一化为空数组
function validFixes(value: unknown): boolean {
  return value === undefined || (Array.isArray(value) && value.every((item: unknown) => record(item) &&
    nonempty(item.location) && nonempty(item.problem) && nonempty(item.resolution) &&
    validSha(item.commit) && nonempty(item.evidence)));
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
        typeof item.evidence === 'string') ||
      !validFixes(value.fixes)) {
    throw new Error('Invalid review report schema');
  }
  // 返回新对象而不修改输入：省略 fixes 时归一化为空数组，保持只读报告向后兼容
  return { ...(value as ReviewReport), fixes: (value as ReviewReport).fixes ?? [] };
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
  // 单 issue 的 skipped 表示 reviewer 如实声明未运行；仍须有通过的自检，且最终由 host 复验。
  if (!report.verification.some((item) => item.status === 'passed' && nonempty(item.evidence)) ||
      report.verification.some((item) => !nonempty(item.evidence) || item.status === 'failed' ||
        (item.status === 'skipped' && !expected.allowSkippedVerification))) {
    throw new Error('Review verification failed or was skipped');
  }
  for (const command of expected.checkedCommands ?? []) {
    if (!report.verification.some((item) => item.command === command && item.status === 'passed')) {
      throw new Error(`Review verification does not match runner check: ${command}`);
    }
  }
  if (report.verdict !== 'pass') throw new Error(`Review verdict is ${report.verdict}`);
}

// 单 issue 修复提交形态断言：只校验报告与已收集提交列表的一致性，不执行任何 Git 操作
export function assertIssueReviewFixes(
  report: ReviewReport,
  expected: { implementedSha: string; reviewedSha: string; repairCommits: string[] },
): void {
  if (report.reviewedSha !== expected.reviewedSha) {
    throw new Error('Review reviewedSha does not match final HEAD');
  }
  if (report.fixes.length) {
    // 有修复时：implementedSha..reviewedSha 之间必须恰好一个修复 commit，且就是最终 HEAD
    if (expected.reviewedSha === expected.implementedSha) {
      throw new Error('Review declares fixes without a new repair commit');
    }
    if (expected.repairCommits.length !== 1) {
      throw new Error(`Expected exactly one repair commit, found ${expected.repairCommits.length}`);
    }
    const [repair] = expected.repairCommits;
    if (repair !== expected.reviewedSha) {
      throw new Error('Repair commit does not match final HEAD');
    }
    if (report.fixes.some((item) => item.commit !== repair)) {
      throw new Error('A declared fix commit does not match the repair commit');
    }
  } else if (expected.reviewedSha !== expected.implementedSha || expected.repairCommits.length) {
    // 无修复时：最终 HEAD 必须仍是实现 SHA，且不得出现任何修复 commit
    throw new Error('Review has code changes without declared fixes');
  }
}
