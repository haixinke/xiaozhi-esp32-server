# Sandcastle Java Verification Implementation Plan

**Goal:** Provision JDK 21/Maven, reuse the host Maven cache, and prevent automatic merges when required verification fails.

**Architecture:** Keep the existing sequential implement/review runner. The host runner owns validation commands and merge decisions; both agents share the existing Docker sandbox. Verify manager-api changes after implementation and again after review.

**Tech Stack:** Sandcastle 0.12, Node/TypeScript, Docker, JDK 21, Maven.

**Spec:** User-approved scope in this chat: add JDK/Maven, correct verification commands, enforce checks before automatic merge, and bind-mount host `~/.m2`.

## Constraints and review focus

- Preserve UID/GID alignment and existing agent setup.
- Mount `~/.m2` read-write at `/home/agent/.m2`; do not read its credentials into logs.
- Explicitly enable Maven tests. Missing infrastructure blocks verification rather than silently skipping tests.
- Verify the committed state; reject uncommitted changes and a changed host HEAD before merge.
- Review may introduce Java changes even when implementation did not; calculate scope again after review.
- Merge the verified commit SHA, and run checks before sandbox cleanup.

## Tasks

- [ ] Add behavioral runner tests using temporary Git repositories and substitutes for the external agent/container boundary. Observe failures with the current runner: failed checks must prevent merge; successful checks must precede merge; review changes must be rechecked; dirty worktrees must block merge; cache and repository paths must be correct.
- [ ] Add official JDK/Maven image stages and build-time version checks to `.sandcastle/Dockerfile`.
- [ ] Update `.sandcastle/main.mts` with cache mounting, toolchain preflight, scoped verification and checked merge flow; run runner tests.
- [ ] Correct both prompts and update the existing Sandcastle setup documentation, including test-service prerequisites and image rebuild steps.
- [ ] Run code review, Docker build/toolchain/cache smoke checks if available, runner tests and `graphify update .`. Report any environment blockers accurately.
