# Code Review: OSS 音频存储迁移

**Reviewed**: 2026-06-22
**Branch**: f-mini-voice
**Decision**: REQUEST CHANGES

## Summary

本次改动实现了将聊天音频从 OceanBase LONGBLOB 迁移到阿里云 OSS 的核心能力。整体结构符合规范：新增 OSS SDK 依赖、配置属性类、OSS Client Bean、OssService 封装、实体/oss_key 字段、Liquibase 迁移和错误码/i18n。构建验证通过。

但存在 **2 个 HIGH 问题** 必须修复后才能合并：越南语/繁体中文国际化文件格式损坏（导致错误码无法解析），以及删除逻辑没有按 spec 查询数据库中的实际 `oss_key` 而是根据 audioId 重建路径。另外新增代码缺少单元测试，不符合项目 80% 覆盖率和 TDD 要求。

## Findings

### CRITICAL

无。

### HIGH

#### 1. 越南语/繁体中文 i18n 文件格式损坏

- **文件**: `main/manager-api/src/main/resources/i18n/messages_vi_VN.properties:213`
- **文件**: `main/manager-api/src/main/resources/i18n/messages_zh_TW.properties:213`
- **问题**: 新增的 `10400` 错误码被追加到 `10204` 所在行的末尾，缺少换行。
  - vi_VN: `10204=Kích thước tệp vượt quá 1MB10400=Tải tệp từ OSS thất bại`
  - zh_TW: `10204=檔案大小超過1MB限制10400=OSS下載檔案失敗`
- **影响**: `10204` 的实际值包含 `10400=...`，`10400` 和 `10401` 这两个键无法被正确解析，OSS 下载/删除错误消息会失效或返回 key。
- **修复**: 在这两个文件中为 `10400` 前面添加换行，确保每条键值独占一行。

#### 2. 删除逻辑未按 spec 查询实际 `oss_key`

- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatHistoryServiceImpl.java:110-112`
- **问题**: Spec Task 8 要求：
  1. 查询待删除音频的 `oss_key` 列表；
  2. 调用 `ossService.deleteBatch(ossKeys)`；
  3. 再删除 DB 记录。

  当前实现使用 `dataList.stream().map(OssService::buildAudioOssKey)` 根据 `audioId` 重建路径，而不是从 `ai_agent_chat_audio` 表中读取 `oss_key`。
- **影响**:
  - 若以后路径格式变化，OSS 对象将无法清理。
  - 对于旧 BLOB 数据（`oss_key` 为 NULL）也会尝试删除一个不存在的 OSS 对象。
  - 如果 `audio_id` 在 `ai_agent_chat_audio` 中不存在（历史脏数据），可能误删同名路径的其他对象。
- **修复**: 按 spec 在 `AiAgentChatAudioDao.java` 和 `mapper/agent/AiAgentChatHistoryDao.xml`（或新增 `AiAgentChatAudioDao.xml`）中添加 `getOssKeysByAudioIds(List<String> audioIds)`，过滤掉 NULL 值后再调用 `deleteBatch`。

#### 3. 新增代码缺少单元测试

- **文件**: `main/manager-api/src/main/java/xiaozhi/common/oss/OssService.java`
- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatAudioServiceImpl.java`
- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatHistoryServiceImpl.java`
- **问题**: 项目规则要求 TDD 和 80% 覆盖率。本次新增的 OSS 上传/下载/删除、OSS 失败回退 BLOB、删除时同步清理 OSS 等逻辑均没有对应测试。
- **修复**: 至少补充以下测试：
  - `OssServiceTest`: `isEnabled` 在 client 为 null / 配置不完整 / 完整时的行为；`upload`/`download`/`delete` 调用 OSS client；`deleteBatch` 按 1000 分批；`buildAudioOssKey` 路径格式。
  - `AgentChatAudioServiceImplTest`: OSS 启用时先存 DB、上传 OSS、更新 oss_key；OSS 上传失败时回退到 BLOB；OSS 未启用时直接存 BLOB；优先从 OSS 读取并回退 BLOB。
  - `AgentChatHistoryServiceImplTest`: `deleteByAgentId` 时调用 `ossService.deleteBatch`（使用查询到的 oss_key），并在 OSS 删除失败时记录日志。

### MEDIUM

#### 4. `isConfigured()` 未校验 `region`

- **文件**: `main/manager-api/src/main/java/xiaozhi/common/config/AliyunOssProperties.java:30-32`
- **问题**: V4 签名必须携带 region。当前 `isConfigured()` 只检查 `endpoint`、`accessKeyId`、`accessKeySecret`、`bucketName`，未检查 `region`。
- **影响**: 若只配置了前四项而缺少 `region`，`isEnabled()` 会返回 true，`AliyunOssConfig` 会创建 client，但实际请求可能失败。
- **修复**: 将 `region` 加入 `isConfigured()` 的非空校验。

#### 5. OSS 操作方法缺少入参校验

- **文件**: `main/manager-api/src/main/java/xiaozhi/common/oss/OssService.java:51, 65, 79`
- **问题**: `upload(String ossKey, byte[] data)`、`download(String ossKey)`、`delete(String ossKey)` 未对 `ossKey` 和 `data` 做 null/空校验。
- **影响**: 传入 null 时会在 OSS SDK 内部抛出难以定位的异常（如 `ByteArrayInputStream` NPE）。
- **修复**: 在方法开头使用 `Assert.hasText(ossKey, ...)` / `Assert.notNull(data, ...)` 校验，或抛出自定义业务异常。

#### 6. OSS 下载失败直接返回 null，未回退 BLOB

- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatAudioServiceImpl.java:66-70`
- **问题**: 当 `ossKey` 存在但 OSS 下载失败时，`getAudio` 捕获异常并返回 `null`。
- **影响**: 若 OSS 临时不可用或对象丢失，原本可以从 BLOB 读取的旧数据也无法播放（虽然新数据 BLOB 为 null，但旧数据仍有 BLOB）。
- **修复**: 在 catch 块中增加日志后回退到 `entity.getAudio()`，或根据业务需求决定；如需严格按 spec 执行，至少应在注释中说明设计选择。

