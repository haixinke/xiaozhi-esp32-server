# Blueprint: Miniprogram "Destiny" Page (Agent Creation)

**Objective**: Add a "Destiny" (命运初见) page to the miniprogram. It shows when a new user has no agent. The user selects a character portrait, occupation, voice, and quirks, then creates their personalized AI companion agent.

**Reference Design**: `main/miniprogram/first.html` — glass-morphism card UI with portrait carousel, 3x3 occupation grid, vertical voice picker, quirks textarea, and CTA button.

**Trigger Logic**: When `silentLogin()` returns `agentId === null` (user has no agent), redirect to this page instead of auto-creating an agent with `ensureAgentExists()`.

**Scope**: Frontend only. No backend changes — uses existing `POST /agent` (create) + `PUT /agent/{id}` (update with systemPrompt, ttsVoiceId, etc.).

---

## Architecture Decision

### Agent Creation Strategy

The backend `AgentCreateDTO` only has `agentName`. But `AgentUpdateDTO` supports `systemPrompt`, `ttsVoiceId`, and all other fields. So:

1. `POST /agent` with `{ agentName }` — creates agent with defaults from template
2. `PUT /agent/{id}` with `{ systemPrompt, ttsVoiceId }` — applies user customizations

No backend changes needed.

### Navigation Flow

```
app.js onLaunch
  → silentLogin()
  → if agentId exists:
      → checkDeviceStatus() → createPetIfNeeded() → index page (normal)
  → if agentId is null:
      → set globalData.needsDestiny = true
      → checkDeviceStatus() → createPetIfNeeded() (these don't need agentId for OTA registration)

index page onLoad
  → _waitForAppReady()
  → detects needsDestiny === true
  → wx.redirectTo('/pages/destiny/destiny')

destiny page
  → user selects character, occupation, voice, quirks
  → POST /agent (create) → PUT /agent/{id} (update)
  → set globalData.agentId
  → wx.switchTab('/pages/index/index')

index page (second load, now has agentId)
  → _waitForAppReady() succeeds
  → user can chat
```

Key: `wx.redirectTo` replaces the index page on the stack, preventing back navigation. After destiny completes, `wx.switchTab` returns to the index tab.

### Character Presets

Hardcoded in `destiny.js` as a data array. Each preset has:
- `name`: display name (e.g. "高冷白月光")
- `image`: URL or local path
- `defaultPrompt`: base system prompt for this character type

### Occupation-to-Prompt Mapping

Each occupation maps to a prompt segment that gets merged into the system prompt:

```
character.defaultPrompt + "\n\n职业设定：你是{occupation}。" + quirks
```

### Voice Mapping

