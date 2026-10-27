# TASK

Fix and self-review the blocking final-review findings in this single agent session. Do not start another reviewer or fixer. The previous merged HEAD is `{{REVIEWED_SHA}}`; issue scope is `{{ISSUE_IDS}}`.

Issue files: `{{ISSUE_PATHS}}`
Frozen feature specs: `{{SPEC_PATHS}}`
Required report IDs: `{{REQUIREMENT_IDS}}`

Findings JSON:

{{FINDINGS}}

Read the feature specs, issue files, affected code and tests, @.sandcastle/CODING_STANDARDS.md and applicable subproject rules. Reproduce each confirmed blocking defect with a failing regression test, then make the smallest fix. Preserve all acceptance criteria; do not weaken, delete or skip tests to obtain a pass. Do not change specs or issue files, reopen an issue, or perform unrelated cleanup. If a finding cannot be fixed safely, report the blocker and make no partial repair commit.

Commit the complete repair and its regression tests as exactly one nonempty commit after `{{REVIEWED_SHA}}`. Do not amend, reset, rebase, merge, or rewrite existing history. Leave a clean worktree. Self-review the committed repair, its callers and cross-issue impact, and confirm all batch issue/spec requirements still hold. Run the affected checks against this final HEAD. Report only checks actually executed; an unrun check must be `skipped` with a reason. A pass needs passing self-check evidence, all requirements met with evidence, and no unresolved blocking findings. The runner independently validates your report and runs its checks after you finish; do not claim those future runner checks have passed.

Emit `<promise>COMPLETE</promise>` once and exactly one `<review_report>` tag containing:

```json
{
  "baseSha": "{{REVIEWED_SHA}}",
  "reviewedSha": "actual final 40-character HEAD SHA",
  "issueIds": ["every exact ID in scope"],
  "verdict": "pass | changes_required | blocked",
  "fixes": [{ "location": "exact original finding location", "problem": "exact original finding trigger", "resolution": "how it was repaired", "commit": "final repair SHA", "evidence": "regression test location and actual result" }],
  "findings": [{ "severity": "blocking | advisory", "location": "file:line", "trigger": "concrete condition", "impact": "observable consequence" }],
  "requirements": [{ "id": "exact required report ID", "status": "met | missing | unverified", "evidence": "implementation and test locations" }],
  "verification": [{ "command": "actually executed command", "status": "passed | failed | skipped", "evidence": "actual result or skip reason" }]
}
```

Include a `fixes` row for every repaired blocking finding, copying its original `location` and `trigger` exactly into `location` and `problem`. All fixes must reference the same final repair commit. Retain unresolved blocking findings and advisories in `findings`; use `fixes: []` when no repair was committed. Include every Required report ID in `requirements`. Use `blocked` for missing external evidence or unclear requirements, and `changes_required` for remaining defects. Never claim pass with incomplete or unverified requirements.
