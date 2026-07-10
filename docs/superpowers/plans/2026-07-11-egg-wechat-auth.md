# 蛋宝宝微信注册与授权登录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让蛋宝宝微信小程序通过现有 `/wechat/login` 完成真实注册/登录，并安全维护、刷新和清除 Bearer 登录态。

**Architecture:** 将配置、认证存储、HTTP 请求和 App 登录协调拆成独立模块；欢迎页只承担隐私同意与登录交互，账号页通过 App 统一退出。请求层显式标记匿名请求，鉴权请求遇到 401 时共享 App 的单例登录并只重试一次。

**Tech Stack:** 微信原生小程序 JavaScript、Node.js 内置 `assert`、微信 `wx.login`/`wx.request`/Storage API、现有 Java Spring Boot `/xiaozhi/wechat/login`。

## Global Constraints

- 本次不修改 `manager-api`，不新增后端测试。
- 首次登录不调用 `wx.getUserProfile`，不强制昵称、头像或手机号授权。
- 正常入口登录失败时留在欢迎页，不降级成本地 Mock 登录。
- `userId` 是业务账号主键；`openid` 不得作为宠物或设备 ID。
- 不记录微信 code、token、openid、Authorization、完整登录响应、密码或 AppSecret。
- token 在到期前 300 秒视为临期；401 最多刷新并重试一次。
- 退出清除认证态和账号相关 Mock 数据，保留非账号界面偏好。
- 保留工作树中已有的 `main/egg-miniprogram/CLAUDE.md` 与 `graphify-out/` 修改，不得覆盖或提交。

---

### Task 1: 认证存储与服务地址配置

**Files:**
- Create: `main/egg-miniprogram/miniprogram/config/api.js`
- Create: `main/egg-miniprogram/miniprogram/utils/auth.js`
- Create: `main/egg-miniprogram/miniprogram/utils/auth.test.js`

**Interfaces:**
- Produces: `API_BASE_URL: string`
- Produces: `saveSession(session, issuedAt?)`, `getSession()`, `clearSession()`, `hasValidSession(now?)`, `isExpired(now?)`, `isExpiringSoon(now?, bufferSeconds?)`
- Session shape: `{token: string, userId: number|string, openid: string, isNewUser: boolean, hasPhone: boolean, agentId: string|null, issuedAt: number, expire: number}`

- [ ] **Step 1: Write the failing auth storage test**

Create `utils/auth.test.js` with an in-memory `wx` storage mock. Verify that `saveSession` rejects incomplete input without writing, atomically persists a complete response, `getSession` reconstructs it, a 12-hour token becomes “expiring soon” inside the last 300 seconds, and `clearSession` removes every auth key.

```js
const assert = require('assert');

const storage = new Map();
global.wx = {
  getStorageSync(key) { return storage.has(key) ? storage.get(key) : ''; },
  setStorageSync(key, value) { storage.set(key, value); },
  removeStorageSync(key) { storage.delete(key); }
};

const auth = require('./auth');
const now = 1_700_000_000_000;
const login = {
  token: 'test-token', userId: 42, openid: 'test-openid',
  isNewUser: true, hasPhone: false, agentId: null, expire: 43_200
};

assert.throws(() => auth.saveSession({ userId: 42 }, now), /登录响应缺少必要字段/);
assert.strictEqual(storage.size, 0);

auth.saveSession(login, now);
assert.deepStrictEqual(auth.getSession(), { ...login, issuedAt: now });
assert.strictEqual(auth.hasValidSession(now + 1000), true);
assert.strictEqual(auth.isExpiringSoon(now + 43_200_000 - 301_000), false);
assert.strictEqual(auth.isExpiringSoon(now + 43_200_000 - 299_000), true);
assert.strictEqual(auth.isExpired(now + 43_200_000), true);

auth.clearSession();
assert.strictEqual(auth.getSession(), null);
assert.strictEqual(storage.size, 0);
console.log('auth.test.js: ALL PASS');
```

- [ ] **Step 2: Run the auth test and verify RED**

Run:

```bash
node main/egg-miniprogram/miniprogram/utils/auth.test.js
```

Expected: FAIL with `Cannot find module './auth'`.

- [ ] **Step 3: Implement the minimum auth module and API config**

Create `config/api.js` with the same development URL currently used by the sister mini program; keep the `/xiaozhi` context path. When the workstation IP changes, use the project `/mini-ip` workflow to update this value.

```js
const API_BASE_URL = 'http://192.168.4.12:8002/xiaozhi';

module.exports = { API_BASE_URL };
```

