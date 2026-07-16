# 收藏卡多卡功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将收藏卡从 `ai_pet` 单字段升级为独立多卡表，支持每只宠物最多 10 张卡，按获取时间排序，每张卡有独立简介。

**Architecture:** 新建 `ai_pet_collection_card` 表存储多卡数据，后端新增 `PetCollectionCardService` 管理卡片 CRUD，`PetServiceImpl.hatch()` 改为调用该服务插入首卡，`toVO()` 返回卡片列表。前端 `pet-store.js` 直接使用后端返回的卡片数组，卡册页和收藏卡详情页支持多卡展示。

**Tech Stack:** Java 17 + Spring Boot + MyBatis-Plus + Liquibase (OceanBase), 微信小程序原生开发

---

## File Structure

### 后端新增文件
- `main/manager-api/src/main/resources/db/changelog/202607161200.sql` — 建表 SQL
- `main/manager-api/src/main/java/xiaozhi/modules/pet/entity/PetCollectionCardEntity.java` — 实体类
- `main/manager-api/src/main/java/xiaozhi/modules/pet/dao/PetCollectionCardDao.java` — DAO
- `main/manager-api/src/main/java/xiaozhi/modules/pet/vo/CollectionCardVO.java` — 视图对象
- `main/manager-api/src/main/java/xiaozhi/modules/pet/service/PetCollectionCardService.java` — 服务接口
- `main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/PetCollectionCardServiceImpl.java` — 服务实现
- `main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetCollectionCardServiceImplTest.java` — 服务测试

### 后端修改文件
- `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` — 注册 changelog
- `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java` — 新增错误码
- `main/manager-api/src/main/java/xiaozhi/modules/pet/vo/PetVO.java` — 移除 collectionCardUrl，新增 collectionCards
- `main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/PetServiceImpl.java` — hatch/toVO 改造
- `main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetServiceImplHatchTest.java` — 测试适配

### 前端修改文件
- `main/egg-miniprogram/miniprogram/utils/pet-store.js` — 缓存层改造
- `main/egg-miniprogram/miniprogram/utils/pet-store.test.js` — 测试适配
- `main/egg-miniprogram/miniprogram/pages/home/home.wxml` — 图片 src 改为首卡
- `main/egg-miniprogram/miniprogram/pages/collection-card/collection-card.js` — 多卡浏览
- `main/egg-miniprogram/miniprogram/pages/album/album.js` — 多卡列表
- `main/egg-miniprogram/miniprogram/pages/album/album.wxml` — 多卡列表渲染
- `main/egg-miniprogram/miniprogram/pages/pet-detail/pet-detail.js` — 引用更新

---

## Task 1: Liquibase 建表

**Files:**
- Create: `main/manager-api/src/main/resources/db/changelog/202607161200.sql`
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: 创建建表 SQL**

```sql
CREATE TABLE `ai_pet_collection_card` (
    `id` VARCHAR(32) NOT NULL COMMENT '收藏卡唯一标识',
    `pet_id` VARCHAR(32) NOT NULL COMMENT '关联宠物ID',
    `image_url` VARCHAR(1024) NOT NULL COMMENT '收藏卡图片URL',
    `brief` VARCHAR(100) COMMENT '一句话简介',
    `source` VARCHAR(50) NOT NULL DEFAULT 'HATCH' COMMENT '来源类型: HATCH-破壳首卡',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号(0=最先获取)',
    `creator` BIGINT COMMENT '创建者',
    `create_date` DATETIME COMMENT '创建时间',
    `updater` BIGINT COMMENT '更新者',
    `update_date` DATETIME COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_pet_card_image` (`pet_id`, `image_url`),
    INDEX `idx_pet_card_sort` (`pet_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物收藏卡表';
```

- [ ] **Step 2: 在 master yaml 末尾注册**

在 `db.changelog-master.yaml` 最后一项 `202607111500` 之后追加：

```yaml
  - changeSet:
      id: 202607161200
      author: minwang
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202607161200.sql
```

- [ ] **Step 3: Commit**

```bash
git add main/manager-api/src/main/resources/db/changelog/202607161200.sql main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat: add ai_pet_collection_card table for multi-card support"
```

---

## Task 2: 后端 Entity & DAO

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pet/entity/PetCollectionCardEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pet/dao/PetCollectionCardDao.java`

- [ ] **Step 1: 创建 PetCollectionCardEntity**

```java
package xiaozhi.modules.pet.entity;

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
@TableName("ai_pet_collection_card")
@Schema(description = "宠物收藏卡")
public class PetCollectionCardEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "ID")
    private String id;

    @Schema(description = "关联宠物ID")
    private String petId;

    @Schema(description = "收藏卡图片URL")
    private String imageUrl;

    @Schema(description = "一句话简介")
    private String brief;

    @Schema(description = "来源类型: HATCH-破壳首卡")
    private String source;

    @Schema(description = "排序序号(0=最先获取)")
    private Integer sortOrder;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.UPDATE)
    private Long updater;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private Date updateDate;
}
```

- [ ] **Step 2: 创建 PetCollectionCardDao**

```java
package xiaozhi.modules.pet.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.pet.entity.PetCollectionCardEntity;

