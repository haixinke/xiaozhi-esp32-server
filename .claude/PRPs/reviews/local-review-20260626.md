# Local Code Review — 2026-06-26

**Scope**: Uncommitted changes in `main/manager-api` and `main/miniprogram`
**Decision**: APPROVE with comments (no CRITICAL/HIGH issues)

## Files Reviewed

| File | Change Type | Lines |
|---|---|---|
| `main/manager-api/pom.xml` | Modified | +20 |
| `main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java` | Modified | +25, -7 |
| `main/manager-api/src/test/java/xiaozhi/modules/companion/util/CompanionMoodTest.java` | Modified | +18, -7 |
| `main/miniprogram/utils/request.js` | Modified | +4, -4 |

## Findings

### CRITICAL
None.

### HIGH
None.

### MEDIUM

#### 1. `main/miniprogram/utils/request.js` — Production BASE_URL committed
**Location**: Lines 7-9
**Issue**: The diff switches `BASE_URL` from a local development IP to the production domain `https://chat-api.benniu.tech/xiaozhi`. This is an environment-specific change that should not be committed to shared branches unless intentional, as it will break local development for other contributors and force all builds to point to production.
**Suggested fix**: Keep development URLs in the working tree only, or introduce a build-time/environment-based config mechanism (e.g., `project.config.json`/`project.private.config.json` overrides, or a separate `config.js` that is gitignored). If this change is intentional for a release, mention it explicitly in the commit message.

### LOW

#### 2. `main/manager-api/src/test/java/xiaozhi/modules/companion/util/CompanionMoodTest.java` — Trailing blank line
**Location**: End of file
**Issue**: An extra trailing blank line was added after the closing brace.
**Suggested fix**: Remove the extra blank line to keep the file tidy.

## Validation Results

| Check | Result |
|---|---|
| Targeted unit tests (`CompanionMoodTest`, `CompanionServiceImplTest`, `CompanionMoodRefreshTaskTest`) | Pass |
| JaCoCo report generation | Pass |
| Security scan (hardcoded secrets, injection risks) | Pass |

## Notes

- `pom.xml` addition of `jacoco-maven-plugin:0.8.12` is standard and correct for Java 21.
- The new `update_moodIsNormalizedToUpperCase` test correctly validates the mood uppercase normalization path.
- Removing the `isNotEqualTo("CALM")` assertion in `refreshAllMoods_updatesAllCompanionsAndSyncs` is the right fix for the flaky random-mood test.
- The `fromCode` tests appropriately cover null/blank/unknown inputs and case-insensitive lookups.

## Recommendation

Approve the `manager-api` changes. Consider reverting or isolating the `request.js` BASE_URL change before merging unless it is an intentional production configuration update.