In `utils/auth.js`, use namespaced keys so they cannot collide with the sister mini program:

```js
const PREFIX = 'eggbaby_auth_';
const KEYS = {
  token: `${PREFIX}token`, userId: `${PREFIX}user_id`, openid: `${PREFIX}openid`,
  isNewUser: `${PREFIX}is_new_user`, hasPhone: `${PREFIX}has_phone`,
  agentId: `${PREFIX}agent_id`, issuedAt: `${PREFIX}issued_at`, expire: `${PREFIX}expire`
};
const REQUIRED = ['token', 'userId', 'openid', 'expire'];

function saveSession(session, issuedAt) {
  if (!session || REQUIRED.some((key) => session[key] === undefined || session[key] === null || session[key] === '')) {
    throw new Error('登录响应缺少必要字段');
  }
  const normalized = {
    token: session.token, userId: session.userId, openid: session.openid,
    isNewUser: !!session.isNewUser, hasPhone: !!session.hasPhone,
    agentId: session.agentId || null, issuedAt: issuedAt || Date.now(), expire: Number(session.expire)
  };
  if (!Number.isFinite(normalized.expire) || normalized.expire <= 0) throw new Error('登录响应缺少必要字段');
  Object.keys(KEYS).forEach((key) => wx.setStorageSync(KEYS[key], normalized[key]));
  return normalized;
}

function getSession() {
  const session = {};
  Object.keys(KEYS).forEach((key) => { session[key] = wx.getStorageSync(KEYS[key]); });
  if (REQUIRED.some((key) => session[key] === undefined || session[key] === null || session[key] === '')) return null;
  return session;
}

function expiresAt(session) { return session.issuedAt + session.expire * 1000; }
function isExpired(now) { const s = getSession(); return !s || (now || Date.now()) >= expiresAt(s); }
function isExpiringSoon(now, bufferSeconds) {
  const s = getSession();
  return !s || (now || Date.now()) + (bufferSeconds === undefined ? 300 : bufferSeconds) * 1000 >= expiresAt(s);
}
function hasValidSession(now) { return !isExpired(now); }
function clearSession() { Object.values(KEYS).forEach((key) => wx.removeStorageSync(key)); }

module.exports = { saveSession, getSession, clearSession, hasValidSession, isExpired, isExpiringSoon };
```

- [ ] **Step 4: Run the auth test and verify GREEN**

Run `node main/egg-miniprogram/miniprogram/utils/auth.test.js`.

Expected: `auth.test.js: ALL PASS`.

- [ ] **Step 5: Commit the focused change**

```bash
git add main/egg-miniprogram/miniprogram/config/api.js main/egg-miniprogram/miniprogram/utils/auth.js main/egg-miniprogram/miniprogram/utils/auth.test.js
git commit -m "feat: add egg mini program auth storage"
```

---

### Task 2: 统一请求层与 401 单次重试

**Files:**
- Create: `main/egg-miniprogram/miniprogram/utils/request.js`
- Create: `main/egg-miniprogram/miniprogram/utils/request.test.js`

**Interfaces:**
- Consumes: `API_BASE_URL`, `auth.getSession()`, `auth.clearSession()`, `getApp().silentLogin()`
- Produces: `request({url, method?, data?, header?, anonymous?}): Promise<any>`; `_retried401` is private to the module
- Produces: `get(url, data?, options?)`, `post(url, data?, options?)`, `put(url, data?, options?)`, `del(url, data?, options?)`
- Success resolves directly to the backend envelope's `data`.
- Failure rejects `{type, statusCode, code, userMessage}` without sensitive response content.

- [ ] **Step 1: Write failing request behavior tests**

Create a Promise-based test with mocked `wx.request`, auth module, and `getApp`. Cover these exact behaviors:

