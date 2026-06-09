# Plan: Companion Setup Aggregation API

## Problem

Miniprogram `onComplete()` (in `memory-anchor.js`) calls 4 APIs sequentially:
1. `POST /companion/create` - create companion record
2. `POST /agent` - create agent (if not exists)
3. `POST /companion/sync-prompt` - sync prompt to agent
4. `POST /ota/` + `POST /device/bind/{agentId}/{code}` + `POST /ota/` - device binding

These are non-atomic. Any step failure causes data inconsistency (orphaned companion, agent without prompt, etc.).

## Solution

Create a single `POST /companion/setup` aggregation endpoint in the backend that wraps all steps. Steps 1-3 (pure DB) run inside a `@Transactional` boundary. Step 4 (device binding, involves Redis) runs after the transaction commits - if it fails, the core data is still consistent and device binding can be retried.

## Architecture Decision

**Two-phase approach** rather than all-or-nothing:
- **Phase 1** (Transactional): companion create + agent ensure + prompt sync
- **Phase 2** (After commit): device check/register + device bind + WS info retrieval

**Why not all-or-nothing**: Device binding uses Redis for activation codes (`DeviceServiceImpl.checkDeviceActive` generates codes, `deviceActivation` reads them). Redis operations cannot participate in DB transactions. Making Phase 1 transactional gives us the critical guarantee: companion, agent, and prompt are always consistent. Device binding is inherently idempotent and retriable.

## Implementation

### Step 1: Backend - New DTO

**File**: `main/manager-api/src/main/java/xiaozhi/modules/companion/dto/CompanionSetupDTO.java`

Fields (merged from `CompanionCreateDTO` + agent/device info):
- All fields from `CompanionCreateDTO`: `deviceId`, `type`, `avatar`, `defaultImage`, `character`, `occupation`, `voice`, `soulTraits`, `soulQuirk`, `relationType`, `petType`, `petName`, `quirksText`, `pastLifeSecret`
- `agentId` (optional) - if miniprogram already has one, pass it to skip creation

### Step 2: Backend - New VO

**File**: `main/manager-api/src/main/java/xiaozhi/modules/companion/vo/CompanionSetupVO.java`

Fields:
- `CompanionVO companion` - created companion data (avatar, defaultImage, etc.)
- `String agentId` - agent ID (new or existing)
- `boolean deviceBound` - whether device binding succeeded
- `String wsUrl` - WebSocket URL (if device bound)
- `String wsToken` - WebSocket token (if device bound)

### Step 3: Backend - Service Method

**File**: `main/manager-api/src/main/java/xiaozhi/modules/companion/service/CompanionService.java`
- Add: `CompanionSetupVO setup(CompanionSetupDTO dto)`

