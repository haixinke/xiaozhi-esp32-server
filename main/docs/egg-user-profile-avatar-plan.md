# 蛋宝宝用户资料 + OSS 头像：可执行实施计划

> 范围：manager-api 后端（`ai_wechat_user` 扩展、profile 读写、头像 OSS 上传）+ egg-miniprogram 前端（`pages/profile` 接后端）。
> 分支：`codex/egg-v1`
> 前置确认：
> - 字段落在 `ai_wechat_user`（已有 nickname/avatar_url/phone）
> - 性别/生日不限次修改
> - 头像 OSS bucket 公开读（public URL = `https://{bucket}.{endpoint}/avatar/{userId}/{uuid}.{ext}`）
> - 用户昵称 ≤16 字符；城市自由文本；星座后端从 birthday 派生
> - 后端 + 前端一起接通

## 一、后端（manager-api）

### 1.1 Schema 迁移

文件：`main/manager-api/src/main/resources/db/changelog/202607111500.sql`

```sql
-- 微信小程序用户绑定表：新增用户资料字段
ALTER TABLE ai_wechat_user
    ADD COLUMN gender VARCHAR(8) NULL COMMENT '性别: MALE/FEMALE/OTHER' AFTER phone,
    ADD COLUMN birthday DATE NULL COMMENT '生日' AFTER gender,
    ADD COLUMN city VARCHAR(32) NULL COMMENT '常驻城市' AFTER birthday,
    ADD COLUMN mbti VARCHAR(4) NULL COMMENT 'MBTI类型' AFTER city;
```

在 `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` 末尾追加：

```yaml
  - changeSet:
      id: 202607111500
      author: minwang
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202607111500.sql
```

### 1.2 Entity / DTO / VO

**`WechatUserEntity.java`** 新增字段：

```java
private String gender;
private LocalDate birthday;
private String city;
private String mbti;
```

**新建 `WechatProfileUpdateDTO.java`**：

```java
@Data
@Schema(description = "用户资料更新请求")
public class WechatProfileUpdateDTO {
    @Size(max = 16, message = "昵称最多16个字符")
    @Schema(description = "昵称")
    private String nickname;

    @Size(max = 512, message = "头像URL过长")
    @Schema(description = "头像URL")
    private String avatarUrl;

    @Pattern(regexp = "MALE|FEMALE|OTHER", message = "性别格式错误")
    @Schema(description = "性别: MALE/FEMALE/OTHER")
    private String gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Size(max = 32, message = "城市最多32个字符")
    @Schema(description = "常驻城市")
    private String city;

    @Pattern(regexp = "INFP|INFJ|INTJ|INTP|ENFP|ENFJ|ENTJ|ENTP|ISFP|ISFJ|ISTJ|ISTP|ESFP|ESFJ|ESTJ|ESTP",
             message = "MBTI类型错误")
    @Schema(description = "MBTI类型")
    private String mbti;
}
```

**新建 `WechatProfileVO.java`**：

```java
@Data
@Schema(description = "用户资料视图")
public class WechatProfileVO {
    private String nickname;
    private String avatarUrl;
    private String gender;
    private LocalDate birthday;
    private String city;
    private String mbti;
    private String zodiac;
    private String phone;
}
```

### 1.3 错误码

在 `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java` 追加（10215-10221）：

```java
int NICKNAME_TOO_LONG = 10215;
int NICKNAME_SENSITIVE = 10216;
int INVALID_GENDER = 10217;
int INVALID_MBTI = 10218;
int INVALID_BIRTHDAY = 10219;
int AVATAR_FILE_TYPE_ERROR = 10220;
int CITY_TOO_LONG = 10221;
```

并在 `src/main/resources/i18n/messages*.properties` 补充文案：

```properties
10215=昵称最多16个字符
10216=昵称含有不适合的内容
10217=性别格式错误
10218=MBTI类型错误
10219=生日格式错误
10220=头像文件类型错误，仅支持jpg/png/webp
10221=城市最多32个字符
```

### 1.4 星座 helper

在 `main/manager-api/src/main/java/xiaozhi/modules/pet/util/PetBirthCalculator.java` 新增：

```java
private static final String[] ZODIAC_CN = {
    "摩羯座", "水瓶座", "双鱼座", "白羊座", "金牛座", "双子座",
    "巨蟹座", "狮子座", "处女座", "天秤座", "天蝎座", "射手座", "摩羯座"
};
private static final int[] ZODIAC_SPLIT = { 20, 19, 21, 20, 21, 22, 23, 23, 23, 24, 23, 22, 22 };

public static String zodiacOf(LocalDate date) {
    if (date == null) return null;
    int month = date.getMonthValue();
    int day = date.getDayOfMonth();
    int index = month - 1;
    if (day >= ZODIAC_SPLIT[index]) index++;
    return ZODIAC_CN[index];
}
```

### 1.5 轻量校验工具

新建 `main/manager-api/src/main/java/xiaozhi/modules/wechat/util/ProfileValidator.java`：