```js
const assert = require('assert');
const Module = require('module');

let token = 'old-token';
let requestCalls = [];
let loginCalls = 0;
let responses = [];
global.wx = { request(options) { requestCalls.push(options); options.success(responses.shift()); } };
global.getApp = () => ({ silentLogin: async () => { loginCalls += 1; token = 'new-token'; } });

const originalLoad = Module._load;
Module._load = function (request) {
  if (request === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
  if (request === './auth') return {
    getSession: () => token ? { token } : null,
    clearSession: () => { token = ''; }
  };
  return originalLoad.apply(this, arguments);
};
const api = require('./request');

(async () => {
  responses = [{ statusCode: 200, data: { code: 0, data: { userId: 42 } } }];
  assert.deepStrictEqual(await api.post('/wechat/login', { code: 'test' }, { anonymous: true }), { userId: 42 });
  assert.strictEqual(requestCalls[0].header.Authorization, undefined);

  responses = [
    { statusCode: 401, data: { code: 10021, msg: 'expired' } },
    { statusCode: 200, data: { code: 0, data: { ok: true } } }
  ];
  assert.deepStrictEqual(await api.get('/pet/list'), { ok: true });
  assert.strictEqual(loginCalls, 1);
  assert.strictEqual(requestCalls.at(-1).header.Authorization, 'Bearer new-token');

  responses = [{ statusCode: 200, data: { code: 10201, msg: 'bad' } }];
  await assert.rejects(api.get('/pet/list'), (error) => error.type === 'business' && error.code === 10201);

  responses = [
    { statusCode: 401, data: {} },
    { statusCode: 401, data: {} }
  ];
  await assert.rejects(api.get('/pet/list'), (error) => error.type === 'unauthorized');
  assert.strictEqual(responses.length, 0);
  console.log('request.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
```

- [ ] **Step 2: Run request test and verify RED**

Run `node main/egg-miniprogram/miniprogram/utils/request.test.js`.

Expected: FAIL with `Cannot find module './request'`.

- [ ] **Step 3: Implement envelope parsing, explicit anonymity, and retry guard**

Implement `request.js` so that `anonymous` defaults to `false`, an unauthenticated protected request rejects before `wx.request`, and `_retried401` is internal-only. Build headers without an empty Authorization field. For 2xx responses require a response object with numeric `code`; resolve `data` only when `code === 0`. On 401 and no previous retry, clear the old session, await `getApp().silentLogin()`, then issue one cloned request with `_retried401: true`.

Use these stable user messages:

```js
const MESSAGES = {
  network: '暂时无法连接服务，请稍后重试',
  unauthorized: '登录状态已失效，请重新登录',
  business: '操作失败，请稍后重试',
  invalidResponse: '服务响应异常，请稍后重试'
};
```

Do not attach raw response bodies, request bodies, headers, or tokens to rejected errors.

- [ ] **Step 4: Run request tests and verify GREEN**

Run `node main/egg-miniprogram/miniprogram/utils/request.test.js`.

Expected: `request.test.js: ALL PASS`.

- [ ] **Step 5: Commit the focused change**

```bash
git add main/egg-miniprogram/miniprogram/utils/request.js main/egg-miniprogram/miniprogram/utils/request.test.js
git commit -m "feat: add authenticated mini program requests"
```

---

### Task 3: App 登录协调器、恢复与并发锁

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/app.js`
- Create: `main/egg-miniprogram/miniprogram/app.test.js`

**Interfaces:**
- Consumes: `auth.getSession/saveSession/clearSession/isExpired/isExpiringSoon`, `request.post`
- Produces: `App.silentLogin(): Promise<Session>`
- Produces: `App.ensureLogin(): Promise<Session>`
- Produces: `App.clearLoginState(): void`
- Produces: `globalData.authReady: Promise`, plus session fields `token/userId/openid/isNewUser/hasPhone/agentId`

- [ ] **Step 1: Write failing App tests**

Use `Module._load` to mock `./utils/auth` and `./utils/request`, and capture the object passed to `global.App`. Test:

```js
assert.ok(appConfig, 'App should be registered');
const first = appConfig.silentLogin.call(appConfig);
const second = appConfig.silentLogin.call(appConfig);
assert.strictEqual(first, second, 'concurrent login must share one Promise');
const session = await first;
assert.strictEqual(wxLoginCalls, 1);
assert.strictEqual(postCalls, 1);
assert.strictEqual(session.userId, 42);
assert.strictEqual(appConfig.globalData.userId, 42);
assert.strictEqual(savedSession.userId, 42);

