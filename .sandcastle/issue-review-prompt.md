# TASK

Review and, only for confirmed in-scope blocking defects, repair the committed implementation of issue `{{ISSUE_ID}}`. The implementation HEAD before your work is `{{IMPLEMENTED_SHA}}`; the batch baseline is `{{BASE_SHA}}`. This is a single-issue review. Do not create a second reviewer or fixer.

## Scope

Issue file: `{{ISSUE_PATH}}`
Feature specs: `{{SPEC_PATHS}}`
Implementation-stage host verification results: `{{VERIFICATION_RESULTS}}`

Read the current issue and every acceptance criterion, the relevant feature specs, the root and affected subprojects' `CLAUDE.md`, and @.sandcastle/CODING_STANDARDS.md. Inspect the complete cumulative diff `{{BASE_SHA}}..{{IMPLEMENTED_SHA}}`, not only individual commits, and trace related callers, tests, migrations, and interfaces. Record the baseline, implementation SHA, and supplied verification evidence. Do not clean up unrelated historical code or change another issue or a frozen spec.

## REVIEW PROCESS

### 1. Establish scope and requirement mapping

Map every issue acceptance criterion to its implementation location and test evidence. Use the issue, specs, and existing interface contracts to determine intent; do not infer requirements from the implementation. Identify missing, divergent, and unverifiable requirements. If requirements, acceptance criteria, or business rules are unclear, stop repair and report the missing information.

### 2. Review by risk

Review in this order:

1. Correctness, boundary conditions, and failure paths.
2. Security, authentication/authorization, data consistency, concurrency, and idempotency.
3. API/data contracts, cross-client compatibility, migrations, and caller impact.
4. Whether tests exercise actual behavior and critical risks.
5. Clarity, complexity, and coding standards.

Report only concrete findings with a location, trigger condition, and observable impact. Do not manufacture a maintainability change merely to find an improvement. Historical issues outside this issue's diff are out of scope.

### 3. Classify findings

Repair only a confirmed blocking problem introduced or aggravated by this issue's diff when it has a concrete location, trigger, and observable impact, can be fixed minimally within this issue, and can be checked with executable evidence. Do not repair advisory suggestions, style preferences, generalized refactoring, or unrelated historical issues; report advisories without changing code.

Do not guess a repair when requirements are unclear, the fix requires changing another issue or a frozen spec, a breaking interface change lacks a compatibility migration, required external services/permissions/data/runtime conditions are unavailable, or a minimal in-scope repair is unsafe. Leave such blocking findings unresolved with verdict `changes_required` or `blocked`; do not commit a partial repair.

### 4. Reproduce and minimally repair

For a repairable blocking defect, first add a regression test that exposes it before the fix, then make the smallest correct implementation change. Do not delete, weaken, or skip tests or assertions, change specs, change another issue, or reopen the current issue. Do not retain or commit an incomplete or unverifiable repair.

### 5. Self-check the final cumulative diff

After any repair, re-review the complete cumulative diff `{{BASE_SHA}}..<final HEAD>`, not just the repair fragment. Recheck all original acceptance criteria, repair impact, related callers, and relevant tests. You may run sandbox checks to support your self-review. Never mark an unrun check as passed. The supplied `VERIFICATION_RESULTS` belong only to `{{IMPLEMENTED_SHA}}`; they are not post-repair results. After a repair, `verification` must contain only checks you actually ran against the final HEAD; record unrun applicable checks as `skipped` with a reason. The runner will independently execute host verification after your report, so do not invent its commands or results.

A reviewer `pass` means all acceptance criteria have evidence, no blocking finding remains, no requirement is unverified, and your actual self-check evidence passes. It does not mean the later host verification has already passed. Advisory findings may remain in a pass report; resolved blocking findings belong in `fixes`, not as unresolved blocking findings.

### 6. Commit and report

If you made an actual repair, commit only its code and regression tests as exactly one independent commit with subject:

`fix: resolve review findings for {{ISSUE_ID}}`

Do not amend, rebase, reset, merge another branch, or rewrite the implementation commit. Do not commit logs, reports, private configuration, specs, or other issue files. Make no empty commit when there is no repair. Leave the worktree clean. At most one repair commit is permitted after `{{IMPLEMENTED_SHA}}`; without a repair, final HEAD must remain `{{IMPLEMENTED_SHA}}`.

Emit `<promise>COMPLETE</promise>` once and exactly one `<review_report>` tag containing a JSON object with this shape:

```json
{
  "baseSha": "{{BASE_SHA}}",
  "reviewedSha": "actual final HEAD after any repair; otherwise {{IMPLEMENTED_SHA}}",
  "issueIds": ["{{ISSUE_ID}}"],
  "verdict": "pass | changes_required | blocked",
  "fixes": [{ "location": "file:line", "problem": "resolved blocking problem", "resolution": "minimal repair", "commit": "40-character repair commit SHA", "evidence": "regression test location and actual result" }],
  "findings": [{ "severity": "blocking | advisory", "location": "file:line", "trigger": "concrete condition", "impact": "observable consequence" }],
  "requirements": [{ "id": "{{ISSUE_ID}}", "status": "met | missing | unverified", "evidence": "implementation and test locations" }],
  "verification": [{ "command": "actually executed command", "status": "passed | failed | skipped", "evidence": "actual result, log reference, or skip reason" }]
}
```

Use `fixes: []` when no repair was committed. Every declared fix must identify the same single repair commit, which must be the final HEAD. `findings` retains unresolved blocking findings and all advisories; a fixed problem with no residual defect is not repeated as unresolved. Include a requirement row whose ID is exactly `{{ISSUE_ID}}`. A pass requires evidence for every requirement, no unresolved blocking findings, and nonempty passing self-check evidence. The runner independently checks report identity, real commit history, scope, worktree cleanliness, self-review, and final host verification before merging.
