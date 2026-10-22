# TASK

Review the code changes on branch `{{BRANCH}}` and improve code clarity, consistency, and maintainability while preserving exact functionality.

# CONTEXT

## Branch diff

!`git diff {{TARGET_BRANCH}}...{{BRANCH}}`

## Commits on this branch

!`git log {{TARGET_BRANCH}}..{{BRANCH}} --oneline`

# REVIEW PROCESS

1. **Understand the change**: Read the diff and commits above to understand the intent.

2. **Analyze for improvements**: Look for opportunities to:
   - Reduce unnecessary complexity and nesting
   - Eliminate redundant code and abstractions
   - Improve readability through clear variable and function names
   - Consolidate related logic
   - Remove unnecessary comments that describe obvious code
   - Avoid nested ternary operators - prefer switch statements or if/else chains
   - Choose clarity over brevity - explicit code is often better than overly compact code

3. **Check correctness**:
   - Does the implementation match the intent? Are edge cases handled?
   - Are new/changed behaviours covered by tests?
   - Are there unsafe casts, `any` types, or unchecked assumptions?
   - Does the change introduce injection vulnerabilities, credential leaks, or other security issues?

4. **Maintain balance**: Avoid over-simplification that could:
   - Reduce code clarity or maintainability
   - Create overly clever solutions that are hard to understand
   - Combine too many concerns into single functions or components
   - Remove helpful abstractions that improve code organization
   - Make the code harder to debug or extend

5. **Apply project standards**: Follow the coding standards defined in @.sandcastle/CODING_STANDARDS.md

6. **Preserve functionality**: Never change what the code does - only how it does it. All original features, outputs, and behaviors must remain intact.

# EXECUTION

If you find improvements to make:

1. Make the changes directly on this branch
2. Run the project's checks to ensure nothing is broken. Verification is dispatched per subproject by changed paths; run every row whose prefix matches a file changed on this branch:

   | Changed path | Check command (from repo root) |
   |---|---|
   | `main/manager-api/` | `mvn -B -ntp -f main/manager-api/pom.xml -Dmaven.repo.local=/home/agent/.m2/repository -DskipTests=false -Dmaven.test.skip=false -DfailIfNoTests=true -Dtest='!xiaozhi.modules.pdc.nfc.service.PdcNfcTrustReleaseIntegrationTest,!xiaozhi.modules.pdc.nfc.service.PdcNfcClaimInviteReplacementIntegrationTest,!xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultImportIntegrationTest' clean verify` |
   | `main/manager-web/` | `cd main/manager-web && npm run test:unit && npm run check:i18n && npm run check:template` (node_modules is preinstalled by sandbox hook) |
   | `main/egg-miniprogram/` | `node main/egg-miniprogram/scripts/verify-project.js` |
   | `main/xiaozhi-server/` | `python3.12 -m compileall -q main/xiaozhi-server/core/ main/xiaozhi-server/config/ main/xiaozhi-server/plugins_func/` |

   JDK 21/Maven, Node 22, Python 3.12 and the writable `/home/agent/.m2` cache are available; there is no `mvnw`. The three excluded PDC NFC tests need Testcontainers/Docker-in-Docker, unavailable here — do not re-add them or skip other tests. xiaozhi-server checks are syntax-only; if the change needs runtime validation, flag it in your review output instead of claiming it passed.
3. Commit describing the refinements and leave no uncommitted changes. The host runner verifies the final committed code again and will refuse to merge on failure, even if you output the completion signal.

If the code is already clean and well-structured, do nothing.

Once complete, output <promise>COMPLETE</promise>.
