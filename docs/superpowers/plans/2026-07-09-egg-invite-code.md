# 蛋宝宝邀请码后端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `main/manager-api` 新建 `invite` 模块，落地蛋宝宝小程序邀请码后端（建模、个人码自动生成、消耗、管理后台 CRUD、测试）。

**Architecture:** 单 `InviteController` 挂 `/invite`，角色隔离（`sys:role:superAdmin` 管理员 / `sys:role:normal` 小程序）。两张表 `ai_invite_code` + `ai_invite_usage`，一行一码。消耗走行锁 + 条件 UPDATE 双保险，幂等靠使用记录唯一键。个人码在微信注册流程用 `REQUIRES_NEW` 子事务自动生成，失败不阻断登录。

**Tech Stack:** Java 21 / Spring Boot 3.4.3 / MyBatis-Plus 3.5.5 / Apache Shiro / Liquibase / JUnit 5 + Mockito。

## Global Constraints

- 子项目根：`main/manager-api/`。所有相对路径均相对此目录。
- 包名前缀：`xiaozhi.modules.invite`。
- 表约定：InnoDB / `utf8mb4` / 审计列 `creator`/`create_date`/`updater`/`update_date`（与 `pet` 模块一致，renren 风格）。
- 错误处理：业务校验用 `throw new RenException("<中文消息>")`（与 `WechatServiceImpl` 一致，`code` 默认 500）；参数校验用 Jakarta validation。
- 响应：统一 `Result<T>` 信封，`new Result<T>().ok(data)`。
- 角色：`@RequiresPermissions("sys:role:normal")` 普通用户 / `"sys:role:superAdmin"` 管理员。
- 个人码配额：配置项 `invite.personal.quota`，默认 5。
- Liquibase：只新增 changeSet，不改历史；文件名按"当前时分"。
- 构建验证：`mvn compile`；测试 `mvn test -DskipTests=false -Dtest=<Class>`。
- 不含小程序端、不含 manager-web 页面。

## 文件结构

| 文件 | 职责 |
|---|---|
| `src/main/resources/db/changelog/202607091500.sql` | 建两张表 |
| `src/main/resources/db/changelog/db.changelog-master.yaml` | 追加 changeSet |
| `src/main/java/xiaozhi/modules/invite/constant/InviteCodeType.java` | 类型常量 1=个人 2=企业 |
| `src/main/java/xiaozhi/modules/invite/entity/InviteCodeEntity.java` | 主表实体 |
| `src/main/java/xiaozhi/modules/invite/entity/InviteUsageEntity.java` | 使用记录实体 |
| `src/main/java/xiaozhi/modules/invite/dao/InviteCodeDao.java` | 主表 Mapper（含 FOR UPDATE / 扣减自定义方法） |
| `src/main/java/xiaozhi/modules/invite/dao/InviteUsageDao.java` | 使用记录 Mapper |
| `src/main/java/xiaozhi/modules/invite/dto/InviteCodeCreateDTO.java` | 企业码创建入参 |
| `src/main/java/xiaozhi/modules/invite/dto/InviteCodeUpdateDTO.java` | 企业码编辑入参 |
| `src/main/java/xiaozhi/modules/invite/dto/InviteConsumeDTO.java` | 消耗入参 `{code}` |
| `src/main/java/xiaozhi/modules/invite/vo/InviteCodeVO.java` | 码视图 |
| `src/main/java/xiaozhi/modules/invite/vo/InviteConsumeVO.java` | 消耗结果视图 |
| `src/main/java/xiaozhi/modules/invite/vo/InviteUsageVO.java` | 使用记录视图 |
| `src/main/java/xiaozhi/modules/invite/vo/InviteStatsVO.java` | 统计视图 |
| `src/main/java/xiaozhi/modules/invite/util/InviteCodeGenerator.java` | 8 位 base32 码生成 |
| `src/main/java/xiaozhi/modules/invite/service/InviteService.java` | 服务接口 |
| `src/main/java/xiaozhi/modules/invite/service/impl/InviteServiceImpl.java` | 服务实现 |
| `src/main/java/xiaozhi/modules/invite/controller/InviteController.java` | REST 端点 |
| `src/main/java/xiaozhi/modules/wechat/service/impl/WechatServiceImpl.java` | 注入个人码自动生成 |
| `src/test/java/xiaozhi/modules/invite/util/InviteCodeGeneratorTest.java` | 生成器单测 |
| `src/test/java/xiaozhi/modules/invite/service/impl/InviteServiceImplTest.java` | 服务单测（Mockito） |
| `src/test/java/xiaozhi/modules/invite/InviteConsumeConcurrencyTest.java` | 消耗并发集成测试 |

**跨任务契约（方法签名）：**
- `InviteCodeGenerator.generate(): String`
- `InviteService.createPersonalCode(Long userId): InviteCodeVO`
- `InviteService.getMine(Long userId): InviteCodeVO`
- `InviteService.consume(String code, Long inviteeUserId): InviteConsumeVO`
- `InviteService.createEnterprise(InviteCodeCreateDTO dto): InviteCodeVO`
- `InviteService.update(InviteCodeUpdateDTO dto): void`
- `InviteService.delete(Long id): void`
- `InviteService.page(Map<String,Object> params): PageData<InviteCodeVO>`
- `InviteService.usageList(Long codeId, Map<String,Object> params): PageData<InviteUsageVO>`
- `InviteService.stats(): InviteStatsVO`
- `InviteCodeDao.selectByCodeForUpdate(String code): InviteCodeEntity`
- `InviteCodeDao.decrementRemaining(Long id): int`

---

### Task 1: 数据库表与 Liquibase changeSet

