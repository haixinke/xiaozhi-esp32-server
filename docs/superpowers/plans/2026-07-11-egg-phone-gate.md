# Egg Phone Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require a successfully bound WeChat phone number before an unbound user can leave the welcome page, while preserving silent registration and fast entry for returning bound users.

**Architecture:** `App.onLaunch()` continues to establish the server session silently. A focused `wechat-api.js` module owns the authenticated `/wechat/bindPhone` call, `auth.js` owns durable `hasPhone` updates, and the welcome page gates navigation on `hasPhone` rather than `userId`.

**Tech Stack:** WeChat Mini Program JavaScript/WXML, Node.js built-in `assert`, existing `manager-api` `/wechat/bindPhone` endpoint.

## Global Constraints

- Do not request or populate WeChat nickname or avatar during onboarding.
- A user without `hasPhone=true` must remain on the welcome page.
- Direct entry to any registered non-welcome page must reLaunch the welcome page when `hasPhone!==true`.
- Phone authorization must start from an explicit `open-type="getPhoneNumber"` button after the privacy checkbox is selected.
- Rejection, missing phone code, login failure, or bind failure must not navigate to Home.
- Never log phone codes, tokens, openids, authorization headers, or full login responses.
- Preserve unrelated user changes in the dirty worktree.

---

### Task 1: Session phone-state update and API wrapper

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/utils/auth.js`
- Test: `main/egg-miniprogram/miniprogram/utils/auth.test.js`
- Create: `main/egg-miniprogram/miniprogram/utils/wechat-api.js`
- Create: `main/egg-miniprogram/miniprogram/utils/wechat-api.test.js`

**Interfaces:**
- Produces: `auth.markPhoneBound(): session|null`, which persists `hasPhone=true` without changing other session fields.
- Produces: `wechatApi.bindPhone(phoneCode): Promise<{phone: string}>`, which posts `{ phoneCode }` to `/wechat/bindPhone` with authentication.

- [ ] **Step 1: Write failing utility tests**

Add assertions that `markPhoneBound()` preserves the stored token/session fields and changes only `hasPhone`, and that `bindPhone('phone-code')` calls `post('/wechat/bindPhone', { phoneCode: 'phone-code' })`.

- [ ] **Step 2: Run tests to verify RED**

Run: `node main/egg-miniprogram/miniprogram/utils/auth.test.js && node main/egg-miniprogram/miniprogram/utils/wechat-api.test.js`

Expected: FAIL because `markPhoneBound` and `wechat-api.js` do not exist.

- [ ] **Step 3: Implement the minimal utilities**

Implement `markPhoneBound` through the existing session normalization/storage path and implement the API wrapper using the existing authenticated `post` helper.

- [ ] **Step 4: Run tests to verify GREEN**

Run the same command and expect both scripts to print `ALL PASS` with exit code 0.

### Task 2: Welcome-page phone gate

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.wxml`
- Test: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`

**Interfaces:**
- Consumes: `wechatApi.bindPhone(phoneCode)` and `auth.markPhoneBound()` from Task 1.
- Produces: `onAuthorize(event)` as the `bindgetphonenumber` handler and navigation based only on bound-phone state.

- [ ] **Step 1: Write failing page tests**

Cover: `onLoad` does not navigate for `{userId: 42, hasPhone: false}`; it navigates for `{userId: 42, hasPhone: true}`; unchecked privacy does not bind; missing/rejected phone code does not navigate; successful binding updates state and enters Home; bind failure resets loading and remains on the page.

- [ ] **Step 2: Run the page test to verify RED**

Run: `node main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`

Expected: FAIL because the current page navigates using `userId` and has no phone binding handler.

- [ ] **Step 3: Implement the minimal page flow**

Change the button to `open-type="getPhoneNumber" bindgetphonenumber="onAuthorize"`, gate the handler on the privacy checkbox and `e.detail.code`, ensure a valid silent session, call `bindPhone`, persist `hasPhone=true`, and navigate only after success. Keep the existing default local display user and safe error messages.

- [ ] **Step 4: Run the page test to verify GREEN**

Run the same command and expect `welcome.test.js: ALL PASS` with exit code 0.

### Task 3: Full verification and documentation consistency

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/app.js`
- Test: `main/egg-miniprogram/miniprogram/app.test.js`
- Modify: `main/egg-miniprogram/CLAUDE.md`
- Modify: `main/egg-miniprogram/docs/page-navigation.md` only if already tracked or explicitly required; otherwise leave the user's untracked file untouched.

**Interfaces:**
- Consumes: completed phone-gate flow.
- Produces: accurate project authentication documentation and fresh verification evidence.

- [ ] **Step 1: Guard direct page entry**

Add an application-level phone gate that allows the welcome route and bound sessions, but uses `wx.reLaunch('/pages/welcome/welcome')` when an unbound or missing session directly opens another registered page. Cover unbound Home entry, bound Home entry, and the Welcome non-loop case in `app.test.js`.

- [ ] **Step 2: Update project contract documentation**

Document that startup login remains silent, unbound users remain on Welcome, the phone authorization is mandatory, and returning users with `hasPhone=true` enter Home directly.

- [ ] **Step 3: Run all focused tests**

Run: `node main/egg-miniprogram/miniprogram/utils/auth.test.js && node main/egg-miniprogram/miniprogram/utils/wechat-api.test.js && node main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js && node main/egg-miniprogram/miniprogram/app.test.js && node main/egg-miniprogram/miniprogram/utils/request.test.js`

Expected: every script prints `ALL PASS`, exit code 0.

- [ ] **Step 4: Run project verification**

Run: `node main/egg-miniprogram/scripts/verify-project.js`

Expected: project verification passes.

- [ ] **Step 5: Run syntax and JSON validation**

Run the project-prescribed JavaScript syntax and JSON parse commands from `main/egg-miniprogram/CLAUDE.md`; expect exit code 0.