**File**: `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Add: `setup()` method with `@Transactional(rollbackFor = Exception.class)` for Phase 1
- Inject `AgentService`, `DeviceService`, `SysParamsService`
- Phase 1 (in transaction):
  1. Call existing `create(dto)` logic for companion
  2. If `dto.getAgentId()` is blank, call `agentService.createAgent()` with `agentName = openid`
  3. Call existing `syncPromptToAgent()` logic
- Phase 2 (after transaction commits - use `TransactionSynchronizationManager.registerSynchronization` or simply call after the transactional method returns):
  4. Call `deviceService.checkDeviceActive()` to get activation info
  5. If unbound, call `deviceService.deviceActivation()` to bind
  6. Get WS URL/token from system params

**Note on Phase 2**: The simplest approach is to split into two methods - a `@Transactional` method for Phase 1, and a non-transactional orchestration method that calls Phase 1 then Phase 2. This way Phase 1's transaction commits before Phase 2 starts.

### Step 4: Backend - Controller Endpoint

**File**: `main/manager-api/src/main/java/xiaozhi/modules/companion/controller/CompanionController.java`
- Add: `POST /companion/setup` endpoint

```java
@PostMapping("setup")
@Operation(summary = "Companion setup aggregation API")
public Result<CompanionSetupVO> setup(@RequestBody @Valid CompanionSetupDTO dto) {
    CompanionSetupVO vo = companionService.setup(dto);
    return new Result<CompanionSetupVO>().ok(vo);
}
```

### Step 5: Miniprogram - Update `onComplete()`

**File**: `main/miniprogram/pages/memory-anchor/memory-anchor.js`

Replace the 4-step flow with a single API call:

```javascript
onComplete: async function () {
    // ... existing validation ...

    var body = {
        deviceId: app.globalData.virtualMAC,
        type: 'gf',
        avatar: codes.CHARACTER_AVATARS[charId] || '',
        // ... all companion fields ...
        agentId: app.globalData.agentId || '',  // pass existing agentId if any
    };

    wx.showLoading({ title: '...', mask: true });

    try {
        var res = await request.post('/companion/setup', body);
        if (!res || res.code !== 0) {
            wx.hideLoading();
            wx.showToast({ title: '...', icon: 'none' });
            return;
        }

        // Update globalData from single response
        var data = res.data;
        app.globalData.agentId = data.agentId;
        app.globalData.companionAvatar = data.companion.avatar || null;
        app.globalData.companionBgImage = data.companion.defaultImage || null;
        app.globalData.companionDataLoaded = true;
        app.globalData.needsDestiny = false;

        if (data.deviceBound) {
            app.globalData.wsUrl = data.wsUrl;
            app.globalData.wsToken = data.wsToken;
            app.globalData.isDeviceBound = true;
        }

        wx.setStorageSync('agentId', data.agentId);
        wx.hideLoading();
        this.setData({ showCompletion: true });
        // ... navigation timeout ...
    } catch (err) {
        wx.hideLoading();
        wx.showToast({ title: '...', icon: 'none' });
    }
}
```

### Step 6: Remove Redundant Calls

After verifying the aggregation endpoint works:
- The calls to `app.ensureAgentExists()` and `app.checkDeviceStatus()` in `onComplete()` are removed (they're now server-side)
- These methods remain in `app.js` for other usage (e.g., login flow) - do NOT delete them

## Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Device binding fails after commit | LOW | Phase 2 is idempotent; client retries via existing `checkDeviceStatus()` |
| OTA endpoint returns `ResponseEntity<String>` not `Result<T>` | MEDIUM | Reuse `DeviceService.checkDeviceActive()` directly, not via HTTP |
| Agent already exists (idempotency) | LOW | Check `dto.getAgentId()` - skip creation if provided |
| Companion already exists for device | LOW | Existing `create()` already validates this |
| Shiro auth filter blocks `/companion/setup` | LOW | `/companion/**` is already behind `oauth2` filter - same as existing endpoints |

## Files Changed

| File | Change |
|------|--------|
| `manager-api/.../companion/dto/CompanionSetupDTO.java` | **NEW** - request DTO |
| `manager-api/.../companion/vo/CompanionSetupVO.java` | **NEW** - response VO |
| `manager-api/.../companion/service/CompanionService.java` | **MODIFY** - add `setup()` interface |
| `manager-api/.../companion/service/impl/CompanionServiceImpl.java` | **MODIFY** - implement `setup()` |
| `manager-api/.../companion/controller/CompanionController.java` | **MODIFY** - add `POST /setup` |
| `miniprogram/pages/memory-anchor/memory-anchor.js` | **MODIFY** - replace 4-step with single call |

## Verification

1. Backend compiles: `cd main/manager-api && mvn compile`
2. Test aggregation endpoint manually via Knife4j (`/doc.html`)
3. Test miniprogram flow end-to-end
4. Test failure scenario: companion create fails (e.g., duplicate deviceId) → verify no agent created
5. Test failure scenario: device binding fails → verify companion + agent + prompt are still consistent
