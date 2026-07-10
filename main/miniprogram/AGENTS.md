# AGENTS.md

Codex instructions for the native WeChat Mini Program in this directory. Keep root `../../AGENTS.md` in mind, and use this file for Mini Program specific rules.

## Project Shape

- Native WeChat Mini Program using JavaScript, WXML, WXSS, and JSON.
- Module style is CommonJS.
- This is not a uni-app, Vue, React, or CloudBase project.
- Auth currently uses `wx.login` to call the manager API login flow and stores token/openid locally.
- REST traffic points to the manager API, and voice/WebSocket traffic points to the xiaozhi server.
- Local Opus JavaScript/WASM assets are used for voice features.

## Common Commands

- Open and debug with WeChat DevTools by importing `main/miniprogram`.
- Run all JavaScript tests from this directory:

```sh
for test_file in $(find . -name '*.test.js' -print | sort); do node "$test_file" || exit 1; done
```

- Run one test: `node path/to/file.test.js`

Notes:

- There is no package manager, npm script, formatter, or linter configured here.
- For UI, device, recording, and WebSocket behavior, verify in WeChat DevTools and on a real device when risk warrants it.

## Directory Conventions

- Pages and components should keep the standard four-file shape: `.js`, `.json`, `.wxml`, `.wxss`.
- Register new pages in `app.json`.
- Register custom components through `usingComponents`.
- Keep pure logic testable outside the WeChat runtime where practical.
- Follow existing UI conventions and `DESIGN.md` before changing visual patterns.

## API And Auth Rules

- Do not casually change `BASE_URL` or WebSocket endpoints. Use the project local-IP workflow when switching to local development.
- On `401`, follow the existing silent refresh behavior and retry at most once.
- Reuse the current login promise/concurrency pattern so parallel requests do not trigger duplicate logins.
- Never log token, openid, unionid, `wx.login` code, full login responses, or authenticated WebSocket URLs.

## Voice And WebSocket Rules

- Treat socket state, reconnect timers, recording state, playback state, and page lifetime as one system.
- On page unload/destruction, release sockets, recorders, audio contexts, intervals, and timeouts.
- Keep timeout and reconnect behavior explicit so calls do not hang silently.

## Testing Expectations

- Add or update tests for logic changes that can run under Node.
- For Mini Program APIs, validate manually in WeChat DevTools.
- For recording, playback, and socket flows, include real-device verification when the change touches runtime permissions or audio behavior.

## Documentation

- `CLAUDE.md` is historical context, not guaranteed current truth.
- Avoid copying stale directory descriptions or old initialization flows without checking source files.