```java
public final class ProfileValidator {
    private static final Set<String> MBTI_SET = Set.of(
        "INFP","INFJ","INTJ","INTP","ENFP","ENFJ","ENTJ","ENTP",
        "ISFP","ISFJ","ISTJ","ISTP","ESFP","ESFJ","ESTJ","ESTP");
    private static final Set<String> GENDER_SET = Set.of("MALE", "FEMALE", "OTHER");
    private static final Set<String> SENSITIVE = Set.of("违法", "诈骗", "赌博", "色情", "暴力");

    public static void validate(WechatProfileUpdateDTO dto) {
        if (StringUtils.isNotBlank(dto.getNickname())) validateNickname(dto.getNickname());
        if (StringUtils.isNotBlank(dto.getGender())) validateGender(dto.getGender());
        if (StringUtils.isNotBlank(dto.getMbti())) validateMbti(dto.getMbti());
        if (StringUtils.isNotBlank(dto.getCity())) validateCity(dto.getCity());
    }

    private static void validateNickname(String nickname) { ... }
    private static void validateGender(String gender) { ... }
    private static void validateMbti(String mbti) { ... }
    private static void validateCity(String city) { ... }
}
```

### 1.6 Service 实现

在 `WechatService.java` 增加：

```java
WechatProfileVO getProfile(Long userId);
void updateProfile(Long userId, WechatProfileUpdateDTO dto);
String uploadAvatar(Long userId, MultipartFile file);
```

在 `WechatServiceImpl.java` 实现：

- `getProfile(userId)`：按 `userId` 查 `ai_wechat_user`，转 VO，调用 `PetBirthCalculator.zodiacOf(birthday)`，手机号脱敏。
- `updateProfile(userId, dto)`：
  - 校验 userId 非空
  - `ProfileValidator.validate(dto)`
  - `UpdateWrapper<WechatUserEntity>` 只更新 DTO 中提供的非空字段
- `uploadAvatar(userId, file)`：
  - 校验 file 非空、≤2MB、contentType ∈ {image/jpeg, image/png, image/webp}
  - `OssService.isEnabled()` 否则抛 `OSS_UPLOAD_FILE_ERROR`
  - 生成 uuid 和 ossKey = `avatar/{userId}/{uuid}.{ext}`
  - `ossService.upload(ossKey, file.getBytes())`
  - 返回 `https://{bucket}.{cleanEndpoint}/{ossKey}`（strip endpoint scheme）

### 1.7 Controller

在 `WechatController.java` 增加：

```java
@GetMapping("/profile")
@Operation(summary = "查询当前用户资料")
@RequiresPermissions("sys:role:normal")
public Result<WechatProfileVO> getProfile() {
    Long userId = SecurityUser.getUserId();
    return new Result<WechatProfileVO>().ok(wechatService.getProfile(userId));
}

@PutMapping("/profile")
@Operation(summary = "更新当前用户资料")
@RequiresPermissions("sys:role:normal")
public Result<Void> updateProfile(@RequestBody @Valid WechatProfileUpdateDTO dto) {
    Long userId = SecurityUser.getUserId();
    wechatService.updateProfile(userId, dto);
    return new Result<>();
}

@PostMapping("/avatar")
@Operation(summary = "上传头像到OSS")
@RequiresPermissions("sys:role:normal")
public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
    Long userId = SecurityUser.getUserId();
    return new Result<String>().ok(wechatService.uploadAvatar(userId, file));
}
```

### 1.8 测试

新建 `main/manager-api/src/test/java/xiaozhi/modules/wechat/service/impl/WechatServiceImplProfileTest.java`：

- `getProfile_success_returnsMaskedPhoneAndZodiac`
- `getProfile_notLogin_throwsUserNotLogin`
- `updateProfile_success_updatesOnlyProvidedFields`
- `updateProfile_nicknameTooLong_throws`
- `updateProfile_invalidMbti_throws`
- `uploadAvatar_success_returnsPublicUrl`
- `uploadAvatar_ossDisabled_throws`
- `uploadAvatar_invalidFileType_throws`

新建 `WechatProfileUpdateDTOTest.java` 做 Bean Validation 边界测试。

---

## 二、前端（egg-miniprogram）

### 2.1 请求工具

确保 `utils/request.js` 已能发送 GET/PUT/POST + Bearer token + 401 静默刷新。示例调用：

```js
import request from '../../utils/request';

request.get('/wechat/profile').then(res => ...)
request.put('/wechat/profile', { nickname: 'xxx' })
wx.uploadFile({
  url: API_BASE_URL + '/wechat/avatar',
  filePath: tempAvatarPath,
  name: 'file',
  header: { Authorization: 'Bearer ' + token }
})
```

### 2.2 pet-store.js

新增/复用一个 helper，把后端 profile 的 nickname/avatarUrl 同步到 `USER_KEY`：

