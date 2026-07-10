# AGENTS.md

Codex instructions for the egg-miniprogram subproject. This project does not currently have a local `CLAUDE.md`, so these rules are based on the README, PRD, WeChat project files, and current source shape.

## Project Shape

- Native WeChat Mini Program using JavaScript, WXML, WXSS, and JSON.
- The project root is this directory. `project.config.json` does not define a separate `miniprogramRoot`.
- There is no package manager, build script, app-wide request wrapper, app-wide auth module, or automated test framework yet.
- Current source is mostly a UI skeleton with static or simulated data. Do not describe unimplemented flows as production behavior.
- There is no executable CloudBase or `wx.cloud` integration in the current source.

## Common Checks

Run these from the repository root or this subproject when changing source:

```sh
find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
find main/egg-miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
```

Also verify changed pages in WeChat DevTools. Use real-device testing when touching camera, scan, subscription messages, customer service, login, device binding, or audio/video behavior.

## Directory Conventions

- Keep pages and components in standard four-file WeChat Mini Program shape: `.js`, `.json`, `.wxml`, `.wxss`.
- Register new pages in `app.json`.
- Register custom components through `usingComponents`.
- Reuse style tokens and layout conventions from `app.wxss`.
- Keep static assets under the existing assets structure.

## Product And State Rules

- Treat backend-owned state as authoritative once APIs exist: device ownership, pet ownership, hatch state, events, personality, skills, achievements, invites, and account status.
- Query parameters and client-side IDs are hints only. Do not trust them for ownership or authorization.
- Client-only locks, such as one-time gender or birthday edits, must be enforced by the backend when the backend is introduced.
- Account deletion, cooling periods, and related copy are currently UI/product text only unless backed by APIs.
- The hatch-time rule has a known product conflict: current code comments say interactions do not change hatch date, while the PRD mentions tasks reducing hatch time. Confirm product direction before changing this behavior.

## API And Auth Rules

- Auth architecture is not implemented yet. Choose and document the approach before adding login, token storage, refresh, or backend calls.
- Do not claim CloudBase login-free auth unless real CloudBase integration is added.
- Never commit app secrets, tokens, openid, unionid, `wx.login` code, customer-service IDs, template IDs, private API URLs, or real user data.
- Avoid logging sensitive request or account information.

## WeChat Feature Notes

- Subscription messages and customer service currently use placeholders or static IDs.
- Avatar selection uses WeChat profile/avatar capabilities.
- Scan and device flows are simulated in current source.
- Chat, device, pet, hatch, and invite behavior are not wired to real backend APIs yet.

## Version Control Notes

- This subproject currently contains untracked working files in many directories. Do not reformat or reorganize them unless the task explicitly asks.
- `project.private.config.json`, `.DS_Store`, generated outputs, and local tool state should stay local.
- Be careful with root ignore rules. Essential Mini Program `.json` files must be included in review even if global ignore patterns hide them.
