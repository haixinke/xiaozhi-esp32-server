# 收藏卡多卡功能设计

## 概述

将蛋宝宝小程序的收藏卡从 `ai_pet` 表单字段（`collection_card_url`）升级为独立多卡表，支持每只宠物最多 10 张收藏卡，按获取时间自动排序，每张卡有独立的一句话简介。

### 需求摘要

- 每只宠物最多 10 张收藏卡，同一宠物的卡片图片不可重复
- 所有卡片共用默认图片池（每原型 10 张 `.webp`），不同宠物之间可重复
- 按获取时间自动排序（最先获取的 sortOrder=0），用户无法手动调整
- 破壳时自动生成第一张卡（source=HATCH），简介使用宠物表的 `personalityBrief` 字段
- 后续卡片简介从 `PERSONALITY_BRIEF_POOL` 预设文案池随机生成，存储在收藏卡表中
- 后续卡片的获取方式待定，预留 `source` 字段标记来源类型
- 保留 `ai_pet.collection_card_url` 列不删除，不做数据迁移，新破壳不再写入该字段

## 数据模型

### 新建表 `ai_pet_collection_card`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(32) PK | UUID 主键 |
| `pet_id` | VARCHAR(32) NOT NULL | 关联 `ai_pet.id` |
| `image_url` | VARCHAR(1024) NOT NULL | 收藏卡图片 URL |
| `brief` | VARCHAR(100) | 一句话简介 |
| `source` | VARCHAR(50) NOT NULL DEFAULT 'HATCH' | 来源类型（HATCH=破壳首卡，后续扩展） |
| `sort_order` | INT NOT NULL DEFAULT 0 | 排序序号（0=最先获取，按获取时间递增） |
| `creator` | BIGINT | 创建者 |
| `create_date` | DATETIME | 创建时间 |
| `updater` | BIGINT | 更新者 |
| `update_date` | DATETIME | 更新时间 |

**索引：**
- `UNIQUE INDEX uk_pet_card_image (pet_id, image_url)` — 防止同一宠物收藏重复图片
- `INDEX idx_pet_card_sort (pet_id, sort_order)` — 按排序查询卡片列表

### `ai_pet` 表

- **保留** `collection_card_url` 列不动，不删除、不迁移
- 新破壳的宠物不再写入该字段，该字段对已有数据仅做存量保留

### Liquibase Changelog

新增 `202607161200.sql`，仅包含 `CREATE TABLE ai_pet_collection_card`，不涉及 `ai_pet` 表变更。在 `db.changelog-master.yaml` 末尾注册。

## 后端改动

### 新增类

**1. `PetCollectionCardEntity`** — 实体类，映射 `ai_pet_collection_card` 表，使用 `@TableName("ai_pet_collection_card")`，字段与表一一对应。

**2. `PetCollectionCardDao`** — 继承 `BaseMapper<PetCollectionCardEntity>`。

**3. `CollectionCardVO`** — 卡片视图对象，字段：`id`、`imageUrl`、`brief`、`source`、`sortOrder`、`createDate`。

**4. `PetCollectionCardService`** — 卡片业务服务，核心方法：

| 方法 | 说明 |
|---|---|
| `listByPetId(String petId)` | 按 sortOrder 升序返回宠物全部收藏卡 |
| `createCard(petId, prototype, brief, source)` | 创建新卡：校验上限(10张)、选不重复图片、算 sort_order、插入记录 |

**图片去重逻辑：** `createCard` 内部查询该宠物已有 `image_url` 列表，从配置池 10 张中排除已占用的，随机选一张。若 10 张已集齐则抛异常拒绝创建。

**sort_order 计算：** `max(现有 sort_order) + 1`，首卡为 0。

### 修改类

**5. `PetVO`**
- 移除 `collectionCardUrl` 字段
- 新增 `List<CollectionCardVO> collectionCards` 字段

**6. `PetServiceImpl`**

- **`hatch()`**：不再 `pet.setCollectionCardUrl(...)`，改为调用 `petCollectionCardService.createCard(petId, prototype, personalityBrief, "HATCH")` 插入破壳首卡。首卡创建与宠物状态变更在同一个 `@Transactional` 中原子提交。
- **`toVO()`**：移除 `vo.setCollectionCardUrl(...)`，改为 `vo.setCollectionCards(petCollectionCardService.listByPetId(pet.getId()))`。查询失败时返回空列表，不影响宠物主体数据。
- **`randomCollectionCardUrl()`**：该方法逻辑迁移到 `PetCollectionCardService.createCard()` 内部，`PetServiceImpl` 中删除。

**7. `CollectionCardGenerationListener`**（当前已禁用）
- 未来重新启用时，改为调用 `createCard()` 插入新表，不再回写 `ai_pet.collection_card_url`

### 不变部分

- `PetEntity` 保留 `collectionCardUrl` 字段（对应 DB 列保留）
- `PetCollectionCardProperties` 配置类不变，仍作为图片池配置
- `CollectionCardImageService` 接口不变（AI 生成图片的能力保留）
- `PERSONALITY_BRIEF_POOL` 文案池不变，后续卡片的 brief 从中随机取

## 前端改动

### 1. `pet-store.js`（本地缓存层）