@Mapper
public interface PetCollectionCardDao extends BaseMapper<PetCollectionCardEntity> {
}
```

- [ ] **Step 3: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pet/entity/PetCollectionCardEntity.java main/manager-api/src/main/java/xiaozhi/modules/pet/dao/PetCollectionCardDao.java
git commit -m "feat: add PetCollectionCardEntity and PetCollectionCardDao"
```

---

## Task 3: 后端 VO & ErrorCode

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pet/vo/CollectionCardVO.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pet/vo/PetVO.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java`

- [ ] **Step 1: 创建 CollectionCardVO**

```java
package xiaozhi.modules.pet.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "收藏卡视图对象")
public class CollectionCardVO {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "收藏卡图片URL")
    private String imageUrl;

    @Schema(description = "一句话简介")
    private String brief;

    @Schema(description = "来源类型: HATCH-破壳首卡")
    private String source;

    @Schema(description = "排序序号(0=最先获取)")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private Date createDate;
}
```

- [ ] **Step 2: 修改 PetVO — 移除 collectionCardUrl，新增 collectionCards**

在 `PetVO.java` 中：

移除：
```java
@Schema(description = "AI生成的破壳收藏卡图片URL")
private String collectionCardUrl;
```

新增（放在 `avatarUrl` 字段之后）：
```java
@Schema(description = "收藏卡列表")
private java.util.List<CollectionCardVO> collectionCards;
```

同时在文件顶部添加 import：
```java
import java.util.List;
```

- [ ] **Step 3: 新增 ErrorCode**

在 `ErrorCode.java` 的 `PET_HATCH_TIME_NOT_REACHED` 行之后添加：

```java
int PET_COLLECTION_CARD_LIMIT_REACHED = 10215; // 收藏卡已集齐(10/10)
```

- [ ] **Step 4: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pet/vo/CollectionCardVO.java main/manager-api/src/main/java/xiaozhi/modules/pet/vo/PetVO.java main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java
git commit -m "feat: add CollectionCardVO, update PetVO and ErrorCode for multi-card"
```

---

## Task 4: 后端 PetCollectionCardService（TDD）

**Files:**
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetCollectionCardServiceImplTest.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pet/service/PetCollectionCardService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/PetCollectionCardServiceImpl.java`

- [ ] **Step 1: 写服务接口**

```java
package xiaozhi.modules.pet.service;

import java.util.List;

import xiaozhi.modules.pet.vo.CollectionCardVO;

public interface PetCollectionCardService {

    /**
     * 按 sortOrder 升序返回宠物全部收藏卡。
     */
    List<CollectionCardVO> listByPetId(String petId);

