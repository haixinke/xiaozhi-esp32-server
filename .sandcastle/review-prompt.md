# TASK

Review the committed diff `{{BASE_SHA}}..{{REVIEWED_SHA}}` for issues `{{ISSUE_IDS}}`. This is a read-only review. Do not edit, commit, rebase, or change HEAD.

## Scope

Issue files: `{{ISSUE_PATHS}}`
Feature specs: `{{SPEC_PATHS}}`
Required report IDs: `{{REQUIREMENT_IDS}}`
Host verification results (commands, status and host log paths): `{{VERIFICATION_RESULTS}}`

Read each issue, its acceptance criteria, and the relevant feature spec. Inspect the diff and related callers, tests, migrations, and interfaces. Apply @.sandcastle/CODING_STANDARDS.md and affected subprojects' `CLAUDE.md` rules.

## Review priorities

1. Correctness against the issue/spec, including failure and edge paths.
2. Security, authorization, data consistency, cross-module compatibility and test coverage.
3. Clarity, maintainability and coding standards in the changed code.

Report only concrete findings with a location, trigger, and impact. Mark a defect or missing required validation as `blocking`; use `advisory` for non-blocking suggestions. Do not mark unrun tests as passed. The host verification results above are the runner's executed checks; include their exact commands in `verification`. Host log paths are references only and may be inaccessible inside this sandbox. If no host checks apply, state what behavior was independently checked or mark it unverified. Never claim the change passes merely because the diff looks plausible.

Emit exactly one `<review_report>` tag containing a JSON object with this shape:

```json
{
  "baseSha": "{{BASE_SHA}}",
  "reviewedSha": "{{REVIEWED_SHA}}",
  "issueIds": ["one ID for every issue in scope"],
  "verdict": "pass | changes_required | blocked",
  "requirements": [{ "id": "exact issue ID (plus extra spec IDs if useful)", "status": "met | missing | unverified", "evidence": "code and test locations" }],
  "findings": [{ "severity": "blocking | advisory", "location": "file:line", "trigger": "concrete condition", "impact": "observable consequence" }],
  "verification": [{ "command": "executed command", "status": "passed | failed | skipped", "evidence": "result or log path" }]
}
```

Include at least one `requirements` row whose `id` is exactly each issue ID. A pass requires evidence for every requirement and no blocking findings or unverified checks. The runner checks SHA, scope, report structure and result before merging.
