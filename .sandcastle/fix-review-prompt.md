# TASK

Fix the blocking final-review findings on the current branch. The previous merged HEAD is `{{REVIEWED_SHA}}`; issue scope is `{{ISSUE_IDS}}`.

Findings JSON:

{{FINDINGS}}

Read the relevant feature specs, issue files, changed code and tests. Reproduce each confirmed defect with a failing regression test, then make the smallest fix. Preserve all acceptance criteria and do not weaken, delete or skip tests to obtain a pass. Run the affected subproject checks, commit the fix, and leave a clean worktree. If a finding cannot be fixed safely, explain the blocker and make no commit.
