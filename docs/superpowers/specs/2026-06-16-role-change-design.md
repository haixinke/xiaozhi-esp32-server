# 角色变更（role_change）设计文档

- 日期：2026-06-16
- 分支：f-mini-fist
- 涉及子项目：manager-api（Java 后端）、miniprogram（微信小程序）、xiaozhi-server（Python，零改动）

## 1. 背景与目标

在道具 SKU 表 `ai_item_sku` 新增「角色变更」道具，允许用户付费变更女友的角色（人设原型）。
四个角色：

| 编码 | 标签 |
|---|---|
| `baiyueguang` | 高冷白月光 |
| `linjiamei` | 元气邻家妹 |
| `zhixingyujie` | 知性御姐 |
| `erciyuan` | 潮酷二次元 |

小程序端参考「身份变更」（实为职业变更 `occupation_change`）的逻辑新增模块；角色变更后同步调整 agent 提示词内容。

## 2. 核心洞察

四个「角色」即 `ai_companion` 表上已存在的 `character` 字段——`CompanionLabels.CHARACTER` 已精确映射上述四值。提示词模板 `SYSTEM_PROMPT_TEMPLATE` 已含 `{{character}}` 占位符（标注「你的外貌角色」）。

因此本特性不是新增维度，而是把已有的 `character` 字段接入现成的「扣券 + 同步提示词」机制，完全平行于 `occupation`（职业变更）。

顺带修复两个潜在缺口：
1. 当前改 `character` 免费（`CompanionServiceImpl.update()` 162-165 行直接改库不扣券）。
2. 只改 `character` 不会同步 agent 提示词——agent 同步（199 行）仅在 `consumeSkus` 非空时触发，而 `ReshapeVoucherRule` 目前不认识 `character`。

## 3. 已确认决策

| 决策项 | 选择 |
|---|---|
| 道具定价 | ¥99（`price_fen = 9900`），同小任性/声音变更券 |
| 头像联动 | 变更角色时自动切换为新角色的头像 + 背景图 |
| 提示词深度 | 最小化——仅依赖现有 `{{character}}` 标签替换，不新增人设描写 |

## 4. 方案选型

- **方案 A（采纳）**：把 `character` 接入 `ReshapeVoucherRule`（扣券决策）+ `CompanionServiceImpl.update()`（门控）+ 新增 SKU + 小程序新模块；头像由小程序随请求带上。最小、最一致，顺带修复同步缺口。
- 方案 B（否决）：新建独立 `role` 列/占位符——与已存在的 `character` 重复，四值本就是 character。
- 方案 C（否决）：不扣券只加 UI——产品要求做成「道具」，必须走扣券。

## 5. 分层设计

### 5.1 数据层（新增 SKU，无 schema 变更）

新 Liquibase 变更集 `db/changelog/202606161000.sql`，复用 `consumable_change` 类别：

```sql
INSERT INTO ai_item_sku (sku_code, sku_name, category, price_fen, attributes, description, sort)
VALUES ('role_change', '角色变更', 'consumable_change', 9900, NULL, '一次性变更女友角色', 14);
```

`sort=14`，接在 `voice_change`（13）之后；并在 `db.changelog-master.yaml` 末尾追加对应 changeSet。

### 5.2 后端逻辑（manager-api / Java）

- `item/enums/ConsumeBizType.java`：加 `public static final String ROLE_CHANGE = "role_change";`
- `companion/util/ReshapeVoucherRule.java`：
  - 加 `ROLE_CHANGE` 常量；
  - `After` record 增加 `character` 字段，`after(...)` 工厂加 character 入参；
  - `decide()` 增 `if (changed(after.character, before.getCharacter())) skus.add(ROLE_CHANGE);`
