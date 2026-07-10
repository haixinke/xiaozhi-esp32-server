# AGENTS.md

Codex instructions for the manager-api subproject. This file is the local source of truth for agent work in this directory. Keep root `../../AGENTS.md` in mind, and use this file for Spring Boot specific rules.

## Project Shape

- Java 21, Spring Boot 3.4.x, Maven packaging as a jar.
- Main stack: MyBatis-Plus, MySQL, Druid, Redis, Liquibase, Shiro, SpringDoc/Knife4j, JUnit 5, Mockito, JaCoCo.
- Work from `main/manager-api` unless the task explicitly spans multiple projects.
- There is no Maven wrapper in this subproject. Use the local `mvn`.

## Common Commands

- Compile: `mvn compile`
- Package: `mvn package`
- Run locally: `mvn spring-boot:run`
- Run all tests: `mvn test -DskipTests=false`
- Run one test class: `mvn test -DskipTests=false -Dtest=ClassName`
- Run one test method: `mvn test -DskipTests=false -Dtest=ClassName#methodName`
- Run verification with JaCoCo report: `mvn verify -DskipTests=false`

Notes:

- `pom.xml` skips tests by default, so pass `-DskipTests=false` when test execution matters.
- Some Spring Boot tests use local profiles and may need MySQL or Redis.
- Do not invent lint, formatter, Checkstyle, or Spotless commands unless they are added to the project.

## Directory Conventions

- Keep common infrastructure in the existing common/config/security style locations.
- Keep domain work inside the existing module structure.
- Put MyBatis mapper XML in the established resources mapper locations.
- Keep Liquibase files under the existing database changelog structure.
- Keep tests close to the package and behavior being changed.

## Database Changes

- Use Liquibase for schema changes.
- Add a new dated SQL/changelog entry and register it in the master changelog.
- Do not edit historical migration SQL or old changesets after they may have run.
- Treat database data volume seriously. Use pagination and batches for large operations.

## Backend Rules

- API responses should follow the existing `Result<T>` pattern, with success code `0`.
- Add new error cases through the centralized error code and i18n message structure.
- For auth or route changes, inspect the current Shiro configuration first. Do not duplicate route lists.
- For scheduled or bulk tasks, avoid full-table reads such as unbounded `selectList(null)`.
- In batch work, isolate per-record failure where possible, use database batch APIs, and log useful counts.

## Security

- Never log tokens, passwords, secrets, raw authorization headers, or full auth responses.
- Do not commit local config, generated files, logs, `target/`, IDE metadata, or compiled classes.
- Validate request input at the API boundary and fail with explicit errors.

## Documentation

- `CLAUDE.md` can be useful historical context, but verify commands and architecture against current files before copying details.
- If behavior changes, update the closest existing project document instead of creating unrelated top-level docs.