wxLoginResult = { fail: new Error('denied') };
await assert.rejects(appConfig.silentLogin.call(appConfig), /微信登录失败/);
wxLoginResult = { code: 'second-code' };
await appConfig.silentLogin.call(appConfig);
assert.strictEqual(wxLoginCalls, 3, 'lock must release after failure');
```

Also verify `ensureLogin` restores a valid saved session without calling `wx.login`, `onShow` refreshes only when the session is expiring soon, and `clearLoginState` clears storage and nulls all global auth fields.

- [ ] **Step 2: Run App test and verify RED**

Run `node main/egg-miniprogram/miniprogram/app.test.js`.

Expected: FAIL because the current App object has no `silentLogin` or `ensureLogin`.

- [ ] **Step 3: Implement the minimum App coordinator**

Replace the current “store login code in globalData” behavior. The core login method must follow this shape:

```js
silentLogin() {
  if (this._loginPromise) return this._loginPromise;
  this._loginPromise = loginWithWechat()
    .then((loginData) => {
      const session = auth.saveSession(loginData);
      this.applySession(session);
      return session;
    });
  this._loginPromise.then(
    () => { this._loginPromise = null; },
    () => { this._loginPromise = null; }
  );
  return this._loginPromise;
}
```

`loginWithWechat()` wraps `wx.login`, rejects when no code is returned, and calls:

```js
post('/wechat/login', { code: loginResult.code }, { anonymous: true })
```

`onLaunch` assigns `globalData.authReady = ensureLogin()`. Catch startup failure into a resolved “not logged in” state so no unhandled rejection is produced; the welcome page remains responsible for presenting retry UI. `onShow` refreshes a soon-expiring session but never logs sensitive identifiers.

- [ ] **Step 4: Run App tests and verify GREEN**

Run `node main/egg-miniprogram/miniprogram/app.test.js`.

Expected: `app.test.js: ALL PASS`.

- [ ] **Step 5: Commit the focused change**

```bash
git add main/egg-miniprogram/miniprogram/app.js main/egg-miniprogram/miniprogram/app.test.js
git commit -m "feat: connect egg app to wechat login"
```

---

### Task 4: 欢迎页使用真实登录态

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.wxml`
- Create: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`

**Interfaces:**
- Consumes: `getApp().ensureLogin()`, `getApp().silentLogin()`, `getApp().globalData.userId`
- Consumes: `petStore.saveUser({id, nickname, avatarUrl, authorizedAt})`
- Produces: welcome page behavior that gates navigation on a real backend session

- [ ] **Step 1: Write failing welcome page tests**

Capture the `Page` config and stub `petStore`, `getApp`, and `wx`. Test three cases:

```js
const page = makePage();
page.onAuthorize();
assert.strictEqual(loginCalls, 0);
assert.strictEqual(toasts.at(-1), '请先阅读并同意隐私政策');

page.setData({ agreed: true });
await page.onAuthorize();
assert.deepStrictEqual(savedUser, {
  id: 42, nickname: '蛋友', avatarUrl: '', authorizedAt: fixedNow
});
assert.strictEqual(switchedTo, '/pages/home/home');
assert.strictEqual(profileCalls, 0);

loginError = new Error('network');
await page.onAuthorize();
assert.strictEqual(switchedTo, null);
assert.strictEqual(page.data.authorizing, false);
assert.strictEqual(toasts.at(-1), '暂时无法连接服务，请稍后重试');
```

Also verify `onLoad` redirects only after `ensureLogin` returns a valid session, not merely because `petStore.getUser()` exists.

- [ ] **Step 2: Run welcome test and verify RED**

Run `node main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`.

Expected: FAIL because the current page calls `wx.getUserProfile` and does not await server login.

- [ ] **Step 3: Implement real-login welcome behavior**

Make `onAuthorize` async and return its Promise for testability. After agreement:

```js
this.setData({ authorizing: true });
try {
  const session = await getApp().silentLogin();
  petStore.saveUser({
    id: session.userId,
    nickname: '蛋友',
    avatarUrl: '',
    authorizedAt: Date.now()
  });
  wx.switchTab({ url: '/pages/home/home' });
} catch (error) {
  wx.showToast({ title: error.userMessage || '暂时无法连接服务，请稍后重试', icon: 'none' });
} finally {
  this.setData({ authorizing: false });
}
```

Remove every `wx.getUserProfile` call. Change the button text from “微信授权并进入” to “微信登录并进入”，and change the note to explain that login only establishes account identity and does not request nickname, avatar, or phone number.

- [ ] **Step 4: Run welcome tests and verify GREEN**

Run `node main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`.

Expected: `welcome.test.js: ALL PASS`.

- [ ] **Step 5: Commit the focused change**

```bash
git add main/egg-miniprogram/miniprogram/pages/welcome/welcome.js main/egg-miniprogram/miniprogram/pages/welcome/welcome.wxml main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js
git commit -m "feat: require server login on egg welcome page"
```

---

### Task 5: 安全退出、账号数据清理与最终验证

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.js`
- Create: `main/egg-miniprogram/miniprogram/utils/pet-store.test.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/account/account.js`
- Create: `main/egg-miniprogram/miniprogram/pages/account/account.test.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/account/account.wxml`