#### 7. `deleteByAgentId` 在 OSS 删除失败后仍删除 DB 记录

- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatHistoryServiceImpl.java:114-116`
- **问题**: OSS 删除异常被捕获并仅记录 warn，随后继续删除 DB 记录。
- **影响**: DB 记录被删除后，OSS 中可能留下孤儿对象。
- **修复**: 根据业务选择：
  - 若要求强一致，应让异常向上抛出，使事务回滚，记录失败原因后再人工处理；
  - 若允许最终一致，至少应在日志/监控中显式标记需清理的 oss_key，并提供补偿机制。

#### 8. 上传音频缺少大小/内容校验

- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatAudioServiceImpl.java:30`
- **问题**: `saveAudio(byte[] audioData)` 直接上传任意字节数组，未校验空数据、大小限制或内容类型。
- **影响**: 空数据会写入 OSS 0 字节对象；极大文件会占用 OSS 空间和带宽。
- **修复**: 增加空检查与合理大小上限（可复用 `application.yml` 中的配置），必要时校验 WAV 头。

### LOW

#### 9. JAXB 使用 `javax` 命名空间与 Spring Boot 3 的 Jakarta EE 9 不完全一致

- **文件**: `main/manager-api/pom.xml:265-279`
- **问题**: Spring Boot 3.4.3 基于 Jakarta EE 9+，而新增的 JAXB 依赖是 `javax.xml.bind` 命名空间。
- **影响**: 当前构建通过，但可能在运行时出现类加载或模块路径问题。
- **修复**: 确认 `aliyun-sdk-oss:3.17.4` 是否兼容 Jakarta；如后续出现 `ClassNotFoundException: javax.xml.bind.JAXBException`，可改用 `jakarta.xml.bind-api` 和对应 runtime。

#### 10. 异常捕获过于宽泛

- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatAudioServiceImpl.java:42, 67`
- **问题**: `catch (Exception e)` 捕获所有异常。
- **影响**: 可能会吞掉需要立即处理的编程错误。
- **修复**: 区分 OSS 特定异常（如 `OSSException`、`ClientException`）与数据库异常，DB 异常不应被静默回退。

## Validation Results

| Check | Result |
|---|---|
| Compile | Pass |
| Package (`mvn clean package -DskipTests`) | Pass |
| Tests (`mvn test -DskipTests=false`) | Fail (20 errors, environment-related) |

### 测试失败说明

测试失败的原因是 Spring Boot 上下文无法启动：`Cannot resolve reference to bean 'sqlSessionTemplate'`，根本原因是本地未启动 MySQL/OceanBase 和 Redis，导致 Druid 数据源初始化失败。这些错误与本次 OSS 改动无关。

## Files Reviewed

- Modified:
  - `main/manager-api/pom.xml`
  - `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java`
  - `main/manager-api/src/main/java/xiaozhi/modules/agent/entity/AgentChatAudioEntity.java`
  - `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatAudioServiceImpl.java`
  - `main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatHistoryServiceImpl.java`
  - `main/manager-api/src/main/resources/application-dev.yml`
  - `main/manager-api/src/main/resources/application-prod.yml`
  - `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`
  - `main/manager-api/src/main/resources/i18n/messages.properties`
  - `main/manager-api/src/main/resources/i18n/messages_de_DE.properties`
  - `main/manager-api/src/main/resources/i18n/messages_en_US.properties`
  - `main/manager-api/src/main/resources/i18n/messages_pt_BR.properties`
  - `main/manager-api/src/main/resources/i18n/messages_vi_VN.properties`
  - `main/manager-api/src/main/resources/i18n/messages_zh_CN.properties`
  - `main/manager-api/src/main/resources/i18n/messages_zh_TW.properties`
- Added:
  - `main/manager-api/src/main/java/xiaozhi/common/config/AliyunOssConfig.java`
  - `main/manager-api/src/main/java/xiaozhi/common/config/AliyunOssProperties.java`
  - `main/manager-api/src/main/java/xiaozhi/common/oss/OssService.java`
  - `main/manager-api/src/main/resources/db/changelog/202506221800.sql`

## Next Steps

1. 修复 `messages_vi_VN.properties` 和 `messages_zh_TW.properties` 中 `10400`/`10401` 的换行问题。
2. 按 spec 实现 `getOssKeysByAudioIds` 并在 `deleteByAgentId` 中查询真实 `oss_key` 后删除 OSS 对象。
3. 为 `OssService`、`AgentChatAudioServiceImpl`、`AgentChatHistoryServiceImpl` 补充单元测试。
4. 将 `region` 加入 `AliyunOssProperties.isConfigured()` 校验。
5. 建议为 OSS 操作方法增加入参校验，并考虑 OSS 下载失败时回退到 BLOB。
6. 重新运行 `mvn clean package -DskipTests` 和补充的单元测试。

---
Review artifact generated by code-review skill.
