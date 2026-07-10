# AGENTS.md

Codex instructions for the xiaozhi-server Python service. Keep root `../../AGENTS.md` in mind, and use this file for Python service specific rules.

## Project Shape

- Python 3.12 service.
- Async WebSocket server on the main service port and an aiohttp HTTP service on the health/config port.
- Uses YAML configuration, loguru logging, provider abstractions, plugins, ffmpeg, and opus support.
- Provider categories include ASR, TTS, LLM, VAD, Intent, Memory, and VLLM.
- Work from `main/xiaozhi-server` unless the task explicitly spans multiple projects.

## Common Commands

- Install dependencies: `python3.12 -m pip install -r requirements.txt`
- Run locally: `python app.py`
- Build local Docker image: `docker build -t xiaozhi-server:local .`
- Run compose stack: `docker compose up -d`
- Run OceanBase compose stack when needed by the task: use the existing compose file in this directory.
- Manual performance probe: `python performance_tester.py`

Notes:

- `docker compose up -d` may use published remote images rather than the current source tree. Use `docker build` when validating local source changes in an image.
- There is no configured pytest suite, linter, or formatter unless one is added later.
- `test_volmem0_api.py` is a manual network probe with placeholders, not a normal automated test suite.

## Configuration

- Keep real local configuration in `data/.config.yaml`, which is ignored by git.
- Never commit API keys, passwords, tokens, private endpoints, local model credentials, or real user data.
- An empty `data/.config.yaml` is not a valid safe default because YAML loading can return `None`; use a minimal valid YAML object when needed.
- Shared example config should avoid real secrets.

## Provider And Plugin Rules

- Follow the existing provider base classes, factories, and registration patterns.
- Add new provider behavior behind the relevant abstraction instead of special-casing call sites.
- Plugins should follow the existing plugin directory and decorator registration style.
- Keep async paths nonblocking. Avoid blocking file, network, or CPU work on the event loop.

## Runtime And Data Rules

- Do not edit generated runtime folders, logs, model artifacts, caches, or temporary data unless the task is explicitly about those files.
- Avoid personal absolute paths in committed config, tests, scripts, and docs.
- Redact logs before sharing output.

## Testing Expectations

- For narrow Python logic, add focused tests only after confirming the project has a suitable test harness or after adding one intentionally.
- For service behavior, verify with the smallest practical local run or targeted manual probe.
- When a test cannot be automated because it needs external services or secrets, document the exact manual check performed.

## Documentation

- `CLAUDE.md` is historical context and contains some stale details. Verify Python version, commands, and dependency claims against current files before reusing them.
