# TASK

Perform a read-only cumulative review of the batch diff `{{BASE_SHA}}..{{REVIEWED_SHA}}` on the merged host HEAD. Do not edit files, commit, rebase, or change HEAD.

## Batch scope

Issue IDs: `{{ISSUE_IDS}}`
Issue files: `{{ISSUE_PATHS}}`
Feature specs: `{{SPEC_PATHS}}`
Required report IDs: `{{REQUIREMENT_IDS}}`
Host verification results (commands, status and host log paths): `{{VERIFICATION_RESULTS}}`

Read every feature spec and issue file. Build a requirement-to-implementation map from the spec, including requirements that no issue mentions. Inspect changed code, tests, migrations, permissions, API callers, and cross-issue interactions. Apply @.sandcastle/CODING_STANDARDS.md and affected subproject rules.

Check whether all promised functionality is present, tested and integrated. Pay particular attention to state transitions, access control, data contracts, failure paths, compatibility and duplicated or contradictory implementations. Include the exact executed host commands above in `verification`; host log paths are references and may be inaccessible from this sandbox. If required integration or runtime tests were skipped, mark the affected requirement unverified and explain what is needed.

For every fixable missing requirement, also report a `blocking` finding with a concrete location, trigger, and impact so the fixer receives it. If the implementation is entirely absent, use the relevant spec acceptance item's `file:line` as the location; do not invent a code location. `changes_required` requires at least one `blocking` finding. Use `blocked` when required external evidence is unavailable or requirements are unclear, and explain what is needed to proceed.

A final-review `pass` requires at least one actually executed check with status `passed` and nonempty evidence, and no `failed` or `skipped` verification entries. If no host checks apply, execute and report an appropriate scoped check; if an applicable check cannot be performed, report it honestly and use `blocked` when its absence prevents verification. Record actual failures and skips even though they prevent a pass; do not omit them to satisfy the gate.

Emit `<promise>COMPLETE</promise>` once the report is finalized.

Emit exactly one `<review_report>` tag with the same JSON shape as `.sandcastle/review-prompt.md`:

```json
{
  "baseSha": "{{BASE_SHA}}",
  "reviewedSha": "{{REVIEWED_SHA}}",
  "issueIds": ["every exact ID in scope"],
  "verdict": "pass | changes_required | blocked",
  "requirements": [{ "id": "exact issue ID or spec requirement ID", "status": "met | missing | unverified", "evidence": "code and test locations" }],
  "findings": [{ "severity": "blocking | advisory", "location": "file:line", "trigger": "concrete condition", "impact": "observable consequence" }],
  "verification": [{ "command": "executed command", "status": "passed | failed | skipped", "evidence": "result or log path" }]
}
```

Include one `requirements` row for each exact ID in `Required report IDs`, plus rows for any additional spec acceptance items. Use `changes_required` for concrete fixable defects and `blocked` for missing external evidence or unclear requirements. Do not output `pass` when any necessary check or requirement is unverified.