**Interfaces:**
- Produces: `petStore.clearAccountData(): void`
- Consumes: `getApp().clearLoginState()`, `petStore.clearAccountData()`

- [ ] **Step 1: Write failing account-data cleanup tests**

In `pet-store.test.js`, seed these exact keys and an unrelated preference key:

```js
const accountKeys = [
  'eggbaby_mvp_pet_v1',
  'eggbaby_mvp_user_v1',
  'eggbaby_mvp_identity_v1',
  'eggbaby_exhibition_backup_v1'
];
accountKeys.forEach((key) => storage.set(key, { value: key }));
storage.set('eggbaby_theme', 'light');
petStore.clearAccountData();
accountKeys.forEach((key) => assert.strictEqual(storage.has(key), false));
assert.strictEqual(storage.get('eggbaby_theme'), 'light');
```

In `account.test.js`, capture the Page config and simulate a confirmed modal. Verify call order includes both `app.clearLoginState()` and `petStore.clearAccountData()`, then `wx.reLaunch('/pages/welcome/welcome')`. Verify cancel performs no cleanup.

- [ ] **Step 2: Run cleanup tests and verify RED**

Run:

```bash
node main/egg-miniprogram/miniprogram/utils/pet-store.test.js
node main/egg-miniprogram/miniprogram/pages/account/account.test.js
```

Expected: FAIL because `clearAccountData` is not exported and account logout only calls `clearUser`.

- [ ] **Step 3: Implement account-data cleanup and logout wiring**

Add an immutable list of account keys in `pet-store.js` and export:

```js
function clearAccountData() {
  [PET_KEY, USER_KEY, IDENTITY_KEY, EXHIBITION_BACKUP_KEY]
    .forEach((key) => {
      try { wx.removeStorageSync(key); } catch (error) {}
    });
}
```

Update the modal copy to “退出后将清除本机账号及蛋宝宝体验数据。” On confirmation call App auth cleanup first, then pet-store cleanup, then relaunch. Keep the existing separate “重置本地体验数据” action unchanged.

- [ ] **Step 4: Run cleanup tests and verify GREEN**

Run both commands from Step 2.

Expected: both print `ALL PASS`.

- [ ] **Step 5: Run every new test**

```bash
find main/egg-miniprogram/miniprogram -type f -name '*.test.js' -print0 | xargs -0 -n1 node
```

Expected: every auth, request, App, welcome, pet-store, and account test prints `ALL PASS`; exit code 0.

- [ ] **Step 6: Run project integrity and syntax validation**

```bash
node main/egg-miniprogram/scripts/verify-project.js
find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
find main/egg-miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
git diff --check
```

Expected: project verification passes, all syntax/JSON checks exit 0, and `git diff --check` prints nothing.

- [ ] **Step 7: Review for sensitive logging and forbidden identity coupling**

Run:

```bash
rg -n "console\.|loginCode|Authorization|openid.*device|device.*openid|getUserProfile" main/egg-miniprogram/miniprogram
```

Expected: no sensitive authentication logging, no saved login code, no `openid`-as-device coupling, and no `wx.getUserProfile` in the welcome flow. The literal `Authorization` is allowed only in `utils/request.js` and its test.

- [ ] **Step 8: Commit the focused change**

```bash
git add main/egg-miniprogram/miniprogram/utils/pet-store.js main/egg-miniprogram/miniprogram/utils/pet-store.test.js main/egg-miniprogram/miniprogram/pages/account/account.js main/egg-miniprogram/miniprogram/pages/account/account.wxml main/egg-miniprogram/miniprogram/pages/account/account.test.js
git commit -m "feat: clear egg account data on logout"
```

- [ ] **Step 9: Perform manual WeChat verification**

In WeChat Developer Tools, import `main/egg-miniprogram/`, confirm its AppID matches the backend environment, and test:

1. Unchecked privacy agreement blocks login.
2. Checked agreement performs real backend login and reaches home.
3. New login creates a backend user; later login returns the same `userId`.
4. No nickname/avatar/phone authorization dialog appears.
5. Backend outage leaves the user on welcome and retry later succeeds.
6. A forced 401 triggers one silent login and then the protected request succeeds or stops cleanly.
7. Logout returns to welcome and no previous account Mock pet is visible after a different account logs in.

Expected: all seven behaviors match the approved design. Real-device/production verification additionally requires an HTTPS request legal domain.