    /**
     * 创建新收藏卡：校验上限(10张)、选不重复图片、算 sort_order、插入记录。
     *
     * @param petId     宠物ID
     * @param prototype 宠物原型(锦鲤/玉兔)
     * @param brief     一句话简介
     * @param source    来源类型(如 HATCH)
     * @return 创建后的收藏卡 VO
     */
    CollectionCardVO createCard(String petId, String prototype, String brief, String source);
}
```

- [ ] **Step 2: 写测试 — createCard 首卡创建**

```java
package xiaozhi.modules.pet.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.config.PetCollectionCardProperties;
import xiaozhi.modules.pet.dao.PetCollectionCardDao;
import xiaozhi.modules.pet.entity.PetCollectionCardEntity;
import xiaozhi.modules.pet.vo.CollectionCardVO;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetCollectionCardService 测试")
class PetCollectionCardServiceImplTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = org.mockito.Mockito.mock(ApplicationContext.class);
        MessageSource messageSource = org.mockito.Mockito.mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PetCollectionCardDao petCollectionCardDao;

    private PetCollectionCardServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        PetCollectionCardProperties properties = buildProperties();
        service = new PetCollectionCardServiceImpl(petCollectionCardDao, properties);
    }

    private PetCollectionCardProperties buildProperties() {
        PetCollectionCardProperties properties = new PetCollectionCardProperties();
        properties.setFallbackUrl("https://oss.eggbabe.com/default-card/fish/card-fish-0.webp");

        PetCollectionCardProperties.Prototype koi = new PetCollectionCardProperties.Prototype();
        koi.setBaseUrl("https://oss.eggbabe.com/default-card/fish/");
        koi.setPrefix("card-fish");
        koi.setCount(10);
        properties.setKoi(koi);

        PetCollectionCardProperties.Prototype rabbit = new PetCollectionCardProperties.Prototype();
        rabbit.setBaseUrl("https://oss.eggbabe.com/default-card/rabbit/");
        rabbit.setPrefix("card-rabbit");
        rabbit.setCount(10);
        properties.setRabbit(rabbit);

        return properties;
    }

    @Test
    @DisplayName("createCard - 首卡: source=HATCH, sortOrder=0")
    void createCard_firstCard_hatchSource() {
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(petCollectionCardDao.insert(any(PetCollectionCardEntity.class))).thenReturn(1);

        CollectionCardVO result = service.createCard("pet-1", "锦鲤", "温暖好奇", "HATCH");

        assertThat(result.getSource()).isEqualTo("HATCH");
        assertThat(result.getBrief()).isEqualTo("温暖好奇");
        assertThat(result.getSortOrder()).isEqualTo(0);
        assertThat(result.getImageUrl()).startsWith("https://oss.eggbabe.com/default-card/fish/card-fish-");
    }

    @Test
    @DisplayName("createCard - 图片去重: 已有图片A, 新卡不应再选A")
    void createCard_imageDedup_noRepeat() {
        PetCollectionCardEntity existing = new PetCollectionCardEntity();
        existing.setImageUrl("https://oss.eggbabe.com/default-card/fish/card-fish-0.webp");
        existing.setSortOrder(0);
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        when(petCollectionCardDao.insert(any(PetCollectionCardEntity.class))).thenReturn(1);

        CollectionCardVO result = service.createCard("pet-1", "锦鲤", "测试简介", "MILESTONE");

        assertThat(result.getImageUrl()).isNotEqualTo("https://oss.eggbabe.com/default-card/fish/card-fish-0.webp");
        assertThat(result.getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("createCard - 已有10张卡时拒绝创建")
    void createCard_maxLimit_reject() {
        // 构造 10 张已有卡片
        List<PetCollectionCardEntity> existing = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> {
                    PetCollectionCardEntity e = new PetCollectionCardEntity();
                    e.setImageUrl("https://oss.eggbabe.com/default-card/fish/card-fish-" + i + ".webp");
                    e.setSortOrder(i);
                    return e;
                })
                .toList();
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> service.createCard("pet-1", "锦鲤", "测试", "MILESTONE"))
                .isInstanceOf(xiaozhi.common.exception.RenException.class);
    }

    @Test
    @DisplayName("listByPetId - 按 sortOrder 升序返回")
    void listByPetId_sortedAsc() {
        PetCollectionCardEntity card2 = new PetCollectionCardEntity();
        card2.setId("card-2");
        card2.setSortOrder(1);
        card2.setImageUrl("url-2");
        card2.setBrief("brief-2");
        card2.setSource("HATCH");

        PetCollectionCardEntity card1 = new PetCollectionCardEntity();
        card1.setId("card-1");
        card1.setSortOrder(0);
        card1.setImageUrl("url-1");
        card1.setBrief("brief-1");
        card1.setSource("HATCH");

        // selectList 返回乱序，service 内部应按 sortOrder 排序
        when(petCollectionCardDao.selectList(any(QueryWrapper.class))).thenReturn(List.of(card2, card1));

        List<CollectionCardVO> result = service.listByPetId("pet-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSortOrder()).isEqualTo(0);
        assertThat(result.get(1).getSortOrder()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `cd main/manager-api && mvn test -pl . -Dtest=PetCollectionCardServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `PetCollectionCardServiceImpl` 类不存在

- [ ] **Step 4: 实现 PetCollectionCardServiceImpl**

```java
package xiaozhi.modules.pet.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pet.config.PetCollectionCardProperties;
import xiaozhi.modules.pet.dao.PetCollectionCardDao;
import xiaozhi.modules.pet.entity.PetCollectionCardEntity;
import xiaozhi.modules.pet.service.PetCollectionCardService;
import xiaozhi.modules.pet.vo.CollectionCardVO;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetCollectionCardServiceImpl implements PetCollectionCardService {

    private static final int MAX_CARDS = 10;

    private final PetCollectionCardDao petCollectionCardDao;
    private final PetCollectionCardProperties petCollectionCardProperties;

    @Override
    public List<CollectionCardVO> listByPetId(String petId) {
        QueryWrapper<PetCollectionCardEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("pet_id", petId);
        wrapper.orderByAsc("sort_order");
        List<PetCollectionCardEntity> entities = petCollectionCardDao.selectList(wrapper);
        return entities.stream()
                .sorted(Comparator.comparingInt(PetCollectionCardEntity::getSortOrder))
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public CollectionCardVO createCard(String petId, String prototype, String brief, String source) {
        // 查询已有卡片
        QueryWrapper<PetCollectionCardEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("pet_id", petId);
        List<PetCollectionCardEntity> existing = petCollectionCardDao.selectList(wrapper);

        if (existing.size() >= MAX_CARDS) {
            throw new RenException(ErrorCode.PET_COLLECTION_CARD_LIMIT_REACHED);
        }

        // 已有图片集合
        List<String> usedUrls = existing.stream()
                .map(PetCollectionCardEntity::getImageUrl)
                .collect(Collectors.toList());

        // 从配置池中选不重复的图片
        String imageUrl = selectNonDuplicateUrl(prototype, usedUrls);

        // 计算 sort_order
        int sortOrder = existing.stream()
                .mapToInt(PetCollectionCardEntity::getSortOrder)
                .max()
                .orElse(-1) + 1;

        // 插入记录
        PetCollectionCardEntity entity = new PetCollectionCardEntity();
        entity.setPetId(petId);
        entity.setImageUrl(imageUrl);
        entity.setBrief(brief);
        entity.setSource(source);
        entity.setSortOrder(sortOrder);
        petCollectionCardDao.insert(entity);

        log.info("收藏卡创建 petId={}, sortOrder={}, source={}", petId, sortOrder, source);
        return toVO(entity);
    }

    /**
     * 从配置池中随机选一张未被该宠物使用的图片 URL。
     */
    private String selectNonDuplicateUrl(String prototype, List<String> usedUrls) {
        PetCollectionCardProperties.Prototype config = selectConfig(prototype);
        if (config == null || !config.hasImage()) {
            return petCollectionCardProperties.getFallbackUrl() == null ? "" : petCollectionCardProperties.getFallbackUrl();
        }

        // 生成所有可用 URL，排除已使用的
        List<String> available = java.util.stream.IntStream.range(0, config.getCount())
                .mapToObj(i -> config.getBaseUrl() + config.getPrefix() + "-" + i + ".webp")
                .filter(url -> !usedUrls.contains(url))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            throw new RenException(ErrorCode.PET_COLLECTION_CARD_LIMIT_REACHED);
        }

        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private PetCollectionCardProperties.Prototype selectConfig(String prototype) {
        if ("锦鲤".equals(prototype)) {
            return petCollectionCardProperties.getKoi();
        }
        if ("玉兔".equals(prototype)) {
            return petCollectionCardProperties.getRabbit();
        }
        return null;
    }

    private CollectionCardVO toVO(PetCollectionCardEntity entity) {
        CollectionCardVO vo = new CollectionCardVO();
        vo.setId(entity.getId());
        vo.setImageUrl(entity.getImageUrl());
        vo.setBrief(entity.getBrief());
        vo.setSource(entity.getSource());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreateDate(entity.getCreateDate());
        return vo;
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `cd main/manager-api && mvn test -pl . -Dtest=PetCollectionCardServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — 4 tests pass

- [ ] **Step 6: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pet/service/PetCollectionCardService.java main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/PetCollectionCardServiceImpl.java main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetCollectionCardServiceImplTest.java
git commit -m "feat: add PetCollectionCardService with createCard and listByPetId"
```

---

## Task 5: 后端 PetServiceImpl 改造

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/PetServiceImpl.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetServiceImplHatchTest.java`

- [ ] **Step 1: 在 PetServiceImpl 中注入 PetCollectionCardService**

在字段声明区（第77行 `petCollectionCardProperties` 之后）添加：

```java
private final PetCollectionCardService petCollectionCardService;
```

在 import 区添加：

```java
import xiaozhi.modules.pet.service.PetCollectionCardService;
import xiaozhi.modules.pet.vo.CollectionCardVO;
```

- [ ] **Step 2: 修改 hatch() — 不再 setCollectionCardUrl，改为 createCard**

找到 `hatch()` 方法中的这一段（约第487-501行）：

```java
String avatarUrl = randomAvatarUrl(pet.getPrototype());
String collectionCardUrl = randomCollectionCardUrl(pet.getPrototype());

// 回填宠物破壳档案（需在 agent 创建前写 gender/bloodType 以便模板渲染）
pet.setHatchStatus(HATCH_STATUS_HATCHED);
pet.setHatchedAt(now);
pet.setBirthDate(now);
pet.setBazi(calc.bazi());
pet.setWuxing(calc.wuxing());
pet.setZodiac(calc.zodiac());
pet.setMbti(mbti);
pet.setPersonalityBrief(brief);
pet.setGender(gender);
pet.setBloodType(bloodType);
pet.setAvatarUrl(avatarUrl);
pet.setCollectionCardUrl(collectionCardUrl);
pet.setUpdater(userId);
```

改为：

```java
String avatarUrl = randomAvatarUrl(pet.getPrototype());

// 回填宠物破壳档案（需在 agent 创建前写 gender/bloodType 以便模板渲染）
pet.setHatchStatus(HATCH_STATUS_HATCHED);
pet.setHatchedAt(now);
pet.setBirthDate(now);
pet.setBazi(calc.bazi());
pet.setWuxing(calc.wuxing());
pet.setZodiac(calc.zodiac());
pet.setMbti(mbti);
pet.setPersonalityBrief(brief);
pet.setGender(gender);
pet.setBloodType(bloodType);
pet.setAvatarUrl(avatarUrl);
pet.setUpdater(userId);
```

然后在 `petDao.updateById(pet)` 之后、`refreshTodayMood(pet)` 之前（约第529-535行之间），添加首卡创建：

```java
// 创建破壳首卡：简介使用 personalityBrief
petCollectionCardService.createCard(pet.getId(), pet.getPrototype(), brief, "HATCH");
```

- [ ] **Step 3: 修改 toVO() — 返回 collectionCards 列表**

找到 `toVO()` 方法中的（约第643行）：

```java
vo.setCollectionCardUrl(pet.getCollectionCardUrl());
```

替换为：

```java
vo.setCollectionCards(petCollectionCardService.listByPetId(pet.getId()));
```

- [ ] **Step 4: 删除 randomCollectionCardUrl() 方法**

删除 `PetServiceImpl` 中的 `randomCollectionCardUrl()` 方法（约第549-551行）：

```java
/**
 * 按原型从配置中随机取一张默认收藏卡 URL。
 */
private String randomCollectionCardUrl(String prototype) {
    return petCollectionCardProperties.randomCollectionCardUrl(prototype);
}
```

- [ ] **Step 5: 更新 PetServiceImplHatchTest — 添加 mock 和验证**

在测试类中添加 mock 字段：

```java
@Mock private xiaozhi.modules.pet.service.PetCollectionCardService petCollectionCardService;
```

修改 `setUp()` 中的构造函数调用，在末尾追加 `petCollectionCardService` 参数：

```java
petService = new PetServiceImpl(petDao, deviceDao, llmService, chatHistoryDao,
        memoryDao, userProfileDao, inviteService, agentService, eventPublisher,
        avatarProperties, collectionCardProperties, petCollectionCardService);
```

在 `hatch_eggReached_success` 测试方法中，移除对 `collectionCardUrl` 的验证，添加对 `createCard` 的验证：

```java
verify(petCollectionCardService).createCard(eq(PET_ID), anyString(), anyString(), eq("HATCH"));
```

添加 stub（在 setUp 或测试方法中）：

```java
when(petCollectionCardService.listByPetId(anyString())).thenReturn(java.util.List.of());
```

- [ ] **Step 6: 更新其他 PetServiceImpl 测试的构造函数**

`PetServiceImplAdoptTest` 和 `PetServiceImplTodayMoodTest` 也构造了 `PetServiceImpl`，需同步添加 `petCollectionCardService` mock 参数。

在每个测试类中添加：
```java
@Mock private xiaozhi.modules.pet.service.PetCollectionCardService petCollectionCardService;
```

修改构造函数调用，在末尾追加 `petCollectionCardService` 参数。

添加 stub（在 setUp 或各测试方法中）：
```java
when(petCollectionCardService.listByPetId(anyString())).thenReturn(java.util.List.of());
```

- [ ] **Step 7: 编译验证**

Run: `cd main/manager-api && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: 运行测试**

Run: `cd main/manager-api && mvn test -pl . -Dtest=PetServiceImplHatchTest,PetServiceImplAdoptTest,PetServiceImplTodayMoodTest,PetCollectionCardServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/PetServiceImpl.java main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetServiceImplHatchTest.java main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetServiceImplAdoptTest.java main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetServiceImplTodayMoodTest.java
git commit -m "refactor: PetServiceImpl.hatch uses PetCollectionCardService.createCard"
```

---

## Task 6: 前端 pet-store.js 改造

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.js`
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.test.js`

- [ ] **Step 1: 修改 savePetFromVO — 移除 collectionCard/collectionCardUrl，新增 collectionCards**

在 `savePetFromVO()` 函数中，找到包含 `collectionCardUrl` 和 `collectionCard` 的行，替换为：

移除这两行：
```javascript
collectionCardUrl: vo.collectionCardUrl || '',
```
和：
```javascript
collectionCard: isHatched ? (hasFullCard ? existing.collectionCard : buildCollectionCard(vo)) : null,
```

新增一行（放在 `avatarUrl` 之后）：
```javascript
collectionCards: vo.collectionCards || [],
```

同时移除第二处重复的 `collectionCardUrl`（约第157行）：
```javascript
collectionCardUrl: vo.collectionCardUrl || ''
```

- [ ] **Step 2: 删除 buildCollectionCard 函数**

删除整个 `buildCollectionCard(vo)` 函数（约第483-512行）。

- [ ] **Step 3: 简化 createCollectionCard 函数**

将 `createCollectionCard()` 函数替换为：

```javascript
async function createCollectionCard() {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (Date.now() < pet.hatchAt) return { ok: false, message: '还没到破壳时间' };
  if (pet.hatchStatus === 'HATCHED') return { ok: true, created: false, pet };
  try {
    const vo = await petApi.hatchPet(pet.id);
    const updated = savePetFromVO(vo);
    return { ok: true, created: true, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '破壳失败，请稍后重试' };
  }
}
```

- [ ] **Step 4: 修改 getStage 判断条件**

将 `getStage()` 中的：
```javascript
if (pet.collectionCard) return 'hatched';
```
改为：
```javascript
if (pet.hatchStatus === 'HATCHED') return 'hatched';
```

- [ ] **Step 5: 修改 getDailyStatus 引用**

将 `getDailyStatus()` 中的：
```javascript
const stagePool = pet.collectionCard ? STATUS_LINES.pet : STATUS_LINES.egg;
```
改为：
```javascript
const stagePool = pet.hatchStatus === 'HATCHED' ? STATUS_LINES.pet : STATUS_LINES.egg;
```

- [ ] **Step 6: 修改 module.exports — 移除 buildCollectionCard 导出**

在 `module.exports` 中移除 `buildCollectionCard`。

- [ ] **Step 7: 更新 pet-store.test.js**

将测试数据中的 `collectionCard` 和 `collectionCardUrl` 字段替换为 `collectionCards` 数组。例如：

```javascript
// 旧
const vo = { id: 'pet-1', collectionCardUrl: 'https://img/card.png', hatchStatus: 'HATCHED', ... };

// 新
const vo = { id: 'pet-1', collectionCards: [{ id: 'card-1', imageUrl: 'https://img/card.png', brief: 'test', source: 'HATCH', sortOrder: 0 }], hatchStatus: 'HATCHED', ... };
```

更新 `getStage` 相关测试，验证 `hatchStatus === 'HATCHED'` 判断。

- [ ] **Step 8: 运行前端测试**

Run: `cd main/egg-miniprogram && node miniprogram/utils/pet-store.test.js`
Expected: All tests pass

- [ ] **Step 9: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/pet-store.js main/egg-miniprogram/miniprogram/utils/pet-store.test.js
git commit -m "refactor: pet-store uses collectionCards array from backend"
```

---

## Task 7: 前端 home 页面

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.wxml`

- [ ] **Step 1: 修改图片 src 为首卡 imageUrl**

在 `home.wxml` 第25行，将：

```html
<image wx:if="{{stage === 'hatched'}}" class="home-pet-image" src="{{pet.petType === '锦鲤' ? 'https://oss.eggbabe.com/cards-bg/card-fish.png' : 'https://oss.eggbabe.com/cards-bg/card-rabbit.png'}}" mode="aspectFit" catchtap="onOpenProfile"></image>
```

改为：

```html
<image wx:if="{{stage === 'hatched' && pet.collectionCards.length > 0}}" class="home-pet-image" src="{{pet.collectionCards[0].imageUrl}}" mode="aspectFit" catchtap="onOpenProfile"></image>
```

- [ ] **Step 2: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/home/home.wxml
git commit -m "feat: home page uses first collection card image"
```

---

## Task 8: 前端 collection-card 详情页

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/collection-card/collection-card.js`

- [ ] **Step 1: 修改 onLoad 支持 index 参数**

将 `collection-card.js` 的 `onLoad(query)` 替换为：

```javascript
onLoad(query) {
    const pet = petStore.getPet();
    if (!pet || !pet.collectionCards || pet.collectionCards.length === 0) {
      wx.showToast({ title: '还没有破壳收藏卡', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    const index = Math.min(parseInt(query.index || '0', 10), pet.collectionCards.length - 1);
    const cardData = pet.collectionCards[index] || pet.collectionCards[0];
    const proto = pet.prototype || '玉兔';
    const card = {
      ...cardData,
      prototype: proto,
      petType: proto,
      name: pet.name || proto,
      birthday: petStore.todayKey(pet.hatchedAt),
      zodiac: pet.zodiac || '',
      gender: pet.gender || '',
      mbti: pet.mbti || '',
      bloodType: pet.bloodType || '',
      personality: cardData.brief || pet.personalityBrief || ''
    };
    this.setData({
      pet,
      card,
      cardIndex: index,
      cardTotal: pet.collectionCards.length,
      subtitle: card.style ? `${proto} · ${card.style}` : proto,
      birthdayLabel: birthdayLabel(card.birthday),
      genderLabel: genderLabel(card.gender),
      zodiacSymbol: ZODIAC_SYMBOLS[card.zodiac] || '',
      signatureClass: signatureClass(card.personality),
      isNew: query.new === '1'
    });
  },
```

- [ ] **Step 2: 添加 serial 生成（从 pet 数据派生）**

在 `onLoad` 之后添加辅助方法（如果 `cardSerial` 需要从 pet-store 引入，确保导出）：

在 `pet-store.js` 的 `module.exports` 中确保 `cardSerial` 已导出（如果未导出则添加）。在 `collection-card.js` 的 `onLoad` 中设置 card 时添加 serial：

```javascript
serial: petStore.cardSerial ? petStore.cardSerial(pet) : '',
```

> 注意：`cardSerial` 函数已存在于 `pet-store.js` 中（第431-437行），需在 `module.exports` 中添加导出。

- [ ] **Step 3: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/collection-card/collection-card.js main/egg-miniprogram/miniprogram/utils/pet-store.js
git commit -m "feat: collection-card page supports multi-card browsing by index"
```

---

## Task 9: 前端 album 卡册页

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/album/album.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/album/album.wxml`

- [ ] **Step 1: 修改 album.js — 读取 collectionCards 数组**

将 `album.js` 替换为：

```javascript
const petStore = require('../../utils/pet-store');
Page({
  data: { cards: [] },
  onShow() {
    const pet = petStore.getPet();
    const cards = (pet && pet.collectionCards) ? pet.collectionCards.map((card, index) => ({
      ...card,
      petType: pet.prototype || '玉兔',
      name: pet.name || pet.prototype || '蛋宝宝',
      index
    })) : [];
    this.setData({ cards });
  },
  onOpen(e) {
    const index = e.currentTarget.dataset.index || 0;
    wx.navigateTo({ url: `/pages/collection-card/collection-card?index=${index}` });
  }
});
```

- [ ] **Step 2: 修改 album.wxml — 多卡列表渲染**

将 `album.wxml` 替换为：

```xml
<view class="page">
  <nav-bar title="我的卡册"></nav-bar>
  <view class="content">
    <view wx:if="{{cards.length > 0}}" class="card-list">
      <view wx:for="{{cards}}" wx:key="id" class="album-card" bindtap="onOpen" data-index="{{item.index}}">
        <image wx:if="{{item.imageUrl}}" class="album-pet-avatar" src="{{item.imageUrl}}" mode="aspectFill"></image>
        <view wx:else class="baby-mark">{{item.petType === '锦鲤' ? '锦' : '兔'}}</view>
        <view class="copy">
          <text class="name">{{item.name}}</text>
          <text class="meta">{{item.brief || ''}}</text>
          <text class="source-tag">{{item.source === 'HATCH' ? '破壳首卡' : item.source}}</text>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>
    <view wx:else class="empty">
      <view class="empty-card"></view>
      <text class="empty-title">卡册还是空的</text>
      <text class="empty-desc">到达预设破壳日后，收藏卡会自动收入这里。</text>
    </view>
  </view>
</view>
```

- [ ] **Step 3: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/album/album.js main/egg-miniprogram/miniprogram/pages/album/album.wxml
git commit -m "feat: album page shows multi-card list"
```

---

## Task 10: 前端 pet-detail 宠物档案页

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/pet-detail/pet-detail.js`

- [ ] **Step 1: 修改引用从 collectionCard 到 collectionCards**

将 `pet-detail.js` 的 `onShow()` 替换为：

```javascript
onShow() {
    const pet = petStore.getPet();
    if (!pet || pet.hatchStatus !== 'HATCHED') {
      wx.showToast({ title: '破壳后才会生成档案', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    const firstCard = (pet.collectionCards && pet.collectionCards[0]) || {};
    const card = { ...firstCard, petType: pet.prototype };
    this.setData({
      pet: { ...pet, petType: pet.prototype, avatarUrl: pet.avatarUrl || '' },
      card,
      avatarUrl: pet.avatarUrl || '',
      dailyStatus: petStore.getDailyStatus()
    });
  },
```

- [ ] **Step 2: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/pet-detail/pet-detail.js
git commit -m "refactor: pet-detail uses collectionCards[0] instead of collectionCard"
```

---

## Task 11: 全量编译和测试验证

- [ ] **Step 1: 后端编译**

Run: `cd main/manager-api && mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 后端全量测试**

Run: `cd main/manager-api && mvn test -pl . -Dtest=PetServiceImplHatchTest,PetCollectionCardServiceImplTest,PetServiceImplAdoptTest,PetServiceImplTodayMoodTest,CollectionCardGenerationListenerTest,CollectionCardImageServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: All tests pass

- [ ] **Step 3: 前端测试**

Run: `cd main/egg-miniprogram && node miniprogram/utils/pet-store.test.js`
Expected: All tests pass

- [ ] **Step 4: Final commit (if any remaining changes)**

```bash
git add -A
git commit -m "test: verify all tests pass for multi-card feature"
```
