# Egg Home Pet Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the server-side egg pet after a cache-cleared cold launch so the home page renders the pet's actual hatch state.

**Architecture:** Keep the existing Home recovery path as the sole implementation: `onShow()` reads the local cache or calls `GET /pet/list`, then maps the result through `petStore.savePetFromVO()`. Close the login-to-home lifecycle gap by invoking that recovery path once `authReady` resolves.

**Tech Stack:** WeChat Mini Program JavaScript; Node.js `assert` unit tests.

## Global Constraints

- Make only the Home lifecycle and its existing unit test change.
- The backend remains the source of truth for pet existence and `hatchStatus`.
- Do not log or persist login credentials, tokens, OpenID, or wx.login codes.
- Preserve the existing no-pet empty state and existing local-cache fast path.

---

### Task 1: Restore pet state after asynchronous login

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.test.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.js`

**Interfaces:**
- Consumes: `app.globalData.authReady: Promise<Session | null>` and `get('/pet/list'): Promise<PetVO[]>`.
- Produces: `home.data.stage === 'hatched'` for a returned `PetVO` with `hatchStatus: 'HATCHED'`.

- [ ] **Step 1: Write the failing test**

Add counters and configurable stubs for `petStore.getPet`, `petStore.savePetFromVO`, and `request.get`. Add this scenario after the existing asynchronous-login browsing scenario:

```js
resetScenario();
cachedSession = null;
const hatchedPet = { id: 'pet-1', hatchStatus: 'HATCHED', prototype: '玉兔' };
app.globalData.authReady = Promise.resolve({ userId: 42, hasPhone: true });
requestGetResult = [hatchedPet];
const coldStartPage = makePage();
coldStartPage.onLoad();
await Promise.resolve();
await Promise.resolve();
assert.strictEqual(requestGetCalls, 1);
assert.deepStrictEqual(savedPetVO, hatchedPet);
assert.strictEqual(coldStartPage.data.stage, 'hatched');
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`

Expected: FAIL because no `/pet/list` request occurs after `authReady` resolves.

- [ ] **Step 3: Write minimal implementation**

In the successful `authReady` branch in `onLoad()`, call the existing recovery path after the auth gate opens:

```js
this.setData({ authChecked: true });
this.onShow();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`

Expected: `home.test.js: ALL PASS` and exit code 0.

- [ ] **Step 5: Run focused regression verification**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js && node main/egg-miniprogram/miniprogram/app.test.js && node main/egg-miniprogram/scripts/verify-project.js`

Expected: all commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/home/home.js main/egg-miniprogram/miniprogram/pages/home/home.test.js docs/superpowers/specs/2026-07-18-egg-home-pet-restore-design.md docs/superpowers/plans/2026-07-18-egg-home-pet-restore.md
git commit -m "fix(egg-miniprogram): restore pet after cold login"
```
