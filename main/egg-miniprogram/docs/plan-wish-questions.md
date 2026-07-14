# 蛋宝宝小程序「今日许愿」改造实施计划

## 一、需求重述

1. **题目来源**：`docs/蛋宝宝小程序PRD.md` 5.2.1 许愿池题目，共 7 道单选题，每道题目的选项都带 emoji。
2. **出题规则**：用户每天进入许愿页面时，从 7 题中按顺序展示**第一道今天还没答过的题**。
3. **去重规则**：某道题当天被答过后，当天不再出现；7 题全部答完后，页面显示“今天已经许过愿啦”并返回。
4. **提交逻辑**：选择后正常提交 `WISH` 类型的 `hatch-action`，每日减时 60 分钟，保持幂等。
5. **数据保存**：用户的每一次选择都要保存，但**现阶段不影响任何结果**（不影响性格、MBTI、收藏卡等）。

## 二、数据模型设计

### 2.1 题目与选项定义（前端本地静态配置）

题目与选项属于产品文案，不常变，**放在前端本地静态配置**，不建后端表。

建议新增文件：`main/egg-miniprogram/miniprogram/config/wish-questions.js`

```js
module.exports = [
  {
    id: 'world-color',
    title: '你希望蛋宝宝破壳后，第一眼看到的"世界"是什么颜色？',
    options: [
      { emoji: '🌿', text: '森林绿' },
      { emoji: '🌊', text: '海洋蓝' },
      { emoji: '🌸', text: '樱花粉' },
      { emoji: '🌅', text: '夕阳橙' },
      { emoji: '🌌', text: '星空紫' },
    ],
  },
  // ... 其余 6 题
];
```

- 用 `id` 唯一标识题目，便于匹配已答记录。
- 选项用 `{ emoji, text }` 结构，方便前端分别渲染样式，也便于后端存储纯文本。

### 2.2 已答记录

**后端为唯一事实源**，前端仅做本地缓存辅助。

后端：`ai_pet_hatch_action` 表中，每条 `action_type = 'WISH'` 的记录代表一次许愿：

- `action_date`：答题日期（Asia/Shanghai），用于判断今天是否已答。
- `payload`：JSON 字符串，建议格式 `{ questionId: 'world-color', value: '森林绿' }`。

前端：进入页面时通过 `GET /pet/{id}/hatch-actions` 拉取 WISH 记录，筛选出今日的 questionId 集合，从而决定展示哪道题。

前端本地 `pet.preferences.wishes` 可以继续追加保存 `{ date, questionId, value }`，与现有逻辑兼容。

## 三、涉及文件清单

### 前端（蛋宝宝小程序）

| 文件 | 操作 | 说明 |
| --- | --- | --- |
| `miniprogram/config/wish-questions.js` | 新增 | 7 道题目与选项静态配置 |
| `miniprogram/pages/wish/wish.js` | 修改 | 根据已答记录动态出题、提交时带 questionId |
| `miniprogram/pages/wish/wish.wxml` | 修改 | 展示当前题目与 emoji 选项 |
| `miniprogram/pages/wish/wish.wxss` | 修改（可选） | emoji 与文本间距、选中态样式微调 |
| `miniprogram/utils/pet-store.js` | 修改 | `completeWish` 支持接收 `{ questionId, value }` 并记录 |
| `miniprogram/pages/hatch-guide/hatch-guide.js` | 修改（可选） | 若 7 题全答完，今日许愿任务仍应显示为 done |

### 后端（manager-api）

| 文件 | 操作 | 说明 |
| --- | --- | --- |
| `src/main/java/xiaozhi/modules/pet/service/impl/HatchActionServiceImpl.java` | 修改（可选） | 若需要校验/读取 `questionId`，可扩展；当前 `payload` 透传，可不改 |
| 无需新增数据库 changeset | — | 复用 `ai_pet_hatch_action` 表和现有 WISH 类型 |

> 后端核心能力已具备：`POST /pet/{id}/hatch-action` 可接收任意 `payload`，并按 `(pet_id, action_type, action_date)` 幂等。本次改造以**前端出题逻辑**为主，后端大概率无需改动。

## 四、分阶段实施计划

### Phase 1：定义题目配置（约 20 分钟）

**目标**：把 PRD 里的 7 道题整理成前端可消费的静态配置。

步骤：

1. 新建 `miniprogram/config/wish-questions.js`。
2. 将 7 道题的 `id`、`title`、`options`（含 `emoji`、`text`）写入。
3. 在 `wish.js` 中引入该配置并简单打印验证。

验证点：

- `node --check miniprogram/config/wish-questions.js` 通过。
- 题目顺序与 PRD 一致，emoji 无乱码。

### Phase 2：改造许愿页面出题逻辑（约 60 分钟）

**目标**：页面加载时，根据今日已答记录算出要展示的题目。

步骤：

1. `wish.js` 的 `onLoad/onShow` 中：
   - 读取当前 pet。
   - 调用 `petApi.listHatchActions(pet.id)` 获取全部 hatch-actions。
   - 过滤出 `actionType === 'WISH'` 且 `actionDate === 今天` 的记录。
   - 从每条记录的 `payload` 中解析出 `questionId`。
   - 遍历 7 道题，找到第一个不在已答集合中的题；若全部答完，进入“已完成”状态。
2. 在 data 中新增字段：`question`（当前题目对象）、`answeredToday`（已答题 questionId 集合）、`allDone`（布尔值）。
3. 渲染当前题目标题与选项。