```js
function syncUserProfile(profile) {
  if (!profile) return;
  const user = getUser() || {};
  if (profile.nickname !== undefined) user.nickname = profile.nickname;
  if (profile.avatarUrl !== undefined) user.avatarUrl = profile.avatarUrl;
  saveUser(user);
}
```

### 2.3 pages/profile/profile.js

- `onLoad`：调 `GET /wechat/profile`，写入 `PROFILE_KEY` 和 `pet-store` 的 user。
- `onChooseAvatar(e)`：拿到 `e.detail.avatarUrl` 临时路径后，用 `wx.uploadFile` 调 `POST /wechat/avatar`，成功后把 URL 保存到 profile 并调 `PUT /wechat/profile`。
- `onEditNickname`：showModal 编辑，trim + slice(0,16)，调 `saveProfile({ nickname })`。
- `onEditGender`：action sheet ['男','女','其他'] → 映射 `MALE/FEMALE/OTHER`。
- `onEditBirthday`：改用 `picker mode="date"` 选日期，调 `saveProfile({ birthday })`，成功后刷新 profile 以更新后端派生 zodiac。
- `onEditCity`：action sheet 常见城市 + '其他'（其他转可输入 modal）。
- `onEditMbti`：action sheet MBTI_LIST。
- `saveProfile(partial)`：调 `PUT /wechat/profile`；成功后更新本地缓存并同步 user。

### 2.4 pages/profile/profile.wxml

- 移除"性别设置后不可修改"、"生日设置后不可修改"的锁定提示。
- 生日项改为 `picker mode="date"`。
- 城市项保留 list-row，点击后支持自由输入/选择。

---

## 三、文档

### 3.1 manager-api/CLAUDE.md

在"业务文档"列表追加：

```markdown
- [蛋宝宝用户资料与头像 OSS 上传](docs/egg-user-profile-avatar.md) — `GET/PUT /wechat/profile`、`POST /wechat/avatar`，`ai_wechat_user` 画像字段，阿里云 OSS 头像公开 URL。
```

### 3.2 egg-miniprogram/CLAUDE.md

在"1. 微信注册 / 登录"小节后补充：

```markdown
- `GET /wechat/profile`：查询当前用户资料（昵称/头像/性别/生日/城市/MBTI/星座/脱敏手机号）
- `PUT /wechat/profile`：更新当前用户资料（字段全可选，部分更新）
- `POST /wechat/avatar`：上传头像到阿里云 OSS，返回公开 URL
```

### 3.3 新建后端参考文档

新建 `main/manager-api/docs/egg-user-profile-avatar.md`，记录：
- 数据模型（`ai_wechat_user` 字段）
- 三个端点契约
- OSS URL 公式
- 校验规则
- 前端调用示例
- 错误码表

---

## 四、执行清单（按顺序）

- [ ] 1. 后端：schema 迁移文件 + changelog 注册
- [ ] 2. 后端：`WechatUserEntity` 加字段
- [ ] 3. 后端：`WechatProfileUpdateDTO` / `WechatProfileVO` / `ProfileValidator`
- [ ] 4. 后端：ErrorCode + i18n 文案
- [ ] 5. 后端：`PetBirthCalculator.zodiacOf(LocalDate)`
- [ ] 6. 后端：`WechatService` / `impl` 实现 get/update/upload
- [ ] 7. 后端：`WechatController` 三个端点
- [ ] 8. 后端：单元测试
- [ ] 9. 后端：`mvn compile` / `mvn test`（仅 wechat 相关或全量）
- [ ] 10. 前端：`pet-store.js` 同步 profile helper
- [ ] 11. 前端：`pages/profile/profile.js` 接后端
- [ ] 12. 前端：`pages/profile/profile.wxml` 移除锁定、改用 date picker
- [ ] 13. 前端：`node --check` + `verify-project.js`
- [ ] 14. 文档：三个 CLAUDE.md + 新建 `egg-user-profile-avatar.md`
- [ ] 15. 本地联调：后端启动后，前端调 GET/PUT /wechat/profile 和 POST /wechat/avatar
- [ ] 16. 提交（单批次或多批次，按之前约定）

---

## 五、关键风险

| 风险 | 说明 | 处置 |
|---|---|---|
| OSS 未配置 | dev 环境 `aliyun.oss.*` 未填时 `uploadAvatar` 会抛 10024 | 文本资料更新不受影响；测试用 mock OssService |
| bucket 非公读 | 公开 URL 会 403 | 需 bucket policy 对 `avatar/*` 允许 `GetObject`；或后续接 CDN |
| 微信 chooseAvatar 临时文件 | 路径只在当前小程序会话有效 | 上传动作紧跟 chooseAvatar，不缓存临时路径 |
| 用户头像与宠物头像混淆 | 用户头像存 OSS；破壳宠物头像仍用 `pet.avatar.koi/rabbit` 配置池 | 字段/端点分离，文档中明确区分 |

---

*生成时间：2026-07-11。执行前请确认 `aliyun.oss.*` 配置已在对应环境就绪。*
