# 蛋宝宝用户资料与头像 OSS 上传

> 适用范围：蛋宝宝小程序（`egg-miniprogram`）用户个人信息页。
> 涉及端点：`GET /wechat/profile`、`PUT /wechat/profile`、`POST /wechat/avatar`。

## 数据模型

`ai_wechat_user` 表扩展字段（changeset `202607111500`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `gender` | VARCHAR(8) | `MALE` / `FEMALE` / `OTHER` |
| `birthday` | DATE | 公历生日 |
| `city` | VARCHAR(32) | 常驻城市 |
| `mbti` | VARCHAR(4) | 16 型人格，如 `INFP` |

原有字段继续保留：`nickname`、`avatar_url`、`phone`、`openid`、`user_id`。

## 端点契约

### GET /wechat/profile

查询当前登录用户资料。

**鉴权**：`Authorization: Bearer <token>`，权限 `sys:role:normal`。

响应 `Result<WechatProfileVO>`：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "nickname": "蛋友",
    "avatarUrl": "https://example.com/a.png",
    "gender": "MALE",
    "birthday": "2000-01-01",
    "city": "上海",
    "mbti": "INFP",
    "zodiac": "摩羯座",
    "phone": "138****8000"
  }
}
```

说明：
- `zodiac` 由后端根据 `birthday` 派生。
- `phone` 为脱敏手机号；未绑定手机号时返回空字符串或 `null`。

### PUT /wechat/profile

部分更新用户资料，所有字段可选。

**鉴权**：`Authorization: Bearer <token>`，权限 `sys:role:normal`。

请求体 `WechatProfileUpdateDTO`：

```json
{
  "nickname": "新昵称",
  "avatarUrl": "https://bucket.endpoint/avatar/7/uuid.png",
  "gender": "FEMALE",
  "birthday": "2000-06-01",
  "city": "北京",
  "mbti": "ENTP"
}
```

校验规则：
- `nickname` ≤ 16 字符，且不能包含敏感词（`违法`、`诈骗`、`赌博`、`色情`、`暴力`）。
- `gender` 必须是 `MALE|FEMALE|OTHER`。
- `mbti` 必须是 16 型之一。
- `city` ≤ 32 字符。
- `birthday` 为合法 `yyyy-MM-dd` 日期。

响应 `Result<Void>`：

```json
{ "code": 0, "msg": "success", "data": null }
```

### POST /wechat/avatar

上传头像到阿里云 OSS，返回公开访问 URL。

**鉴权**：`Authorization: Bearer <token>`，权限 `sys:role:normal`。

请求：multipart/form-data，字段名 `file`。

限制：
- 文件类型：`image/jpeg`、`image/png`、`image/webp`
- 文件大小：≤ 2MB

响应 `Result<String>`：

```json
{
  "code": 0,
  "msg": "success",
  "data": "https://{bucket}.{endpoint}/avatar/{userId}/{uuid}.png"
}
```

## OSS URL 公式

公开读 URL 格式：

```text
https://{bucket}.{cleanEndpoint}/avatar/{userId}/{uuid}.{ext}
```

- `cleanEndpoint`：去掉 `https://` 或 `http://` 的 endpoint。
- `ext`：按 content type 映射为 `jpg`、`png`、`webp`。
- 例：`https://my-bucket.oss-cn-shanghai.aliyuncs.com/avatar/7/436aa0ad80c8443fab12be588ba9f68e.png`

> 需保证 bucket 对 `avatar/*` 允许 `GetObject` 公开读取；生产环境可再套 CDN。

## 前端调用示例

### 查询资料

```js
import request from '../../utils/request';

request.get('/wechat/profile').then(profile => {
  console.log(profile.nickname, profile.zodiac);
});
```

### 更新资料

```js
request.put('/wechat/profile', { nickname: '新昵称', city: '上海' });
```

### 上传头像

```js
import { API_BASE_URL } from '../../config/api';
import auth from '../../utils/auth';

wx.uploadFile({
  url: `${API_BASE_URL}/wechat/avatar`,
  filePath: tempAvatarPath, // chooseAvatar 临时路径
  name: 'file',
  header: { Authorization: `Bearer ${auth.getSession().token}` },
  success: (res) => {
    const envelope = JSON.parse(res.data);
    if (envelope.code === 0) {
      request.put('/wechat/profile', { avatarUrl: envelope.data });
    }
  }
});
```

## 错误码

| 错误码 | 说明 |
|---|---|
| 10024 | OSS 上传失败（OSS 未配置或服务异常） |
| 10204 | 文件大小超过限制（头像 > 2MB 也会复用此码） |
| 10215 | 昵称最多 16 个字符 |
| 10216 | 昵称含有不适合的内容 |
| 10217 | 性别格式错误 |
| 10218 | MBTI 类型错误 |
| 10219 | 生日格式错误 |
| 10220 | 头像文件类型错误，仅支持 jpg/png/webp |
| 10221 | 城市最多 32 个字符 |

## 注意事项

- 用户头像与宠物头像分离：用户头像走 `POST /wechat/avatar` 存 OSS；宠物破壳头像仍由后端从 `pet.avatar.koi/rabbit` 配置池随机分配。
- 微信 `chooseAvatar` 返回的临时文件路径仅在当前小程序会话有效，应在上传头像时立即调用 `wx.uploadFile`。
- dev 环境若未配置 `aliyun.oss.*`，`POST /wechat/avatar` 会返回 10024；文本资料更新不受影响。
