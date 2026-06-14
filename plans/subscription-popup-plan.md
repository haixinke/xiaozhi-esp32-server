# Implementation Plan: Subscription Popup (签订契约浮窗)

## Requirements

When a user without an active subscription clicks "我的契约" on the settings page:
1. Call `GET /subscription/plans` (public endpoint, no auth needed)
2. Show a bottom slide-up popup with:
   - Romantic copy from the "girlfriend" at the top
   - Two side-by-side cards displaying subscription plan info (name, price, features)
   - A persistent "签订契约" button at the bottom, only active when a card is selected

## Current State

- `onContractTap` in `settings.js:64-66` is a placeholder showing a toast "即将上线"
- `settings.js` already calls `GET /subscription/entitlements` on load to check subscription status
- `planCode` data field tracks current subscription: `null` | `'silver'` | `'gold'`
- The destiny page has an established bottom drawer pattern (overlay + panel + CSS transition)
- No reusable popup component exists; popups are implemented inline per page

## Backend API

**Endpoint**: `GET /subscription/plans` (public, no auth)
**Response**: `{ code: 0, data: [SubscriptionPlanVO, ...] }`

**SubscriptionPlanVO fields**:
- `planCode`: `"silver"` | `"gold"`
- `planName`: `"白银月卡"` | `"黄金月卡"`
- `priceFen`: original price in fen (1990, 3990)
- `promoPriceFen`: promo price in fen (990, 1990)
- `features`: `["long_term_memory"]` | `["long_term_memory","voice_input","superpower","social_moments"]`
- `bonusItems`: `[{ skuCode: "rose", count: 10 }]`

**Feature code mapping**:
- `long_term_memory` -> 长期记忆
- `voice_input` -> 语音输入
- `superpower` -> 跨维度能力（天气、新闻）
- `social_moments` -> 查看女友的社交动态

## Implementation Steps

### Step 1: Update settings.js - Add data fields

Add to `data`:
```
showContractPopup: false,
plans: [],
selectedPlanId: null,
contractLoading: false,
```

### Step 2: Update settings.js - Implement onContractTap

Replace the placeholder `onContractTap` with:
1. If user already has active subscription (`planCode` is not null), navigate to subscription management page (or show current plan info) -- for now, still show toast since we only implement the purchase flow
2. If no subscription, call `GET /subscription/plans`
3. On success, set `plans` and `showContractPopup: true`

### Step 3: Add popup event handlers in settings.js

- `onContractOverlayTap` - close popup, set `showContractPopup: false`
- `onContractPanelTap` - prevent event bubbling (catchtap)
- `onPlanSelect(e)` - set `selectedPlanId` to the tapped plan's id
- `onSignContract()` - handle "签订契约" button tap (for now, show toast "支付功能开发中", later will integrate payment)

### Step 4: Add helper methods in settings.js

- `formatPrice(priceFen)` - convert fen to yuan string (e.g., 990 -> "9.90")
- `getFeatureLabel(code)` - map feature code to Chinese label
- `getFeatureIcon(code)` - map feature code to icon (emoji or unicode)

### Step 5: Add popup WXML in settings.wxml

Structure following the destiny page's bottom drawer pattern:
```xml
<!-- Contract Popup -->
<view class="contract-overlay {{showContractPopup ? 'contract-overlay-show' : ''}}" bindtap="onContractOverlayTap">
  <view class="contract-panel" catchtap="onContractPanelTap">
    <!-- Romantic copy -->
    <view class="contract-header">
      <text class="contract-romantic-text">你愿意选一个契约，让我离你的现实生活更近一点吗？</text>
    </view>
    <!-- Two plan cards -->
    <view class="contract-cards">
      <view class="contract-card {{selectedPlanId === plan.id ? 'contract-card-selected' : ''}}"
            wx:for="{{plans}}" wx:key="id"
            data-id="{{plan.id}}" bindtap="onPlanSelect">
        <text class="contract-card-name">{{plan.planName}}</text>
        <view class="contract-card-price">
          <text class="contract-card-amount">¥{{plan.promoPriceFen / 100}}</text>
          <text class="contract-card-original">¥{{plan.priceFen / 100}}</text>
        </view>
        <view class="contract-card-features">
          <view class="contract-feature" wx:for="{{plan.features}}" wx:for-item="feature" wx:key="*this">
            <text>{{feature}}</text>
          </view>
        </view>
      </view>
    </view>
    <!-- Sign button -->
    <view class="contract-action">
      <view class="contract-btn {{selectedPlanId ? 'contract-btn-active' : 'contract-btn-disabled'}}" bindtap="onSignContract">
        签订契约
      </view>
    </view>
  </view>
</view>
```

### Step 6: Add popup WXSS in settings.wxss

Style the popup following the existing glassmorphism design system:
- Overlay: `position: fixed`, `rgba(0,0,0,0.4)` background
- Panel: `position: absolute; bottom: 0`, slide up via `translateY`, `border-radius: 32rpx 32rpx 0 0`
- Cards: glass effect with `backdrop-filter: blur(40rpx)`, side-by-side flex layout
- Selected card: accent border/shadow
- Button: disabled state (gray), active state (gradient pink/purple)
- Romantic text: centered, italic or script-like styling
- Feature items: small pills/tags with icons
- Safe area padding at bottom
- Full dark mode support

### Step 7: Format price display

Use WXS or inline division to display prices from fen to yuan. Since WXML can't do arithmetic, use WXS module:
- Create inline WXS in WXML or add `price.wxs` helper
- Or format in JS before setting data (simpler, preferred approach)

### Step 8: Guard condition

Only show popup when user has NO active subscription. If `planCode` is already set, the button could show current plan details instead (but for this PR, just show toast "您已有契约").

## Files to Modify

| File | Change |
|------|--------|
| `main/miniprogram/pages/settings/settings.js` | Data fields, event handlers, API call |
| `main/miniprogram/pages/settings/settings.wxml` | Popup markup |
| `main/miniprogram/pages/settings/settings.wxss` | Popup styles |

## Design Decisions

1. **Inline popup vs component**: Following existing codebase convention, implement inline in settings page (no new component)
2. **Price formatting**: Format in JS before `setData` to avoid WXS complexity
3. **Feature labels**: Map feature codes to Chinese labels in JS, not hardcoded in WXML
4. **Payment integration**: "签订契约" button will show a placeholder toast for now; payment flow is a separate task

## Risks

- **LOW**: API might return more than 2 plans - the UI should handle gracefully (scroll or show first 2)
- **LOW**: Price display precision - must handle fen-to-yuan conversion correctly
- **NONE**: The endpoint is public (no auth), so no token issues
