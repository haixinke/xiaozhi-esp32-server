# 音频存储迁移：OceanBase LONGBLOB → 阿里云 OSS

## Context

当前聊天音频以 LONGBLOB 存储在 OceanBase `ai_agent_chat_audio` 表中，随数据量增长会导致数据库膨胀、备份困难、性能下降。迁移到阿里云 OSS 可大幅降低存储成本并提升扩展性。用户确认存量 BLOB 数据可丢弃，仅需保证新数据写入 OSS。

---

## Task 1: 添加阿里云 OSS SDK 依赖

**文件**: `main/manager-api/pom.xml`

在 `<dependencies>` 中添加：
```xml
<!-- 阿里云OSS SDK -->
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
</dependency>
<!-- Java 9+ 需要 JAXB 依赖 -->
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
    <version>2.3.1</version>
</dependency>
<dependency>
    <groupId>javax.activation</groupId>
    <artifactId>activation</artifactId>
    <version>1.1.1</version>
</dependency>
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
    <version>2.3.3</version>
</dependency>
```

---

## Task 2: 新建 OSS 配置属性类

**新建文件**: `src/main/java/xiaozhi/common/config/AliyunOssProperties.java`

- 使用 `@ConfigurationProperties(prefix = "aliyun.oss")` 绑定配置
- 包含字段：`endpoint`, `accessKeyId`, `accessKeySecret`, `bucketName`, `region`
- 提供 `isConfigured()` 方法判断 OSS 是否配置完整

---

## Task 3: 新建 OSS Client 配置类

**新建文件**: `src/main/java/xiaozhi/common/config/AliyunOssConfig.java`

- 使用 `@ConditionalOnProperty(prefix = "aliyun.oss", name = "endpoint")` 条件加载
- 创建 `OSS` Bean（使用 V4 签名）
- 实现 `DisposableBean` 或 `@PreDestroy` 确保 client 优雅关闭

---

## Task 4: 新建 OssService 封装类

**新建文件**: `src/main/java/xiaozhi/common/oss/OssService.java`

功能：
- `isEnabled()` - 判断 OSS 是否可用
- `upload(String ossKey, byte[] data)` - 上传字节数组
- `download(String ossKey)` - 下载为字节数组
- `delete(String ossKey)` - 删除单个对象
- `deleteBatch(List<String> ossKeys)` - 批量删除（每批最多1000个）
- `static buildAudioOssKey(String audioId)` - 构造路径: `chat-audio/{audioId}.wav`

---

## Task 5: 添加 YAML 配置

**修改**: `src/main/resources/application-prod.yml` 添加：
```yaml
aliyun:
  oss:
    endpoint: ${ALIYUN_OSS_ENDPOINT:}
    access-key-id: ${ALIYUN_OSS_ACCESS_KEY_ID:}
    access-key-secret: ${ALIYUN_OSS_ACCESS_KEY_SECRET:}
    bucket-name: ${ALIYUN_OSS_BUCKET_NAME:}
    region: ${ALIYUN_OSS_REGION:}
```

**修改**: `src/main/resources/application-dev.yml` 添加同样结构（值留空）

---

## Task 6: 修改 Entity 添加 ossKey 字段

**修改**: `src/main/java/xiaozhi/modules/agent/entity/AgentChatAudioEntity.java`

- 新增 `private String ossKey;` 字段，映射 `oss_key` 列
- 保留 `byte[] audio` 字段（兼容旧数据读取）

---

## Task 7: 重写 AgentChatAudioServiceImpl

**修改**: `src/main/java/xiaozhi/modules/agent/service/impl/AgentChatAudioServiceImpl.java`

核心逻辑：
- **saveAudio**: 若 OSS 可用 → 先 save 获取 UUID → 上传到 OSS → updateById 写入 ossKey（audio 字段为 null）；若 OSS 上传失败或未配置 → 回退到 BLOB
- **getAudio**: 优先读 `ossKey` 从 OSS 下载 → 若无 ossKey 则从 `audio` BLOB 读取

注入 `OssService`（通过构造函数）。

---

## Task 8: 修改删除逻辑 - 同步删除 OSS 对象

**修改**: `src/main/java/xiaozhi/modules/agent/service/impl/AgentChatHistoryServiceImpl.java`

在 `deleteByAgentId` 方法中，删除 DB 记录前：
1. 查询待删除音频的 ossKey 列表
2. 调用 `ossService.deleteBatch(ossKeys)` 从 OSS 删除对象
3. 再删除 DB 记录

需要注入 `OssService`，并在 DAO 中新增查询 ossKey 的方法。

**修改**: `src/main/resources/mapper/agent/AiAgentChatHistoryDao.xml`

新增 SQL：
```sql
<select id="getOssKeysByAudioIds" resultType="java.lang.String">
    SELECT oss_key FROM ai_agent_chat_audio
    WHERE oss_key IS NOT NULL AND id IN
    <foreach collection="audioIds" item="id" open="(" separator="," close=")">
      #{id}
    </foreach>
</select>
```

**修改**: `src/main/java/xiaozhi/modules/agent/dao/AiAgentChatAudioDao.java` 添加方法签名。

---

## Task 9: 新增 Liquibase 数据库迁移

**新建文件**: `src/main/resources/db/changelog/202506221800.sql`

```sql
ALTER TABLE ai_agent_chat_audio
ADD COLUMN oss_key VARCHAR(256) DEFAULT NULL COMMENT 'OSS对象存储路径' AFTER audio;
```

**修改**: `src/main/resources/db/changelog/db.changelog-master.yaml` 追加 changeSet 条目。

---

## Task 10: 新增错误码与国际化

**修改**: `src/main/java/xiaozhi/common/exception/ErrorCode.java`
- 新增 `int OSS_DOWNLOAD_FILE_ERROR = 10400;`
- 新增 `int OSS_DELETE_FILE_ERROR = 10401;`

**修改**: 7个 `messages*.properties` 文件添加对应错误消息。

---

## 不变更的文件

| 文件 | 原因 |
|------|------|
| `AgentChatAudioService.java` | 接口签名 `saveAudio(byte[])` / `getAudio(String)` 不变 |
| `AgentController.java` | 调用方不变 |
| `AgentChatHistoryBizServiceImpl.java` | 调用方不变 |
| `AgentVoicePrintServiceImpl.java` | 调用方不变 |
| Python 端所有文件 | 上报协议不变，仍发送 Base64 WAV |

---

## OSS Bucket 规范

- **Object Key 格式**: `chat-audio/{audioId}.wav`
- **Bucket 访问权限**: 私有（private），通过 manager-api 代理访问
- **存储格式**: WAV（与当前一致，声纹服务和播放 API 无需适配）

---

## 验证

1. **不配 OSS 环境变量启动**: 确认行为与改造前一致（BLOB 存储）
2. **配置 OSS 启动**: 发起设备对话 → 检查 OSS bucket 出现 `chat-audio/{id}.wav`
3. **播放新音频**: 管理后台点击播放，确认正常回放
4. **播放旧音频**: 旧 BLOB 数据仍可正常播放（ossKey 为空时回退读 BLOB）
5. **删除智能体**: 确认 OSS 对象被清理
6. **声纹注册**: 使用新音频注册声纹，确认正常工作
7. **构建验证**: `mvn clean package -DskipTests` 通过