**Files:**
- Create: `src/main/resources/db/changelog/202607091500.sql`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`（末尾追加）

- [ ] **Step 1: 写 SQL 文件**

创建 `src/main/resources/db/changelog/202607091500.sql`：

```sql
-- 邀请码主表（一行一码）
CREATE TABLE IF NOT EXISTS ai_invite_code (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    code          VARCHAR(32)  NOT NULL COMMENT '邀请码字符串',
    type          TINYINT      NOT NULL COMMENT '1=个人邀请码 2=企业邀请码',
    owner_user_id BIGINT       NULL     COMMENT '个人码=归属用户id;企业码=NULL',
    quota         INT          NOT NULL COMMENT '总配额数量',
    used_count    INT          NOT NULL DEFAULT 0 COMMENT '已使用数量',
    remaining     INT          NOT NULL COMMENT '剩余数量',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0=失效 1=有效',
    expire_time   DATETIME     NULL     COMMENT '过期时间,NULL=不过期',
    remark        VARCHAR(255) NULL     COMMENT '备注',
    creator       BIGINT       NULL     COMMENT '创建人',
    create_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater       BIGINT       NULL     COMMENT '更新人',
    update_date   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    UNIQUE KEY uk_owner_type (owner_user_id, type),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码主表';

-- 邀请码使用记录表
CREATE TABLE IF NOT EXISTS ai_invite_usage (
    id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT 'ID',
    code_id         BIGINT   NOT NULL COMMENT '关联ai_invite_code.id',
    invitee_user_id BIGINT   NOT NULL COMMENT '被邀请人user_id',
    create_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消耗时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_invitee (code_id, invitee_user_id),
    KEY idx_invitee (invitee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码使用记录表';
```

> 注：`uk_owner_type` 保证"一个用户只有一个个人码"（企业码 `owner_user_id` 为 NULL，MySQL 唯一键允许多个 NULL，互不冲突）。

- [ ] **Step 2: 注册 changeSet**

在 `db.changelog-master.yaml` 末尾（最后一个 changeSet 之后）追加：

```yaml
  - changeSet:
      id: 202607091500
      author: minwang
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202607091500.sql
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q compile`
Expected: BUILD SUCCESS（资源文件打包不报错）。

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/db/changelog/202607091500.sql src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(invite): add ai_invite_code and ai_invite_usage tables"
```

---

### Task 2: 常量、实体、DAO、DTO、VO（数据脚手架）

**Files:**
- Create: `src/main/java/xiaozhi/modules/invite/constant/InviteCodeType.java`
- Create: `src/main/java/xiaozhi/modules/invite/entity/InviteCodeEntity.java`
- Create: `src/main/java/xiaozhi/modules/invite/entity/InviteUsageEntity.java`
- Create: `src/main/java/xiaozhi/modules/invite/dao/InviteCodeDao.java`
- Create: `src/main/java/xiaozhi/modules/invite/dao/InviteUsageDao.java`
- Create: `src/main/java/xiaozhi/modules/invite/dto/InviteCodeCreateDTO.java`
- Create: `src/main/java/xiaozhi/modules/invite/dto/InviteCodeUpdateDTO.java`
- Create: `src/main/java/xiaozhi/modules/invite/dto/InviteConsumeDTO.java`
- Create: `src/main/java/xiaozhi/modules/invite/vo/InviteCodeVO.java`
- Create: `src/main/java/xiaozhi/modules/invite/vo/InviteConsumeVO.java`
- Create: `src/main/java/xiaozhi/modules/invite/vo/InviteUsageVO.java`
- Create: `src/main/java/xiaozhi/modules/invite/vo/InviteStatsVO.java`

**Produces:** `InviteCodeType.PERSONAL`(=1)/`ENTERPRISE`(=2)；`InviteCodeDao.selectByCodeForUpdate`、`InviteCodeDao.decrementRemaining`；全部实体/DTO/VO 字段名。

- [ ] **Step 1: 常量类**

```java
package xiaozhi.modules.invite.constant;

public final class InviteCodeType {
    public static final int PERSONAL = 1;
    public static final int ENTERPRISE = 2;
    private InviteCodeType() {}
}
```

- [ ] **Step 2: 主表实体**

```java
package xiaozhi.modules.invite.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_invite_code")
@Schema(description = "邀请码")
public class InviteCodeEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "邀请码字符串")
    private String code;

    @Schema(description = "1=个人 2=企业")
    private Integer type;

    @Schema(description = "个人码=归属用户id;企业码=NULL")
    private Long ownerUserId;

    @Schema(description = "总配额")
    private Integer quota;

    @Schema(description = "已使用")
    private Integer usedCount;

    @Schema(description = "剩余")
    private Integer remaining;

    @Schema(description = "0=失效 1=有效")
    private Integer status;

    @Schema(description = "过期时间")
    private Date expireTime;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人")
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createDate;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新人")
    private Long updater;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新时间")
    private Date updateDate;
}
```

- [ ] **Step 3: 使用记录实体**

```java
package xiaozhi.modules.invite.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_invite_usage")
@Schema(description = "邀请码使用记录")
public class InviteUsageEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "关联ai_invite_code.id")
    private Long codeId;

    @Schema(description = "被邀请人user_id")
    private Long inviteeUserId;

    @Schema(description = "消耗时间")
    private Date createDate;
}
```

- [ ] **Step 4: 主表 DAO（含自定义方法）**

```java
package xiaozhi.modules.invite.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.invite.entity.InviteCodeEntity;

@Mapper
public interface InviteCodeDao extends BaseMapper<InviteCodeEntity> {

    @Select("SELECT * FROM ai_invite_code WHERE code = #{code} FOR UPDATE")
    InviteCodeEntity selectByCodeForUpdate(@Param("code") String code);

    @Update("UPDATE ai_invite_code SET used_count = used_count + 1, remaining = remaining - 1, "
            + "update_date = NOW() WHERE id = #{id} AND remaining > 0")
    int decrementRemaining(@Param("id") Long id);
}
```

- [ ] **Step 5: 使用记录 DAO**

```java
package xiaozhi.modules.invite.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.invite.entity.InviteUsageEntity;

@Mapper
public interface InviteUsageDao extends BaseMapper<InviteUsageEntity> {
}
```

- [ ] **Step 6: 企业码创建 DTO**

```java
package xiaozhi.modules.invite.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "企业邀请码创建请求")
public class InviteCodeCreateDTO {

    @NotNull(message = "配额不能为空")
    @Min(value = 1, message = "配额必须大于0")
    @Schema(description = "总配额", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quota;

    @Schema(description = "状态 0=失效 1=有效，默认1")
    private Integer status;

    @Schema(description = "过期时间，可空")
    private Date expireTime;

    @Schema(description = "备注")
    private String remark;
}
```

- [ ] **Step 7: 企业码编辑 DTO**

```java
package xiaozhi.modules.invite.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "企业邀请码编辑请求")
public class InviteCodeUpdateDTO {

    @NotNull(message = "id不能为空")
    @Schema(description = "邀请码ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "配额（仅可调增）")
    private Integer quota;

    @Schema(description = "状态 0=失效 1=有效")
    private Integer status;

    @Schema(description = "过期时间")
    private Date expireTime;

    @Schema(description = "备注")
    private String remark;
}
```

- [ ] **Step 8: 消耗 DTO**

```java
package xiaozhi.modules.invite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "邀请码消耗请求")
public class InviteConsumeDTO {

    @NotBlank(message = "邀请码不能为空")
    @Schema(description = "邀请码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
```

- [ ] **Step 9: 码 VO**

```java
package xiaozhi.modules.invite.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码视图")
public class InviteCodeVO {

    @Schema(description = "ID")
    private Long id;
    @Schema(description = "邀请码字符串")
    private String code;
    @Schema(description = "1=个人 2=企业")
    private Integer type;
    @Schema(description = "归属用户id")
    private Long ownerUserId;
    @Schema(description = "总配额")
    private Integer quota;
    @Schema(description = "已使用")
    private Integer usedCount;
    @Schema(description = "剩余")
    private Integer remaining;
    @Schema(description = "0=失效 1=有效")
    private Integer status;
    @Schema(description = "过期时间")
    private Date expireTime;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private Date createDate;
}
```

- [ ] **Step 10: 消耗结果 VO**

```java
package xiaozhi.modules.invite.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码消耗结果")
public class InviteConsumeVO {

    @Schema(description = "邀请码ID")
    private Long codeId;
    @Schema(description = "剩余数量")
    private Integer remaining;
    @Schema(description = "邀请码状态")
    private Integer status;
    @Schema(description = "消息：success / 已使用过该邀请码")
    private String message;
}
```

- [ ] **Step 11: 使用记录 VO**

```java
package xiaozhi.modules.invite.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码使用记录视图")
public class InviteUsageVO {

    @Schema(description = "ID")
    private Long id;
    @Schema(description = "关联邀请码ID")
    private Long codeId;
    @Schema(description = "被邀请人user_id")
    private Long inviteeUserId;
    @Schema(description = "消耗时间")
    private Date createDate;
}
```

- [ ] **Step 12: 统计 VO**

```java
package xiaozhi.modules.invite.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码统计")
public class InviteStatsVO {

    @Schema(description = "邀请码总数")
    private int totalCodes;
    @Schema(description = "总消耗次数")
    private int totalConsumed;
    @Schema(description = "个人码数")
    private int personalCount;
    @Schema(description = "企业码数")
    private int enterpriseCount;
}
```

- [ ] **Step 13: 编译验证**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 14: 提交**

```bash
git add src/main/java/xiaozhi/modules/invite
git commit -m "feat(invite): scaffold entities, daos, dtos, vos"
```

---

### Task 3: 邀请码生成器（TDD）

**Files:**
- Create: `src/main/java/xiaozhi/modules/invite/util/InviteCodeGenerator.java`
- Test: `src/test/java/xiaozhi/modules/invite/util/InviteCodeGeneratorTest.java`

**Produces:** `InviteCodeGenerator.generate(): String`（8 位，去歧义字符 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`）。

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/xiaozhi/modules/invite/util/InviteCodeGeneratorTest.java`：

```java
package xiaozhi.modules.invite.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodeGeneratorTest {

    private static final String ALLOWED = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Test
    @DisplayName("generate 返回 8 位且全部在允许字符集内")
    void generate_lengthAndCharset() {
        for (int i = 0; i < 500; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(code).hasSize(8);
            for (char c : code.toCharArray()) {
                assertThat(ALLOWED.indexOf(c)).isGreaterThan(-1);
            }
        }
    }

    @Test
    @DisplayName("generate 不包含歧义字符 0/1/I/O")
    void generate_noAmbiguousChars() {
        for (int i = 0; i < 500; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(code).doesNotContain("0").doesNotContain("1")
                    .doesNotContain("O").doesNotContain("I");
        }
    }

    @Test
    @DisplayName("generate 10000 次无重复")
    void generate_uniqueOver10000() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(seen.add(code)).as("重复码: %s", code).isTrue();
        }
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -DskipTests=false -Dtest=InviteCodeGeneratorTest -q`
Expected: 编译失败 / `InviteCodeGenerator` 不存在。

- [ ] **Step 3: 写实现**

创建 `src/main/java/xiaozhi/modules/invite/util/InviteCodeGenerator.java`：

```java
package xiaozhi.modules.invite.util;

import java.security.SecureRandom;

/**
 * 邀请码生成器：8 位，去歧义字符集（无 0/1/I/O），32 字母表。
 */
public final class InviteCodeGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeGenerator() {}

    public static String generate() {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -DskipTests=false -Dtest=InviteCodeGeneratorTest -q`
Expected: 三个测试全 PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/invite/util/InviteCodeGenerator.java src/test/java/xiaozhi/modules/invite/util/InviteCodeGeneratorTest.java
git commit -m "feat(invite): add 8-char base32 invite code generator"
```

---

### Task 4: 服务接口与实现骨架 + createPersonalCode / getMine（TDD）

**Files:**
- Create: `src/main/java/xiaozhi/modules/invite/service/InviteService.java`
- Create: `src/main/java/xiaozhi/modules/invite/service/impl/InviteServiceImpl.java`
- Test: `src/test/java/xiaozhi/modules/invite/service/impl/InviteServiceImplTest.java`

**Consumes:** `InviteCodeGenerator.generate()`、`InviteCodeDao`、`InviteUsageDao`、`InviteCodeType`、`InviteCodeVO`、`InviteConsumeVO`、`InviteUsageVO`、`InviteStatsVO`。
**Produces:** `InviteService` 全部方法签名；本任务实现 `createPersonalCode`、`getMine`、`generateUniqueCode`、`toVO`。

- [ ] **Step 1: 写服务接口**

创建 `src/main/java/xiaozhi/modules/invite/service/InviteService.java`：

```java
package xiaozhi.modules.invite.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.dto.InviteCodeUpdateDTO;
import xiaozhi.modules.invite.entity.InviteCodeEntity;
import xiaozhi.modules.invite.vo.InviteCodeVO;
import xiaozhi.modules.invite.vo.InviteConsumeVO;
import xiaozhi.modules.invite.vo.InviteStatsVO;
import xiaozhi.modules.invite.vo.InviteUsageVO;

public interface InviteService extends BaseService<InviteCodeEntity> {

    InviteCodeVO createPersonalCode(Long userId);

    InviteCodeVO getMine(Long userId);

    InviteConsumeVO consume(String code, Long inviteeUserId);

    InviteCodeVO createEnterprise(InviteCodeCreateDTO dto);

    void update(InviteCodeUpdateDTO dto);

    void delete(Long id);

    PageData<InviteCodeVO> page(Map<String, Object> params);

    PageData<InviteUsageVO> usageList(Long codeId, Map<String, Object> params);

    InviteStatsVO stats();
}
```

- [ ] **Step 2: 写失败测试（createPersonalCode + getMine）**

创建 `src/test/java/xiaozhi/modules/invite/service/impl/InviteServiceImplTest.java`：

```java
package xiaozhi.modules.invite.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.invite.constant.InviteCodeType;
import xiaozhi.modules.invite.dao.InviteCodeDao;
import xiaozhi.modules.invite.dao.InviteUsageDao;
import xiaozhi.modules.invite.entity.InviteCodeEntity;
import xiaozhi.modules.invite.vo.InviteCodeVO;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InviteServiceImpl")
class InviteServiceImplTest {

    @Mock
    private InviteCodeDao inviteCodeDao;
    @Mock
    private InviteUsageDao inviteUsageDao;

    private InviteServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new InviteServiceImpl();
        setField(BaseServiceImpl.class, service, "baseDao", inviteCodeDao);
        setField(InviteServiceImpl.class, service, "inviteUsageDao", inviteUsageDao);
        setField(InviteServiceImpl.class, service, "personalQuota", 5);
        service.setClock(Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneId.systemDefault()));
        when(inviteCodeDao.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("createPersonalCode - 新用户生成个人码 quota=5 remaining=5")
    void createPersonalCode_newUser() {
        when(inviteCodeDao.selectOne(any())).thenReturn(null);

        InviteCodeVO vo = service.createPersonalCode(100L);

        assertThat(vo).isNotNull();
        assertThat(vo.getType()).isEqualTo(InviteCodeType.PERSONAL);
        assertThat(vo.getOwnerUserId()).isEqualTo(100L);
        assertThat(vo.getQuota()).isEqualTo(5);
        assertThat(vo.getRemaining()).isEqualTo(5);
        assertThat(vo.getUsedCount()).isZero();
        assertThat(vo.getStatus()).isEqualTo(1);
        assertThat(vo.getCode()).hasSize(8);
        verify(inviteCodeDao).insert(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("createPersonalCode - 已有个人码时幂等返回已有记录，不重复插入")
    void createPersonalCode_idempotent() {
        InviteCodeEntity existing = new InviteCodeEntity();
        existing.setId(9L);
        existing.setCode("AAAA2222");
        existing.setType(InviteCodeType.PERSONAL);
        existing.setOwnerUserId(100L);
        existing.setQuota(5);
        existing.setRemaining(3);
        when(inviteCodeDao.selectOne(any())).thenReturn(existing);

        InviteCodeVO vo = service.createPersonalCode(100L);

        assertThat(vo.getCode()).isEqualTo("AAAA2222");
        assertThat(vo.getRemaining()).isEqualTo(3);
        verify(inviteCodeDao, never()).insert(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("createPersonalCode - userId 为空抛 NOT_NULL")
    void createPersonalCode_nullUserId() {
        try {
            service.createPersonalCode(null);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getCode()).isEqualTo(10001);
        }
    }

    @Test
    @DisplayName("getMine - 找到返回；未找到抛异常")
    void getMine() {
        InviteCodeEntity e = new InviteCodeEntity();
        e.setId(1L);
        e.setCode("BBBB3333");
        e.setType(InviteCodeType.PERSONAL);
        when(inviteCodeDao.selectOne(any())).thenReturn(e);
        assertThat(service.getMine(7L).getCode()).isEqualTo("BBBB3333");

        when(inviteCodeDao.selectOne(any())).thenReturn(null);
        try {
            service.getMine(7L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException ex) {
            assertThat(ex.getMsg()).contains("个人邀请码");
        }
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value)
            throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn test -DskipTests=false -Dtest=InviteServiceImplTest -q`
Expected: 编译失败（`InviteServiceImpl` 不存在）。

- [ ] **Step 4: 写实现（骨架 + createPersonalCode / getMine）**

创建 `src/main/java/xiaozhi/modules/invite/service/impl/InviteServiceImpl.java`。后续任务会扩展此类，本任务先实现 `createPersonalCode`/`getMine` 及私有辅助方法，其余方法先抛 `UnsupportedOperationException` 占位：

```java
package xiaozhi.modules.invite.service.impl;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.invite.constant.InviteCodeType;
import xiaozhi.modules.invite.dao.InviteCodeDao;
import xiaozhi.modules.invite.dao.InviteUsageDao;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.dto.InviteCodeUpdateDTO;
import xiaozhi.modules.invite.entity.InviteCodeEntity;
import xiaozhi.modules.invite.entity.InviteUsageEntity;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.invite.util.InviteCodeGenerator;
import xiaozhi.modules.invite.vo.InviteCodeVO;
import xiaozhi.modules.invite.vo.InviteConsumeVO;
import xiaozhi.modules.invite.vo.InviteStatsVO;
import xiaozhi.modules.invite.vo.InviteUsageVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InviteServiceImpl extends BaseServiceImpl<InviteCodeDao, InviteCodeEntity>
        implements InviteService {

    private static final int CODE_MAX_RETRY = 5;

    @Autowired
    private InviteUsageDao inviteUsageDao;

    @Value("${invite.personal.quota:5}")
    private int personalQuota;

    private Clock clock = Clock.systemDefaultZone();

    /** 供测试注入固定时钟 */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Date now() {
        return Date.from(clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public InviteCodeVO createPersonalCode(Long userId) {
        if (userId == null) {
            throw new RenException(ErrorCode.NOT_NULL);
        }
        InviteCodeEntity existing = baseDao.selectOne(new QueryWrapper<InviteCodeEntity>()
                .eq("owner_user_id", userId).eq("type", InviteCodeType.PERSONAL));
        if (existing != null) {
            return toVO(existing);
        }
        InviteCodeEntity entity = new InviteCodeEntity();
        entity.setCode(generateUniqueCode());
        entity.setType(InviteCodeType.PERSONAL);
        entity.setOwnerUserId(userId);
        entity.setQuota(personalQuota);
        entity.setUsedCount(0);
        entity.setRemaining(personalQuota);
        entity.setStatus(1);
        entity.setCreateDate(now());
        baseDao.insert(entity);
        return toVO(entity);
    }

    @Override
    public InviteCodeVO getMine(Long userId) {
        InviteCodeEntity entity = baseDao.selectOne(new QueryWrapper<InviteCodeEntity>()
                .eq("owner_user_id", userId).eq("type", InviteCodeType.PERSONAL));
        if (entity == null) {
            throw new RenException("未找到个人邀请码");
        }
        return toVO(entity);
    }

    private String generateUniqueCode() {
        for (int i = 0; i < CODE_MAX_RETRY; i++) {
            String code = InviteCodeGenerator.generate();
            Long count = baseDao.selectCount(new QueryWrapper<InviteCodeEntity>().eq("code", code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new RenException("邀请码生成失败，请重试");
    }

    private InviteCodeVO toVO(InviteCodeEntity e) {
        InviteCodeVO vo = new InviteCodeVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setType(e.getType());
        vo.setOwnerUserId(e.getOwnerUserId());
        vo.setQuota(e.getQuota());
        vo.setUsedCount(e.getUsedCount());
        vo.setRemaining(e.getRemaining());
        vo.setStatus(e.getStatus());
        vo.setExpireTime(e.getExpireTime());
        vo.setRemark(e.getRemark());
        vo.setCreateDate(e.getCreateDate());
        return vo;
    }

    // 以下方法在后续任务实现
    @Override
    public InviteConsumeVO consume(String code, Long inviteeUserId) {
        throw new UnsupportedOperationException("Task 5");
    }

    @Override
    public InviteCodeVO createEnterprise(InviteCodeCreateDTO dto) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public void update(InviteCodeUpdateDTO dto) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public PageData<InviteCodeVO> page(Map<String, Object> params) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public PageData<InviteUsageVO> usageList(Long codeId, Map<String, Object> params) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public InviteStatsVO stats() {
        throw new UnsupportedOperationException("Task 6");
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -DskipTests=false -Dtest=InviteServiceImplTest -q`
Expected: 5 个测试全 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/xiaozhi/modules/invite/service src/test/java/xiaozhi/modules/invite/service/impl/InviteServiceImplTest.java
git commit -m "feat(invite): service skeleton with createPersonalCode and getMine"
```

---

### Task 5: 消耗逻辑 consume（TDD 核心）

**Files:**
- Modify: `src/main/java/xiaozhi/modules/invite/service/impl/InviteServiceImpl.java`（替换 `consume` 方法）
- Test: 追加用例到 `src/test/java/xiaozhi/modules/invite/service/impl/InviteServiceImplTest.java`

**Consumes:** `InviteCodeDao.selectByCodeForUpdate`、`InviteCodeDao.decrementRemaining`、`InviteUsageDao`、`InviteConsumeVO`。

- [ ] **Step 1: 追加失败测试**

先在测试类 import 区追加（若尚未存在）：

```java
import xiaozhi.modules.invite.vo.InviteConsumeVO;
```

在 `InviteServiceImplTest` 类内追加以下用例（放在 `getMine()` 测试之后、`setField` 辅助方法之前）：

```java
    @Test
    @DisplayName("consume - 正常消耗：扣减并写使用记录")
    void consume_normal() {
        InviteCodeEntity entity = codeEntity(1L, "CCCC4444", 200L, 5, 5, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate("CCCC4444")).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(0L);
        when(inviteCodeDao.decrementRemaining(1L)).thenReturn(1);

        InviteConsumeVO vo = service.consume("CCCC4444", 300L);

        assertThat(vo.getMessage()).isEqualTo("success");
        assertThat(vo.getRemaining()).isEqualTo(4);
        verify(inviteCodeDao).decrementRemaining(1L);
        verify(inviteUsageDao).insert(any());
    }

    @Test
    @DisplayName("consume - 码不存在抛 邀请码无效")
    void consume_notFound() {
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(null);
        try {
            service.consume("NOPE", 1L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("无效");
        }
        verify(inviteCodeDao, never()).decrementRemaining(any());
    }

    @Test
    @DisplayName("consume - status=0 抛 已失效")
    void consume_disabled() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 3, 0, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("失效");
        }
    }

    @Test
    @DisplayName("consume - 已过期抛 已过期")
    void consume_expired() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 3, 1,
                Date.from(Instant.parse("2026-07-01T00:00:00Z")));
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("过期");
        }
    }

    @Test
    @DisplayName("consume - remaining=0 抛 已无剩余")
    void consume_noRemaining() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 0, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("无剩余");
        }
    }

    @Test
    @DisplayName("consume - 自邀拦截：owner==invitee 抛异常")
    void consume_selfInvite() {
        InviteCodeEntity entity = codeEntity(1L, "X", 300L, 5, 5, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("自己的邀请码");
        }
        verify(inviteCodeDao, never()).decrementRemaining(any());
    }

    @Test
    @DisplayName("consume - 幂等：同被邀请人重复消耗不扣减，返回 已使用过")
    void consume_idempotent() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 4, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(1L);

        InviteConsumeVO vo = service.consume("X", 300L);

        assertThat(vo.getMessage()).contains("已使用");
        assertThat(vo.getRemaining()).isEqualTo(4);
        verify(inviteCodeDao, never()).decrementRemaining(any());
        verify(inviteUsageDao, never()).insert(any());
    }

    @Test
    @DisplayName("consume - 并发抢空：decrementRemaining 返回 0 抛 已无剩余")
    void consume_raceEmpty() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 1, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(0L);
        when(inviteCodeDao.decrementRemaining(1L)).thenReturn(0);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("无剩余");
        }
    }

    @Test
    @DisplayName("consume - 企业码 owner=NULL 不受自邀限制")
    void consume_enterpriseNoOwner() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 10, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(0L);
        when(inviteCodeDao.decrementRemaining(1L)).thenReturn(1);

        InviteConsumeVO vo = service.consume("X", 300L);
        assertThat(vo.getMessage()).isEqualTo("success");
    }

    private static InviteCodeEntity codeEntity(Long id, String code, Long owner,
            int quota, int remaining, int status, Date expire) {
        InviteCodeEntity e = new InviteCodeEntity();
        e.setId(id);
        e.setCode(code);
        e.setType(owner == null ? InviteCodeType.ENTERPRISE : InviteCodeType.PERSONAL);
        e.setOwnerUserId(owner);
        e.setQuota(quota);
        e.setUsedCount(quota - remaining);
        e.setRemaining(remaining);
        e.setStatus(status);
        e.setExpireTime(expire);
        return e;
    }
```

> 注：`Instant` 已在 `import java.time.Instant;` 导入（Step 2 of Task 4 已导入 `Clock`/`Instant`/`ZoneId`）。

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -DskipTests=false -Dtest=InviteServiceImplTest -q`
Expected: 新增 consume 用例 FAIL（`UnsupportedOperationException`）。

- [ ] **Step 3: 写实现**

将 `InviteServiceImpl.consume` 方法替换为（删掉 `throw new UnsupportedOperationException("Task 5");` 占位）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteConsumeVO consume(String code, Long inviteeUserId) {
        if (code == null || code.isBlank()) {
            throw new RenException(ErrorCode.NOT_NULL);
        }
        if (inviteeUserId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }

        InviteCodeEntity entity = baseDao.selectByCodeForUpdate(code);
        if (entity == null) {
            throw new RenException("邀请码无效");
        }
        if (entity.getStatus() == null || entity.getStatus() != 1) {
            throw new RenException("邀请码已失效");
        }
        if (entity.getExpireTime() != null && !entity.getExpireTime().after(now())) {
            throw new RenException("邀请码已过期");
        }
        if (entity.getRemaining() == null || entity.getRemaining() <= 0) {
            throw new RenException("邀请码已无剩余");
        }
        if (entity.getOwnerUserId() != null && entity.getOwnerUserId().equals(inviteeUserId)) {
            throw new RenException("不能使用自己的邀请码");
        }

        // 幂等：同一被邀请人对同一码重复消耗不扣减
        Long used = inviteUsageDao.selectCount(new QueryWrapper<InviteUsageEntity>()
                .eq("code_id", entity.getId()).eq("invitee_user_id", inviteeUserId));
        if (used != null && used > 0) {
            InviteConsumeVO vo = new InviteConsumeVO();
            vo.setCodeId(entity.getId());
            vo.setRemaining(entity.getRemaining());
            vo.setStatus(entity.getStatus());
            vo.setMessage("已使用过该邀请码");
            return vo;
        }

        int affected = baseDao.decrementRemaining(entity.getId());
        if (affected == 0) {
            throw new RenException("邀请码已无剩余");
        }

        InviteUsageEntity usage = new InviteUsageEntity();
        usage.setCodeId(entity.getId());
        usage.setInviteeUserId(inviteeUserId);
        usage.setCreateDate(now());
        inviteUsageDao.insert(usage);

        InviteConsumeVO vo = new InviteConsumeVO();
        vo.setCodeId(entity.getId());
        vo.setRemaining(entity.getRemaining() - 1);
        vo.setStatus(entity.getStatus());
        vo.setMessage("success");
        return vo;
    }
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -DskipTests=false -Dtest=InviteServiceImplTest -q`
Expected: 全部用例 PASS（createPersonalCode 5 + consume 9 = 14）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/invite/service/impl/InviteServiceImpl.java src/test/java/xiaozhi/modules/invite/service/impl/InviteServiceImplTest.java
git commit -m "feat(invite): implement consume with row lock, idempotency, self-invite guard"
```

---

### Task 6: 管理员 CRUD / page / usageList / stats（TDD）

**Files:**
- Modify: `src/main/java/xiaozhi/modules/invite/service/impl/InviteServiceImpl.java`（替换 6 个占位方法，并加 `toUsageVO` 辅助）
- Test: 追加用例到 `InviteServiceImplTest`

**Consumes:** `InviteCodeCreateDTO`、`InviteCodeUpdateDTO`、`PageData`、`IPage`/`Page`、`InviteStatsVO`、`InviteUsageVO`。

- [ ] **Step 1: 追加失败测试**

在 `InviteServiceImplTest` 追加（放在 `codeEntity` 辅助方法之前）：

```java
    @Test
    @DisplayName("createEnterprise - 生成企业码 quota 来自入参 owner=NULL")
    void createEnterprise() {
        xiaozhi.modules.invite.dto.InviteCodeCreateDTO dto =
                new xiaozhi.modules.invite.dto.InviteCodeCreateDTO();
        dto.setQuota(100);
        dto.setStatus(1);
        dto.setRemark("展会A");

        xiaozhi.modules.invite.vo.InviteCodeVO vo = service.createEnterprise(dto);

        assertThat(vo.getType()).isEqualTo(InviteCodeType.ENTERPRISE);
        assertThat(vo.getOwnerUserId()).isNull();
        assertThat(vo.getQuota()).isEqualTo(100);
        assertThat(vo.getRemaining()).isEqualTo(100);
        assertThat(vo.getUsedCount()).isZero();
        assertThat(vo.getRemark()).isEqualTo("展会A");
        verify(inviteCodeDao).insert(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("update - quota 调增允许并重算 remaining")
    void update_increaseQuota() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 7, 1, null); // used=3
        when(inviteCodeDao.selectById(1L)).thenReturn(entity);

        xiaozhi.modules.invite.dto.InviteCodeUpdateDTO dto =
                new xiaozhi.modules.invite.dto.InviteCodeUpdateDTO();
        dto.setId(1L);
        dto.setQuota(20);
        service.update(dto);

        assertThat(entity.getQuota()).isEqualTo(20);
        assertThat(entity.getRemaining()).isEqualTo(17); // 20-3
        verify(inviteCodeDao).updateById(entity);
    }

    @Test
    @DisplayName("update - quota 小于 used_count 抛异常")
    void update_quotaBelowUsed() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 2, 1, null); // used=8
        when(inviteCodeDao.selectById(1L)).thenReturn(entity);

        xiaozhi.modules.invite.dto.InviteCodeUpdateDTO dto =
                new xiaozhi.modules.invite.dto.InviteCodeUpdateDTO();
        dto.setId(1L);
        dto.setQuota(5); // < used(8)
        try {
            service.update(dto);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("配额不能小于已使用数量");
        }
        verify(inviteCodeDao, never()).updateById(any());
    }

    @Test
    @DisplayName("delete - used_count=0 可删")
    void delete_unused() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 10, 1, null); // used=0
        when(inviteCodeDao.selectById(1L)).thenReturn(entity);
        service.delete(1L);
        verify(inviteCodeDao).deleteById(1L);
    }

    @Test
    @DisplayName("delete - used_count>0 拒绝")
    void delete_used() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 7, 1, null); // used=3
        when(inviteCodeDao.selectById(1L)).thenReturn(entity);
        try {
            service.delete(1L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("已被使用");
        }
        verify(inviteCodeDao, never()).deleteById(any());
    }
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -DskipTests=false -Dtest=InviteServiceImplTest -q`
Expected: 新增用例 FAIL（`UnsupportedOperationException`）。

- [ ] **Step 3: 写实现**

在 `InviteServiceImpl` 顶部 `import` 区追加：

```java
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
```

将 6 个占位方法替换为以下实现（并新增 `toUsageVO` 私有方法，放在 `toVO` 之后）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteCodeVO createEnterprise(InviteCodeCreateDTO dto) {
        if (dto.getQuota() == null || dto.getQuota() <= 0) {
            throw new RenException("配额必须大于0");
        }
        InviteCodeEntity entity = new InviteCodeEntity();
        entity.setCode(generateUniqueCode());
        entity.setType(InviteCodeType.ENTERPRISE);
        entity.setOwnerUserId(null);
        entity.setQuota(dto.getQuota());
        entity.setUsedCount(0);
        entity.setRemaining(dto.getQuota());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setExpireTime(dto.getExpireTime());
        entity.setRemark(dto.getRemark());
        entity.setCreateDate(now());
        baseDao.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InviteCodeUpdateDTO dto) {
        InviteCodeEntity entity = baseDao.selectById(dto.getId());
        if (entity == null) {
            throw new RenException("邀请码不存在");
        }
        if (dto.getQuota() != null) {
            int used = entity.getUsedCount() == null ? 0 : entity.getUsedCount();
            if (dto.getQuota() < used) {
                throw new RenException("配额不能小于已使用数量");
            }
            entity.setQuota(dto.getQuota());
            entity.setRemaining(dto.getQuota() - used);
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getExpireTime() != null) {
            entity.setExpireTime(dto.getExpireTime());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
        entity.setUpdateDate(now());
        baseDao.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InviteCodeEntity entity = baseDao.selectById(id);
        if (entity == null) {
            throw new RenException("邀请码不存在");
        }
        int used = entity.getUsedCount() == null ? 0 : entity.getUsedCount();
        if (used > 0) {
            throw new RenException("邀请码已被使用，无法删除");
        }
        baseDao.deleteById(id);
    }

    @Override
    public PageData<InviteCodeVO> page(Map<String, Object> params) {
        IPage<InviteCodeEntity> page = getPage(params, "create_date", false);
        QueryWrapper<InviteCodeEntity> wrapper = new QueryWrapper<>();
        if (params.get("type") != null) {
            wrapper.eq("type", params.get("type"));
        }
        if (params.get("status") != null) {
            wrapper.eq("status", params.get("status"));
        }
        if (params.get("ownerUserId") != null) {
            wrapper.eq("owner_user_id", params.get("ownerUserId"));
        }
        IPage<InviteCodeEntity> result = baseDao.selectPage(page, wrapper);
        List<InviteCodeVO> list = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageData<>(list, result.getTotal());
    }

    @Override
    public PageData<InviteUsageVO> usageList(Long codeId, Map<String, Object> params) {
        long cur = params.get("page") == null ? 1 : Long.parseLong(params.get("page").toString());
        long limit = params.get("limit") == null ? 10 : Long.parseLong(params.get("limit").toString());
        Page<InviteUsageEntity> page = new Page<>(cur, limit);
        QueryWrapper<InviteUsageEntity> wrapper =
                new QueryWrapper<InviteUsageEntity>().eq("code_id", codeId);
        IPage<InviteUsageEntity> result = inviteUsageDao.selectPage(page, wrapper);
        List<InviteUsageVO> list = result.getRecords().stream()
                .map(this::toUsageVO).collect(Collectors.toList());
        return new PageData<>(list, result.getTotal());
    }

    @Override
    public InviteStatsVO stats() {
        InviteStatsVO vo = new InviteStatsVO();
        vo.setTotalCodes(toInt(baseDao.selectCount(null)));
        vo.setTotalConsumed(toInt(inviteUsageDao.selectCount(null)));
        vo.setPersonalCount(toInt(baseDao.selectCount(
                new QueryWrapper<InviteCodeEntity>().eq("type", InviteCodeType.PERSONAL)));
        vo.setEnterpriseCount(toInt(baseDao.selectCount(
                new QueryWrapper<InviteCodeEntity>().eq("type", InviteCodeType.ENTERPRISE)));
        return vo;
    }

    private static int toInt(Long v) {
        return v == null ? 0 : Math.toIntExact(v);
    }

    private InviteUsageVO toUsageVO(InviteUsageEntity e) {
        InviteUsageVO vo = new InviteUsageVO();
        vo.setId(e.getId());
        vo.setCodeId(e.getCodeId());
        vo.setInviteeUserId(e.getInviteeUserId());
        vo.setCreateDate(e.getCreateDate());
        return vo;
    }
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -DskipTests=false -Dtest=InviteServiceImplTest -q`
Expected: 全部用例 PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/invite/service/impl/InviteServiceImpl.java src/test/java/xiaozhi/modules/invite/service/impl/InviteServiceImplTest.java
git commit -m "feat(invite): admin CRUD, page, usage list, stats"
```

---

### Task 7: 微信注册流程注入个人码自动生成

**Files:**
- Modify: `src/main/java/xiaozhi/modules/wechat/service/impl/WechatServiceImpl.java`

**Consumes:** `InviteService.createPersonalCode(Long)`。

- [ ] **Step 1: 注入 InviteService**

在 `WechatServiceImpl` 类字段区（`private final AgentService agentService;` 之后）追加：

```java
    private final xiaozhi.modules.invite.service.InviteService inviteService;
```

> 该类用 `@RequiredArgsConstructor`，final 字段自动加入构造参数。

- [ ] **Step 2: 在创建用户后调用个人码生成**

定位 `createSysUserForOpenid` 方法内 `sysUserDao.insert(user);` 这一行，在其后、`return new UserCreationResult(...)` 之前插入：

```java
        // 自动为该 openid 用户生成个人邀请码（失败不阻断登录）
        try {
            inviteService.createPersonalCode(user.getId());
        } catch (Exception e) {
            log.warn("为新用户生成个人邀请码失败 userId={}, err={}", user.getId(), e.getMessage());
        }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: 运行既有 wechat 相关测试（若有）确认未破坏**

Run: `mvn test -DskipTests=false -Dtest='*Wechat*' -q`
Expected: 无编译/注入错误（如无匹配测试则 BUILD SUCCESS 跳过）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/wechat/service/impl/WechatServiceImpl.java
git commit -m "feat(wechat): auto-generate personal invite code on user creation"
```

---

### Task 8: InviteController 接线

**Files:**
- Create: `src/main/java/xiaozhi/modules/invite/controller/InviteController.java`

**Consumes:** `InviteService` 全部方法、`SecurityUser.getUserId()`、`InviteConsumeDTO`/`InviteCodeCreateDTO`/`InviteCodeUpdateDTO`。

- [ ] **Step 1: 写控制器**

```java
package xiaozhi.modules.invite.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.dto.InviteCodeUpdateDTO;
import xiaozhi.modules.invite.dto.InviteConsumeDTO;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.invite.vo.InviteCodeVO;
import xiaozhi.modules.invite.vo.InviteConsumeVO;
import xiaozhi.modules.invite.vo.InviteStatsVO;
import xiaozhi.modules.invite.vo.InviteUsageVO;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "邀请码")
@RestController
@RequestMapping("/invite")
@AllArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/mine")
    @Operation(summary = "查询我的个人邀请码")
    @RequiresPermissions("sys:role:normal")
    public Result<InviteCodeVO> mine() {
        Long userId = SecurityUser.getUserId();
        return new Result<InviteCodeVO>().ok(inviteService.getMine(userId));
    }

    @PostMapping("/consume")
    @Operation(summary = "消耗邀请码领蛋")
    @RequiresPermissions("sys:role:normal")
    public Result<InviteConsumeVO> consume(@Valid @RequestBody InviteConsumeDTO dto) {
        Long userId = SecurityUser.getUserId();
        return new Result<InviteConsumeVO>().ok(inviteService.consume(dto.getCode(), userId));
    }

    @PostMapping
    @Operation(summary = "创建企业邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<InviteCodeVO> create(@Valid @RequestBody InviteCodeCreateDTO dto) {
        return new Result<InviteCodeVO>().ok(inviteService.createEnterprise(dto));
    }

    @PutMapping
    @Operation(summary = "编辑企业邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> update(@Valid @RequestBody InviteCodeUpdateDTO dto) {
        inviteService.update(dto);
        return new Result<>();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除企业邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> delete(@PathVariable Long id) {
        inviteService.delete(id);
        return new Result<>();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<InviteCodeVO>> page(@RequestParam Map<String, Object> params) {
        return new Result<PageData<InviteCodeVO>>().ok(inviteService.page(params));
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "查询某邀请码使用记录")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<InviteUsageVO>> usage(@PathVariable Long id,
            @RequestParam Map<String, Object> params) {
        return new Result<PageData<InviteUsageVO>>().ok(inviteService.usageList(id, params));
    }

    @GetMapping("/stats")
    @Operation(summary = "邀请码概览统计")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<InviteStatsVO> stats() {
        return new Result<InviteStatsVO>().ok(inviteService.stats());
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 启动验证（可选，需 MySQL+Redis）**

Run: `mvn spring-boot:run`（启动后看接口文档 `http://localhost:8002/xiaozhi/doc.html` 出现"邀请码"分组即成功，可手动跳过）
Expected: 应用启动无 Bean 注入失败。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/xiaozhi/modules/invite/controller/InviteController.java
git commit -m "feat(invite): wire REST endpoints with role isolation"
```

---

### Task 9: 消耗并发集成测试

**Files:**
- Create: `src/test/java/xiaozhi/modules/invite/InviteConsumeConcurrencyTest.java`

**Consumes:** `InviteService.consume`、`WechatService`/`SecurityUser`（或直接调用 service）。
**前提：** 需要可用的 dev 环境 MySQL+Redis（与 `PetServiceImplProfileTest` 一致的 `@SpringBootTest @ActiveProfiles("dev")`）。

- [ ] **Step 1: 写集成测试**

```java
package xiaozhi.modules.invite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import xiaozhi.common.exception.RenException;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.invite.vo.InviteCodeVO;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("邀请码消耗并发集成测试")
@Transactional
class InviteConsumeConcurrencyTest {

    @Autowired
    private InviteService inviteService;

    @Test
    @DisplayName("同被邀请人并发消耗同码：usage 仅 1 条，used_count 仅 +1")
    void sameInvitee_concurrent_idempotent() throws Exception {
        InviteCodeVO code = createEnterpriseCode(10);
        Long invitee = 900001L;

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyUsed = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    var vo = inviteService.consume(code.getCode(), invitee);
                    if ("success".equals(vo.getMessage())) {
                        success.incrementAndGet();
                    } else if (vo.getMessage().contains("已使用")) {
                        alreadyUsed.incrementAndGet();
                    }
                } catch (RenException e) {
                    errors.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(alreadyUsed.get() + errors.get()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("多被邀请人抢 remaining=2 的码：恰好 2 人成功")
    void multipleInvitees_raceForTwoSeats() throws Exception {
        InviteCodeVO code = createEnterpriseCode(2);
        int threads = 3;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        List<Long> invitees = new ArrayList<>();
        for (long i = 1; i <= threads; i++) {
            invitees.add(910000L + i);
        }

        for (Long invitee : invitees) {
            pool.submit(() -> {
                try {
                    start.await();
                    var vo = inviteService.consume(code.getCode(), invitee);
                    if ("success".equals(vo.getMessage())) {
                        success.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(2);
    }

    private InviteCodeVO createEnterpriseCode(int quota) {
        InviteCodeCreateDTO dto = new InviteCodeCreateDTO();
        dto.setQuota(quota);
        dto.setStatus(1);
        dto.setRemark("concurrency-test-" + UUID.randomUUID());
        return inviteService.createEnterprise(dto);
    }
}
```

> 注：`@Transactional` 会在测试后回滚，避免污染库。`remaining=2` 用例中第三人应得"邀请码已无剩余"异常（被 catch 忽略），故 `success==2`。

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn test -DskipTests=false -Dtest=InviteConsumeConcurrencyTest -q`
Expected: 两个并发用例 PASS（需 dev 环境 MySQL+Redis 可用；如不可用则标记 @Disabled 并记录待在 CI 跑）。

- [ ] **Step 3: 提交**

```bash
git add src/test/java/xiaozhi/modules/invite/InviteConsumeConcurrencyTest.java
git commit -m "test(invite): concurrency integration tests for consume"
```

---

## 完成标准

- `mvn -q compile` BUILD SUCCESS。
- `mvn test -DskipTests=false -Dtest='InviteCodeGeneratorTest,InviteServiceImplTest' -q` 全 PASS（不依赖外部环境）。
- `InviteConsumeConcurrencyTest` 在 dev 环境 MySQL+Redis 可用时 PASS。
- 9 个任务各有独立提交。
- 未触及小程序端与 manager-web。

## Self-Review 记录

（写完已自检：spec 各节均有对应任务；无 TBD/TODO 占位；方法签名跨任务一致——`createPersonalCode`/`getMine`/`consume`/`createEnterprise`/`update`/`delete`/`page`/`usageList`/`stats` 与接口及控制器调用一致；`selectByCodeForUpdate`/`decrementRemaining` 在 DAO 定义且被服务实现调用。）
