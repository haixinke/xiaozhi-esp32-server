# 实施计划：伴侣选项编码化

## 需求重述

将小程序中以下硬编码中文列表改为 ID 编码体系：
1. **灵魂特征** (soulTraits): 6 项 → 加 id
2. **小任性** (quirks): 8 项 → 加 id
3. **关系类型** (relationType): 3 项 → 加 id
4. **宠物类型** (petType): 2 项 → 加 id

前后端统一使用编码 ID，前端根据 ID 动态显示中文标签。

## 当前状态

| 列表 | 当前前端存储 | 当前后端存储 |
|------|-------------|-------------|
| 灵魂特征 | `['粘人精', '嘴硬心软']` (中文) | `"粘人精,嘴硬心软"` (中文逗号分隔) |
| 小任性 | `'重度起床气'` (中文) | `"重度起床气"` (中文) |
| 关系类型 | `'青梅竹马'` (中文) | `"青梅竹马"` (中文，用于 switch 匹配) |
| 宠物类型 | `'猫'` / `'狗'` (中文) | DB comment 写 `cat/dog` 但实际存中文 |

已有先例：`character` (baiyueguang)、`occupation` (design)、`voice` (wenruo) 已经使用 ID 编码。

## 编码定义

```js
// 灵魂特征
SOUL_TRAITS: [
  { id: 'clingy',      label: '粘人精' },
  { id: 'flirty',      label: '撒娇狂魔' },
  { id: 'toughSoft',   label: '嘴硬心软' },
  { id: 'protective',  label: '护短狂魔' },
  { id: 'straightShooter', label: '直球选手' },
  { id: 'rational',    label: '人间清醒' },
]

// 小任性
QUIRKS: [
  { id: 'grumpyMorning',  label: '重度起床气' },
  { id: 'jealous',        label: '小醋坛子' },
  { id: 'noDirection',    label: '路痴晚期' },
  { id: 'gamerNoob',      label: '游戏黑洞' },
  { id: 'nightOwl',       label: '熬夜修仙党' },
  { id: 'indecisive',     label: '选择困难症' },
  { id: 'chaoticLogic',   label: '逻辑泥石流' },
  { id: 'kitchenDisaster',label: '炸厨房选手' },
]

// 关系类型
RELATION_TYPES: [
  { id: 'childhood',  label: '青梅竹马' },
  { id: 'bickering',  label: '欢喜冤家' },
  { id: 'loveAtFirst',label: '一见钟情' },
]

// 宠物类型
PET_TYPES: [
  { id: 'cat', label: '猫' },
  { id: 'dog', label: '狗' },
]
```

## 实施步骤

### Phase 1: 小程序 — 创建共享编码配置文件

**文件**: `main/miniprogram/config/companion-codes.js`

将上述所有编码定义集中到一个文件中，导出查找函数：
- `getLabelByCode(category, id)` — 根据 ID 获取显示标签
- `getOptions(category)` — 获取某个类别的完整选项列表
- 直接导出 `SOUL_TRAITS`, `QUIRKS`, `RELATION_TYPES`, `PET_TYPES`

### Phase 2: 小程序 — 改造 soul-resonance.js

**文件**: `main/miniprogram/pages/soul-resonance/soul-resonance.js`

- 引入 `companion-codes.js`
- `soulTraits` data 改为 `[{ id: 'clingy', label: '粘人精', selected: false }, ...]`
- `quirks` data 改为 `[{ id: 'grumpyMorning', label: '重度起床气' }, ...]`
- `onNext()` 中 `flow.traits` 存 ID 数组 `['clingy', 'toughSoft']`
- `onNext()` 中 `flow.quirk` 存 ID `'grumpyMorning'`
- WXML 无需改动（已用 `item.label` 显示，`item.selected` 控制）

### Phase 3: 小程序 — 改造 memory-anchor.js

**文件**: `main/miniprogram/pages/memory-anchor/memory-anchor.js`

- 引入 `companion-codes.js`
- `RELATION_OPTIONS` 改为 `[{ id: 'childhood', label: '青梅竹马' }, ...]`
- `PET_OPTIONS` 改为 `[{ id: 'cat', label: '猫' }, ...]`
- `onComplete()` 中 `flow.relation` 存 ID `'childhood'`
- `onComplete()` 中 `flow.petType` 存 ID `'cat'`
- WXML 已用 `{{item}}` 遍历，改为 `{{item.label}}`

### Phase 4: 后端 — 改造 CompanionServiceImpl

**文件**: `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`

- `deriveIntimacy()` 中的 switch 从中文改为 ID 编码：
  - `"childhood"` → 0.7f
  - `"loveAtFirst"` → 0.6f
  - `"bickering"` → 0.5f

### Phase 5: 更新 DTO Schema 描述

**文件**: `main/manager-api/src/main/java/xiaozhi/modules/companion/dto/CompanionCreateDTO.java`

更新 `@Schema` 注解描述，标注使用编码 ID：
- `soulTraits` → "灵魂特质编码,逗号分隔"
- `soulQuirk` → "小任性编码"
- `relationType` → "关系类型编码: childhood/bickering/loveAtFirst"
- `petType` → "宠物类型编码: cat/dog"

## 影响范围

| 文件 | 改动类型 |
|------|---------|
| `main/miniprogram/config/companion-codes.js` | **新建** |
| `main/miniprogram/pages/soul-resonance/soul-resonance.js` | 改 data 结构 + onNext |
| `main/miniprogram/pages/soul-resonance/soul-resonance.wxml` | 可能需小改 |
| `main/miniprogram/pages/memory-anchor/memory-anchor.js` | 改常量 + onComplete |
| `main/miniprogram/pages/memory-anchor/memory-anchor.wxml` | `{{item}}` → `{{item.label}}` |
| `main/manager-api/.../CompanionServiceImpl.java` | deriveIntimacy switch |
| `main/manager-api/.../CompanionCreateDTO.java` | Schema 描述 |

## 风险

- **MEDIUM**: 已有数据库中的中文数据需要迁移（如果有生产数据的话）— 本次不做数据迁移，后续单独处理
- **LOW**: WXML 模板绑定变化 — 需确认 `item.label` 在列表渲染中正确工作
- **LOW**: 编码选择 — 使用英文简短编码，保持与 character/occupation/voice 一致风格

## 复杂度评估: LOW

- 改动文件少，逻辑简单
- 主要是数据结构从字符串到 `{ id, label }` 的映射
- 无架构变化