**`savePetFromVO(vo)` 改动：**
- 移除 `collectionCardUrl: vo.collectionCardUrl` 映射
- 移除 `collectionCard: buildCollectionCard(vo)` 本地拼装
- 新增 `collectionCards: vo.collectionCards || []`（直接用后端返回的卡片数组）

**`buildCollectionCard(vo)` 函数：** 删除（卡片数据改为后端返回，不再前端拼装）

**`createCollectionCard()` 简化：**
- 调用 `petApi.hatchPet(pet.id)` 获取破壳 VO（此时 VO 已含 `collectionCards`）
- 调用 `savePetFromVO(vo)` 保存
- 不再本地构建 card 对象

**`getStage(pet)` 判断条件：**
- `pet.collectionCard` → `pet.hatchStatus === 'HATCHED'`
- 改用 `hatchStatus` 而非卡片列表判断，避免未迁移存量数据（已有破壳宠物无新表卡片记录）被误判为未破壳

**`getDailyStatus()` 引用更新：**
- `pet.collectionCard` → `pet.hatchStatus === 'HATCHED'`

### 2. `home.wxml` / `home.js`

**home.wxml 图片 src：**
- 当前：`pet.petType === '锦鲤' ? '...card-fish.png' : '...card-rabbit.png'`（硬编码）
- 改为：`{{pet.collectionCards[0].imageUrl}}`（取首卡图片）

### 3. `collection-card.js` / `collection-card.wxml`（收藏卡详情页）

**数据来源调整：**
- 卡片专属数据（`imageUrl`、`brief`）从 `pet.collectionCards[index]` 获取
- 宠物档案数据（name、birthday、zodiac、gender、mbti、bloodType 等）仍从 `petStore.getPet()` 获取
- `serial` 编号仍由前端从 pet 数据生成（格式不变）

**支持多卡浏览：**
- `onLoad(query)` 接收 `index` 参数，默认为 0（首卡）
- 页面展示对应 index 的卡片

### 4. `album.js` / `album.wxml`（卡册页）

**从单卡展示改为多卡列表：**
- `onShow()` 读取 `pet.collectionCards` 数组
- 列表渲染所有卡片，每条显示：缩略图（`imageUrl`）、简介（`brief`）、来源标签（`source`）
- 点击某张卡 → `wx.navigateTo` 到 collection-card 页面，传 `index` 参数
- 空态提示保持不变

### 5. `pet-detail.js`（宠物档案页）

- 若引用了 `pet.collectionCard`，改为引用 `pet.collectionCards[0]`（首卡）

## 测试与错误处理

### 后端测试

**`PetCollectionCardService` 单元测试（新增）：**

| 测试用例 | 说明 |
|---|---|
| `createCard_firstCard_hatchSource` | 首卡创建：source=HATCH，brief=personalityBrief，sortOrder=0 |
| `createCard_imageDedup_noRepeat` | 同宠物已有图片 A，新卡不应再选 A |
| `createCard_maxLimit_reject` | 已有 10 张卡时拒绝创建，抛异常 |
| `createCard_sortOrder_incremental` | 第 N 张卡的 sortOrder = N-1 |
| `listByPetId_sortedAsc` | 返回按 sortOrder 升序排列的卡片列表 |

**`PetServiceImpl` 测试更新：**

| 测试用例 | 说明 |
|---|---|
| `hatch_eggReached_success` | 验证调用 `createCard(petId, prototype, brief, "HATCH")`，不再验证 `pet.setCollectionCardUrl` |
| `toVO_populatesCollectionCards` | 验证 `vo.collectionCards` 来自 service 查询结果 |

**现有测试适配：**
- `CollectionCardGenerationListenerTest` — 适配新表逻辑
- `CollectionCardImageServiceImplTest` — 无需改动（接口不变）

### 前端测试

- `pet-store.test.js` — 测试数据从 `collectionCard` 单对象改为 `collectionCards` 数组
- `getStage` 测试 — 验证 `hatchStatus === 'HATCHED'` 判断 hatched 状态

### 错误处理

| 场景 | 处理方式 |
|---|---|
| 创建第 11 张卡 | 抛 `RenException`，提示"收藏卡已集齐（10/10）" |
| 同图片重复（并发场景） | DB 唯一索引拦截，catch `DuplicateKeyException` 后重试选另一张 |
| `toVO()` 查询卡片失败 | 返回空列表，不影响宠物主体数据返回 |
| 前端 `collectionCards` 为空但已破壳 | `getStage` 依据 `hatchStatus==='HATCHED'` 返回 hatched 状态，卡册页显示空态 |

### 数据一致性

- `sort_order` 由 `createCard` 内部计算：`max(现有 sortOrder) + 1`
- 并发创建同一宠物的卡片时，唯一索引 `(pet_id, image_url)` 保证不重复
- 破壳流程在 `@Transactional` 中，首卡创建与宠物状态变更原子提交

## 不在本次范围内

- 后续收藏卡的具体触发条件（如对话里程碑、社交分享等）
- AI 动态生成收藏卡图片（`CollectionCardGenerationListener` 仍保持禁用）
- 存量数据迁移（`ai_pet.collection_card_url` → 新表）
- 卡册页面的多卡浏览交互优化（如滑动切换、卡片翻转动效等）
