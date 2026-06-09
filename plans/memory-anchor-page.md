# Plan: 记忆锚定 (Memory Anchor) Page

## Requirements Restatement

Add a new "记忆锚定" page to the mini-program wizard flow (after soul-resonance). The page has:

- **Top section**: Video player showing scenario videos
- **Bottom section**: Interactive options that change per scenario

**Scenario 1** — Video: 女友问"我们是什么关系"
- Single-select options: 青梅竹马 / 欢喜冤家 / 一见钟情
- On selection → auto-transition to scenario 2

**Scenario 2** — Video: 女友问"我想领养小动物，猫还是狗"
- Single-select: 猫 / 狗
- Text input: 给小动物起名
- CTA button: "创造完成，唤醒她" (always visible in scenario 2)

**Videos**:
- Scenario 1: `https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/video/baiyueguang_tianmei.mp4`
- Scenario 2: `https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/video/baiyueguang_tianmei2.mp4`

## Data Flow

```
destiny → soul-resonance → memory-anchor (NEW)
                               ↓
                        app.globalData.destinyFlow.relation
                        app.globalData.destinyFlow.petType
                        app.globalData.destinyFlow.petName
```

## Implementation Steps

### Step 1: Register page in app.json

Add `"pages/memory-anchor/memory-anchor"` to the pages array.

### Step 2: Create page files

`pages/memory-anchor/` — 4 files: `.js`, `.wxml`, `.wxss`, `.json`

**memory-anchor.json** — Custom nav bar:
```json
{ "navigationStyle": "custom", "navigationBarTitleText": "记忆锚定" }
```

**memory-anchor.js** — Page data:
- `statusBarHeight`, `scenario` (1 or 2)
- `videoUrl` — current video URL
- `relationOptions` — ["青梅竹马", "欢喜冤家", "一见钟情"]
- `selectedRelation` — null | index
- `petOptions` — ["猫", "狗"]
- `selectedPet` — null | index
- `petName` — string
- Video reference for playback control

Key handlers:
- `onRelationSelect(e)` — set selection, then auto-switch to scenario 2 after brief delay
- `onPetSelect(e)` — toggle pet selection
- `onPetNameInput(e)` — update petName from input
- `onComplete()` — validate (pet selected + name entered), write to `destinyFlow`, navigate to next step (or show completion)

**memory-anchor.wxml** — Layout:
1. Custom nav-bar (title "记忆锚定")
2. Video player (`<video>`) — fixed height, top section
3. Scenario 1 section (shown when `scenario === 1`):
   - Option grid (3-column, single-select)
4. Scenario 2 section (shown when `scenario === 2`):
   - Pet option grid (2-column, single-select)
   - Name input field (text input)
   - CTA button "创造完成，唤醒她"

**memory-anchor.wxss** — Follow "Ethereal Companion" design system:
- Same palette: `#fbf9f8` bg, `#864e5a` accent, `#ffb7c5` highlight
- Glass-morphism option cards
- `softPulse` animation on CTA button
- Video section styling with rounded corners

### Step 3: Update soul-resonance page navigation

In `soul-resonance.js`, change `onNext()` to navigate to `/pages/memory-anchor/memory-anchor` instead of showing TODO toast.

## Design Consistency

- Custom nav-bar matching destiny & soul-resonance pages
- Option cards use same glass-morphism grid pattern
- CTA button uses same pill-shaped gradient + `softPulse` animation
- Video section: 16:9 aspect ratio, rounded corners, centered
- Smooth transition between scenarios (fade or slide)

## Risks

- **LOW**: Video loading on slow networks — should add loading state
- **LOW**: WeChat `<video>` component z-index issues with custom nav-bar — may need `position: fixed` adjustments
