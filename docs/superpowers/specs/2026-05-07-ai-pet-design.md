# AI 宠物领域设计

## 概述

在 manager-api 中新增"AI 宠物"领域，用于管理 AI 宠物。每个宠物关联一个设备，只属于一个用户。当宠物设备调用出生接口时，系统根据出生时间自动计算八字、五行和星座，然后通过 LLM 推算宠物的 MBTI 人格。

## 数据模型

### 表：`ai_pet`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | VARCHAR(32) | PK | UUID 主键 |
| `user_id` | BIGINT | NOT NULL, INDEX | 归属用户，从设备表查询 |
| `device_id` | VARCHAR(32) | NOT NULL, UNIQUE | 关联设备，一个设备一个宠物 |
| `nickname` | VARCHAR(50) | | 昵称，从默认列表随机分配 |
| `birth_date` | DATETIME | NOT NULL | 出生日期时间（精确到时辰） |
| `bazi` | JSON | | 八字，如 `{"year":"丙午","month":"壬辰","day":"乙亥","hour":"丙戌"}` |
| `wuxing` | JSON | | 五行，英文键名，如 `{"metal":0,"wood":2,"water":3,"fire":5,"earth":4}` |
| `zodiac` | VARCHAR(20) | | 星座英文编码，如 `taurus` |
| `mbti` | VARCHAR(4) | | MBTI 人格，如 `INTJ` |
| `creator` | BIGINT | | 创建者 |
| `create_date` | DATETIME | | 创建时间 |
| `updater` | BIGINT | | 更新者 |
| `update_date` | DATETIME | | 更新时间 |

**约束：**
- `device_id` 唯一（一个设备只能有一个宠物）
- `user_id` 普通索引，提升查询性能

## API 接口

### POST /pet/birth

**调用方：** 设备

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | String | 是 | 设备 ID |

**处理流程：**
1. 校验 deviceId 存在且已绑定用户
2. 校验该设备未创建过宠物
3. 使用当前服务器时间作为出生时间
4. 调用 `PetBirthCalculator` 计算八字、五行、星座
5. 组装 prompt（八字 + 五行），调用 LLM 推算 MBTI
6. 从默认昵称列表随机分配昵称
7. 写入 `ai_pet` 表
8. 返回完整宠物信息

**降级策略：** LLM 调用失败时，MBTI 默认设为 `INFP`

### GET /pet/detail/{deviceId}

**调用方：** 用户 / 设备

**返回：** 该设备关联的宠物信息，不存在返回错误

### GET /pet/list

**调用方：** 用户（管理台）

**返回：** 当前用户下所有宠物列表

### PUT /pet/update

**调用方：** 用户（管理台）

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | String | 是 | 宠物 ID |
| `nickname` | String | 否 | 昵称 |

**权限：** 用户只能编辑自己的宠物

## 八字五行计算

使用 [lunar-java](https://github.com/6tail/lunar-java) 库（GitHub 5k+ star）：

```xml
<dependency>
    <groupId>com.github.6tail</groupId>
    <artifactId>lunar</artifactId>
</dependency>
```

封装为 `PetBirthCalculator` 工具类。输入：`LocalDateTime`。输出：
- **八字：** 年柱、月柱、日柱、时柱的天干地支
- **五行：** 统计八字中金木水火土的出现次数（0~5），使用英文键名存储
- **星座：** 根据月日计算星座，存储英文编码（`aries`、`taurus`、`gemini`、`cancer`、`leo`、`virgo`、`libra`、`scorpio`、`sagittarius`、`capricorn`、`aquarius`、`pisces`）

## LLM 推算 MBTI

复用项目现有的 LLM 基础设施（`xiaozhi.modules.llm`），不引入新依赖。

**Prompt：**
```
根据以下八字和五行信息，推算这个AI宠物的MBTI人格类型。

八字：年柱-丙午，月柱-壬辰，日柱-乙亥，时柱-丙戌
五行：金-0，木-2，水-3，火-5，土-4

请只回复四个字母的MBTI类型，不要其他内容。
```

**解析：** 从 LLM 返回文本中提取合法的 MBTI 四字母编码（16种），非法值降级为 `INFP`。

## 默认昵称列表

配置在 `application.yml` 或数据库字典表中，初始列表约 20-30 个，例如：

`小团子, 豆豆, 年糕, 糯米, 芒果, 布丁, ...`

后续支持管理台维护。

## 模块结构

新增模块 `xiaozhi.modules.pet`，遵循现有包结构规范：

```
xiaozhi/modules/pet/
├── controller/
│   └── PetController.java
├── service/
│   └── PetService.java
├── service/impl/
│   └── PetServiceImpl.java
├── dao/
│   └── PetDao.java
├── entity/
│   └── PetEntity.java
├── dto/
│   └── PetBirthDTO.java
├── vo/
│   └── PetVO.java
└── util/
    └── PetBirthCalculator.java
```

**依赖关系：** pet 模块依赖 `device` 模块（查询用户）和 `llm` 模块（调用 LLM）。仅新增 `lunar` 库依赖。

## 设计决策

1. **同步出生流程：** 宠物出生是设备首次配网时的一次性事件，用户预期会有等待时间。同步模式更简单且够用，无需异步机制。
2. **英文编码存储：** 五行键名和星座值使用英文存储，保持一致性并便于国际化。中文标签可在 VO/展示层添加。
3. **device_id 唯一约束：** 在数据库层面强制一个设备只能有一个宠物。
4. **宠物独立于智能体：** AI 宠物是独立领域，不是 Agent 的扩展。