Voice labels map to `ttsVoiceId` values. For MVP, use the default template voice (don't change ttsVoiceId). The picker is UI-only until we have a voice catalog API.

---

## Steps

### Step 1: Create Destiny Page Files and Register in app.json

**Context**: The miniprogram has 2 pages (index, settings) in `app.json`. We add a third non-tab-bar page.

**Tasks**:
1. Create directory `main/miniprogram/pages/destiny/`
2. Create `destiny.json` — page config with `"navigationStyle": "custom"` and title "命运初见"
3. Create `destiny.wxml` — empty placeholder
4. Create `destiny.wxss` — empty placeholder
5. Create `destiny.js` — basic Page() with data and lifecycle hooks
6. Edit `main/miniprogram/app.json` — add `"pages/destiny/destiny"` to pages array (do NOT add to tabBar)

**Files**:
- NEW: `main/miniprogram/pages/destiny/destiny.js`
- NEW: `main/miniprogram/pages/destiny/destiny.wxml`
- NEW: `main/miniprogram/pages/destiny/destiny.wxss`
- NEW: `main/miniprogram/pages/destiny/destiny.json`
- EDIT: `main/miniprogram/app.json`

**Verification**:
- WeChat DevTools compiles without errors
- `pages/destiny/destiny` appears in the page list
- Navigating to this page shows a blank page with custom nav bar

**Exit Criteria**: Page is registered and loads without errors.

---

### Step 2: Restructure app.js Init Flow

**Context**: Currently `initInBackground()` calls `silentLogin() → ensureAgentExists() → checkDeviceStatus() → createPetIfNeeded()` sequentially. When the user has no agent, `ensureAgentExists()` auto-creates one with `agentName = openid`. We need to skip this and set a flag instead.

**Tasks**:
1. In `initInBackground()`, after `silentLogin()` resolves:
   - Check if `this.globalData.agentId` is null
   - If null: set `this.globalData.needsDestiny = true`, skip `ensureAgentExists()`, still call `checkDeviceStatus()` and `createPetIfNeeded()` (device registration works without agentId; only device-to-agent binding needs it, and that will be handled after destiny page creates the agent)
   - If not null: proceed as before (ensureAgentExists, checkDeviceStatus, createPetIfNeeded)
2. Remove or keep `ensureAgentExists()` method — keep it for now as dead code, it may be useful for fallback
3. In Branch A (returning user with cached token), also check `agentId`:
   - If cached agentId exists: proceed as before
   - If no cached agentId: set `needsDestiny = true`, skip `ensureAgentExists()`

**Files**:
- EDIT: `main/miniprogram/app.js` (initInBackground method, lines 22-57)

**Verification**:
- Fresh user (no agent): `globalData.needsDestiny === true`, no auto agent creation
- Returning user (has agent): normal flow, no change
- Console logs confirm the correct branch

**Exit Criteria**: `needsDestiny` flag is set correctly for new vs returning users.

---

### Step 3: Implement Destiny Page UI (WXML + WXSS)

**Context**: Translate the HTML/CSS design from `first.html` into WXML/WXSS. The design uses glass-morphism (backdrop-blur), a pink/rose color scheme (#864e5a primary), Plus Jakarta Sans font, and Material Symbols icons.

**Key Differences from first.html**:
- WeChat miniprogram doesn't support external fonts via CSS `@import` easily — use system font stack or `wx.loadFontFace()`
- Material Symbols icons aren't available — use Unicode emojis or image icons, or the miniprogram's built-in icon component
- No `<img>` tag — use `<image>` in WXML
- No `<textarea>` — use `<textarea>` (WX component, similar API)
- No `<button>` — use `<button>` or `<view>` with `bindtap`
- No `position: fixed` bottom bar needed — this page is standalone, no tab bar overlay
- Swipe navigation for character cards — use `<swiper>` component

**Tasks**:
1. **destiny.wxml** — Build the page structure:
   - Custom navigation bar (title "命运初见", transparent bg)
   - `<swiper>` for character portrait cards (aspect 4:5 images, name overlay, dot indicators)
   - Section header + 3x3 grid of occupation buttons (use `<view>` with `bindtap`, not `<button>`)
   - Section header + scroll picker for voice (use `<picker-view>` or custom scroll implementation)
   - Section header + `<textarea>` for quirks (with character counter)
   - Fixed bottom CTA button "就是她了，注入灵魂"
2. **destiny.wxss** — Style translation:
   - Define CSS custom properties for the color scheme (match DESIGN.md if exists, or use first.html values)
   - `.glass-card` — `background: rgba(255,255,255,0.6); backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.4)`
   - `.primary-glow` — `box-shadow: 0 0 20px rgba(255,183,197,0.4)`
   - `.active-selection` — selected occupation state
   - Voice picker styles with gradient mask
   - CTA button with gradient and pulse animation
   - Responsive padding using `margin-mobile` equivalent

**Design Tokens** (from first.html / DESIGN.md):
```
primary: #864e5a
primary-container: #ffb7c5
surface: #fbf9f8
surface-container: #f0eded
background: #fbf9f8
```

**Files**:
- NEW content: `main/miniprogram/pages/destiny/destiny.wxml`
- NEW content: `main/miniprogram/pages/destiny/destiny.wxss`

**Verification**:
- Page renders with all sections visible
- Glass card effect works on device
- Swiper cycles through character portraits
- Occupation grid shows 9 options, tap highlights selection
- Voice picker scrolls and highlights center item
- CTA button visible at bottom with pulse animation

**Exit Criteria**: UI matches first.html design within WeChat miniprogram constraints. All interactive elements respond to touch.

---

### Step 4: Implement Destiny Page Logic (JS)

**Context**: The JS handles user selections, constructs the agent configuration, and calls the API to create + update the agent.

**Tasks**:
1. **Page data**:
   ```javascript
   data: {
     characters: [...],  // preset array: { name, image, basePrompt }
     currentCharIdx: 0,
     occupations: [...],  // { label, icon, prompt }
     selectedOccupation: null,
     voices: ['邻家', '可爱', '调皮'],
     selectedVoice: '可爱',
     quirks: '',
     submitting: false
   }
   ```

2. **Character presets** — hardcode 3-5 presets in JS:
   - Each has: `name`, `image` (local or URL), `basePrompt`
   - Example: `{ name: '高冷白月光', image: '/images/char-moonlight.png', basePrompt: '你是一个外表高冷、内心温柔的女生...' }`

3. **Occupation presets** — hardcode the 9 occupations from first.html:
   - `{ label: '大厂设计师', prompt: '你是一名大厂设计师，对美学有极致追求...' }`
   - etc.

4. **Event handlers**:
   - `onCharChange(e)` — swiper change event, update `currentCharIdx`
   - `onOccupationTap(e)` — update `selectedOccupation` index, toggle active class
   - `onVoiceChange(e)` — update `selectedVoice` from picker
   - `onQuirksInput(e)` — update `quirks` text and character count
   - `onSubmit()` — the main creation flow

5. **`onSubmit()` flow**:
   ```
   a. Validate: occupation must be selected
   b. Set submitting = true (disable button, show loading)
   c. Construct agentName = characters[currentCharIdx].name
   d. POST /agent { agentName } → get agentId
   e. Construct systemPrompt:
      character.basePrompt + "\n\n" + occupation.prompt + quirks
   f. PUT /agent/{agentId} { systemPrompt } → apply customization
   g. Set app.globalData.agentId = agentId
   g. Set app.globalData.agentName = agentName
   h. Clear app.globalData.needsDestiny
   i. wx.switchTab({ url: '/pages/index/index' })
   ```

6. **Error handling**: Show toast on API failure, re-enable button

**Files**:
- EDIT: `main/miniprogram/pages/destiny/destiny.js` (fill in the full implementation)

**Verification**:
- Selecting occupation highlights it, selecting another deselects previous
- Voice picker updates selection
- Quirks counter shows correct character count
- Submit creates agent (check database for new agent with correct name)
- After submit, navigates to index page with agentId set

**Exit Criteria**: Full flow works — user selects options, submits, agent is created with correct customization, navigates to chat page.

---

### Step 5: Wire Index Page to Redirect to Destiny

**Context**: The index page's `_waitForAppReady()` polls globalData. We need to detect the `needsDestiny` flag and redirect before the normal readiness check.

**Tasks**:
1. In `pages/index/index.js`, in `_waitForAppReady()`:
   - After getting `app.globalData`, check `g.needsDestiny === true`
   - If true: reject the promise, call `wx.redirectTo({ url: '/pages/destiny/destiny' })`
   - This should happen before the existing ready/fail condition checks

2. In `_bootstrap()`, when the page loads again after destiny completion:
   - The existing logic already handles agentId in globalData
   - `agentName` display will use the new agent name from destiny page
   - Remove the hardcoded "翠花" fallback (or keep it as safety net)

**Files**:
- EDIT: `main/miniprogram/pages/index/index.js` (_waitForAppReady method)

**Verification**:
- New user opens app → redirected to destiny page (not index)
- After completing destiny → returns to index page
- Returning user (has agent) → goes directly to index, no redirect

**Exit Criteria**: Navigation flow works correctly for both new and returning users.

---

### Step 6: End-to-End Verification

**Context**: Full integration test of the new user flow.

**Tasks**:
1. Use `/mini-wechat-cleanup` skill to clean a test openid's data
2. Open miniprogram with the test user
3. Verify: redirected to destiny page
4. Select a character, occupation, voice
5. Enter quirks text
6. Tap "就是她了，注入灵魂"
7. Verify: agent created in database with correct name and systemPrompt
8. Verify: navigated to index page
9. Verify: can tap "召唤" and start chatting
10. Close and reopen miniprogram
11. Verify: returning user goes directly to index (no destiny page)

**Verification**: All 11 checks pass.

**Exit Criteria**: Complete new-user and returning-user flows work without errors.

---

## Dependency Graph

```
Step 1 (page files + app.json)
  ├─→ Step 3 (UI implementation)
  │     └─→ Step 4 (JS logic)
  └─→ Step 2 (app.js restructure)
        └─→ Step 5 (index page redirect)
              └─→ Step 6 (E2E verification)
```

Steps 2 and 3 can run in parallel after Step 1. Steps 4 and 5 can run in parallel after their respective predecessors. Step 6 is the final integration gate.

## Parallelism

- **Wave 1**: Step 1 (sequential — creates page skeleton)
- **Wave 2**: Step 2 + Step 3 (parallel — app.js restructure + UI implementation)
- **Wave 3**: Step 4 + Step 5 (parallel — JS logic + index redirect, after Wave 2)
- **Wave 4**: Step 6 (sequential — E2E verification)

## Rollback

Each step is self-contained. To rollback:
- Steps 1-4: Delete `pages/destiny/` directory and revert `app.json`
- Step 2: Revert `app.js` to call `ensureAgentExists()` unconditionally
- Step 5: Revert `index.js` _waitForAppReady changes
