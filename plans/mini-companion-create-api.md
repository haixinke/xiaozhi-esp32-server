# Implementation Plan: Memory-Anchor Page Calls Backend Create Companion API

## Requirements

When the user clicks the "创造完成" button on the memory-anchor page, call the backend `POST /companion/create` API. Companion type defaults to `gf`, all other fields come from user selections accumulated in `destinyFlow`.

## Current State

- **destiny.js** stores in `destinyFlow`: `charId`, `occId`, `voiceId`, `quirksText`
- **soul-resonance.js** adds: `traits` (array), `quirk` (string)
- **memory-anchor.js** adds: `relation`, `petType`, `petName`
- Current `onComplete()` only saves data and navigates to index - **no API call**
- **Missing**: `avatar`/`defaultImage` not stored in destinyFlow (character image URL is in CHARACTERS array only)

## Field Mapping (destinyFlow -> CompanionCreateDTO)

| DTO Field | Source | Required |
|-----------|--------|----------|
| `deviceId` | `app.globalData.virtualMAC` (openid) | Yes |
| `type` | Hardcoded `'gf'` | Yes |
| `avatar` | `CHARACTERS[by charId].image` | Yes |
| `defaultImage` | Same as `avatar` | Yes |
| `character` | `flow.charId` | Yes |
| `occupation` | `flow.occId` | Yes |
| `voice` | `flow.voiceId` | Yes |
| `soulTraits` | `flow.traits.join(',')` | Yes |
| `soulQuirk` | `flow.quirk` | Yes |
| `relationType` | `flow.relation` | Yes |
| `petType` | `flow.petType` | Yes |
| `petName` | `flow.petName` | Yes |
| `quirksText` | `flow.quirksText` | Optional |

## Implementation Steps

### Step 1: Add CHARACTER_AVATARS to companion-codes.js (DONE)

Avatar URLs are stored as a dictionary keyed by character ID in `config/companion-codes.js`, avoiding any changes to `destiny.js`.

### Step 2: Rewrite onComplete() in memory-anchor.js

**File**: `main/miniprogram/pages/memory-anchor/memory-anchor.js`

Replace the current `onComplete()` with:
1. Build the request body from `destinyFlow`
2. Call `POST /companion/create` via `request.post()`
3. On success: call `app.ensureAgentExists()` -> `app.checkDeviceStatus()` -> clear `needsDestiny` -> navigate to index
4. On failure: show error toast, keep user on page

```js
var request = require('../../utils/request');

// ... inside onComplete():

// Build request body
var app = getApp();
var flow = app.globalData.destinyFlow || {};

var body = {
  deviceId: app.globalData.virtualMAC,
  type: 'gf',
  avatar: codes.CHARACTER_AVATARS[flow.charId] || '',
  defaultImage: codes.CHARACTER_AVATARS[flow.charId] || '',
  character: flow.charId,
  occupation: flow.occId,
  voice: flow.voiceId,
  soulTraits: (flow.traits || []).join(','),
  soulQuirk: flow.quirk || '',
  relationType: codes.RELATION_TYPES[this.data.selectedRelation].id,
  petType: codes.PET_TYPES[this.data.selectedPet].id,
  petName: this.data.petName.trim(),
};
if (flow.quirksText) {
  body.quirksText = flow.quirksText;
}

// Show loading
wx.showLoading({ title: '正在创造...', mask: true });

try {
  await request.post('/companion/create', body);

  // Success chain: create agent -> bind device -> navigate
  await app.ensureAgentExists();
  await app.checkDeviceStatus();
  app.globalData.needsDestiny = false;
  wx.hideLoading();
  wx.reLaunch({ url: '/pages/index/index' });
} catch (err) {
  wx.hideLoading();
  wx.showToast({ title: '创建失败，请重试', icon: 'none' });
}
```

### Step 3: Convert onComplete to async function

The `onComplete` method needs to become `async` to use `await`. In WeChat miniprogram, this is supported in modern base libraries.

## Files to Modify

1. **`main/miniprogram/config/companion-codes.js`** - Add `CHARACTER_AVATARS` dictionary (DONE)
2. **`main/miniprogram/pages/memory-anchor/memory-anchor.js`** - Rewrite `onComplete()` to call API (~30 lines)

## Risks

- **MEDIUM**: The sequential chain (create companion -> create agent -> bind device) could fail at any step. Each step has its own error handling in `app.js`. If agent creation fails, it shows a modal and clears login state.
- **LOW**: `request.js` uses `BASE_URL = 'http://127.0.0.1:8002/xiaozhi'` - hardcoded localhost. This is fine for development but will need updating for production.
- **LOW**: `virtualMAC` (openid) might not be set if login hasn't completed. The destiny flow only starts after login, so this should be safe.

## Complexity: LOW

2 files, ~35 lines of changes total.