验证点：

- 首次进入展示第 1 题。
- 答完第 1 题后再次进入展示第 2 题。
- 7 题全答完后进入 allDone 状态。

### Phase 3：改造提交逻辑与本地记录（约 40 分钟）

**目标**：提交时带上 `questionId` 和选项值，并正确记录到本地。

步骤：

1. `wish.js` 中 `selected` 改为保存选项文本（或 `{ emoji, text }`）。
2. `onSubmit` 时构造 payload：`{ questionId: question.id, value: selectedText }`。
3. 调用 `petStore.completeWish(payload)`。
4. `pet-store.js` 中 `completeWish` 兼容字符串和对象入参：
   - 字符串：兼容旧逻辑，默认 `questionId: 'legacy'`。
   - 对象：使用传入的 `questionId` 和 `value`。
5. `completeDailyTask` 中 demo 模式和非 demo 模式都记录 `{ date, questionId, value }` 到 `pet.preferences.wishes`。

验证点：

- 提交后后端 `payload` JSON 包含 `questionId` 和 `value`。
- `pet.preferences.wishes` 正确追加。
- 重复提交同一题（当天）返回 `alreadyDone=true`，不重复减时。

### Phase 4：全部完成状态与样式调整（约 40 分钟）

**目标**：7 题全答完后给出友好提示并返回。

步骤：

1. `wish.wxml` 新增 `allDone` 分支：
   - 显示“今天已经许过愿啦”。
   - 显示一个返回按钮，点击 `wx.navigateBack()`。
2. 调整选项渲染，使 emoji 和文字自然排列（emoji 在前，文字在后）。
3. 选项被选中后保持现有 radio 选中态。
4. 标题文字随题目动态变化。

验证点：

- 7 题全答完时直接显示完成提示，不出现选项。
- 样式无错位，emoji 正常显示。

### Phase 5：修炼手册状态同步（约 20 分钟）

**目标**：`hatch-guide` 中的“今日许愿”任务完成态正确。

步骤：

1. `hatch-guide.js` 已拉取 `hatch-actions` 并缓存到 `pet._hatchActions`。
2. `getHatchActionState` 已按当日 `WISH` 记录判断 `wishDone`，无需改动。
3. 若 7 题全答完，`wishDone` 仍为 true，符合预期。

验证点：

- 今日答过任意一题许愿后，修炼手册中许愿任务显示已完成。

### Phase 6：测试与校验（约 40 分钟）

**目标**：覆盖 demo 模式、后端模式、边界情况。

测试用例：

1. demo 模式：领养后连续 7 天每天答一题，验证题目顺序、本地存储、allDone 状态。
2. 后端模式：验证 `hatch-action` payload 包含 `questionId`，`listHatchActions` 返回后可正确去重。
3. 边界：7 题全答完后当天再进入，提示已完成；次日重新从第 1 题开始。
4. 兼容：旧缓存中 `pet.preferences.wishes` 只有 `value` 没有 `questionId` 时不报错。

工程校验：

```bash
node main/egg-miniprogram/scripts/verify-project.js
find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
```

## 五、关键设计决策

| 决策 | 方案 | 理由 |
| --- | --- | --- |
| 题目配置放在前端还是后端？ | **前端静态配置** | 题目是产品文案，不常变；后端无需建表和接口，减少改动。 |
| 已答记录以哪里为准？ | **后端 `ai_pet_hatch_action` 为唯一事实源** | 换设备、清缓存后仍能正确恢复进度；前端本地缓存仅用于展示优化。 |
| 题目顺序规则？ | **固定顺序，取第一个未答的题** | 与 PRD “每日更新题目、最多 7 道题、每日刷新”语义一致；实现简单，用户预期明确。 |
| 7 题全答完当天怎么办？ | **显示已完成提示并返回** | 每日动作每日一次的本质不变，7 题答完即今日许愿次数用尽。 |
| 次日如何重置？ | **按 `action_date` 过滤，自然按新日期重新开始** | 无需额外重置逻辑。 |
| payload 结构？ | `{ questionId, value }` | 保留题目标识和选项文本，便于后续分析；不影响现有后端。 |
| emoji 怎么处理？ | 前端展示用 emoji，后端只存文本 | 减少后端存储和传输复杂度，emoji 作为纯展示层。 |

## 六、复杂度评估与风险

**复杂度：中等（ primarily 前端改造）**

| 风险 | 级别 | 应对措施 |
| --- | --- | --- |
| 后端 `payload` 字段长度/编码问题 | 低 | `payload` 为 TEXT，JSON 很小，emoji 不存后端。 |
| 旧缓存只有 `value` 没有 `questionId` | 低 | 新逻辑对旧记录做兼容处理，不依赖 `questionId`。 |
| 用户跨天进入时题目未重置 | 低 | 以 `action_date` 和 `todayKey()` 判断，日期切换自动生效。 |
| demo 模式与后端模式行为不一致 | 中 | 统一在 `completeDailyTask` 中处理，双分支都记录 `questionId`。 |
| 7 题答完后的 UI 文案需产品确认 | 低 | 先按“今天已经许过愿啦”实现，用户可后续调整。 |

## 七、预估工作量

- Phase 1：20 分钟
- Phase 2：60 分钟
- Phase 3：40 分钟
- Phase 4：40 分钟
- Phase 5：20 分钟
- Phase 6：40 分钟

**总计约 3.5～4 小时**