- `companion/service/impl/CompanionServiceImpl.update()`：
  - `after(...)` 调用补传 `dto.getCharacter()`；
  - bizType switch 增 `case ReshapeVoucherRule.ROLE_CHANGE -> ConsumeBizType.ROLE_CHANGE;`
  - 把 162-165 行的免费 `character` 修改门控为扣券后执行，镜像 `occupationChanged`：
    ```java
    boolean characterChanged = consumeSkus.contains(ReshapeVoucherRule.ROLE_CHANGE);
    if (characterChanged) { entity.setCharacter(dto.getCharacter()); needRecalcBirth = true; }
    ```
  - **agent 提示词同步自动生效**：`character` 变更 → `ROLE_CHANGE` 进 `consumeSkus` → `needsAgentSync` 为真 → `syncPromptToAgent` 重渲染 `{{character}}` 并写回 `ai_agent.system_prompt`。无需额外代码。
  - 头像：`dto.getAvatar()/getDefaultImage()` 走既有 145-146 行免费更新路径；小程序在请求里带上新角色头像/背景，扣券成功才落库（事务内，扣券失败即回滚）。

`CompanionUpdateDTO` 已有 `character` 字段，无需改 DTO。

### 5.3 小程序（miniprogram）

- `config/companion-codes.js`：新增并导出 `ROLES`（4 项，标签与后端 `CHARACTER` 完全一致）。
- 新页面 `pages/companion/change-role/`（4 文件，克隆 `change-occupation`）：
  - 4 选项网格；加载 `c.character` 为当前值；券 = `role_change`；
  - 校验「不能与当前相同」+ 无券引导去 `backpack?focus=role_change`（文案 ¥99）；
  - 确认后 `POST /companion/update`，载荷 `{ deviceId, character, avatar: CHARACTER_AVATARS[selected], defaultImage: CHARACTER_IMAGES[selected] }`；
  - 成功置 `app.globalData.needReconnectAfterReshape = true`，进入完成态。
- `pages/backpack/backpack.js`：SKU→页面映射加 `role_change → change-role`；`ICON_IMG_BY_SKU` 加 `role_change: 'role'`。
- `pages/companion/profile/`：加「角色」展示行（`roleLabel = codes.getLabel(codes.ROLES, c.character)`）与 `onRole()` 跳转。
- `app.json`：注册新页面路径。
- 资源：需一张 `role` 道具图标 PNG（遵循「禁 emoji、用 PNG」规范）；暂无设计稿可先复用现有图标占位。

### 5.4 Python（xiaozhi-server）

零改动。人设是 `ai_agent.system_prompt` 不透明字符串，后端 `syncPromptToAgent` 重渲染后，Python 端在下一次连接（召唤重连）经 `/config/agent-models` 取到新 prompt 并注入 `<identity>` 模板。现成「重塑后断开 → 召唤重连」机制已覆盖生效时机。

## 6. 测试

- `ReshapeVoucherRuleTest`（纯函数，重点）：character 变更 → `ROLE_CHANGE`；与旧值相同 → 不扣券；与 occupation/soul/voice 组合互不干扰。镜像现有用例风格。
- 服务层（建议）：`CompanionServiceImpl`——改角色但无券 → 抛 10321 且 character/avatar 不落库；有券 → 落库并触发 `syncPromptToAgent`。

## 7. 风险

- MEDIUM：头像更新依赖小程序正确传值（与现有 setup 流程一致，可接受）。
- LOW：背包缺 `role` 图标 PNG（资源依赖，非代码阻塞）。
- LOW：`character` 改变联动重算生日/星座/八字（既有逻辑，保留）。

## 8. 文件清单

**后端 (4)**
1. `db/changelog/202606161000.sql`（新增）+ `db.changelog-master.yaml`（追加 changeSet）
2. `item/enums/ConsumeBizType.java`（+1 常量）
3. `companion/util/ReshapeVoucherRule.java`（+常量/字段/分支）+ `ReshapeVoucherRuleTest.java`（+用例）
4. `companion/service/impl/CompanionServiceImpl.java`（门控 character + bizType 分支 + after 传参）

**小程序 (5 + 资源)**
5. `config/companion-codes.js`（+ROLES）
6. `pages/companion/change-role/{js,wxml,wxss,json}`（新增）
7. `pages/backpack/backpack.js`（2 处映射）
8. `pages/companion/profile/{js,wxml}`（角色行 + onRole）
9. `app.json`（注册页面）+ `images/`（role 图标，资源）

**Python (0)**

## 9. 复杂度

中低。后端 ~1.5-2h（含测试）、小程序 ~2-3h、测试/自测 ~1h；总计 ~5-6h。
