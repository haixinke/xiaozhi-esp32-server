# 02: 新增小物件（类型下拉 + 图片上传 + 故事）

**What to build:** 运营点"新增小物件"打开弹窗，类型下拉只列出尚未被任何未删除物件占用的字典小类；运营上传一张 png/jpeg/webp（2MB 内）图片并看到预览，填写最长 2000 字的故事（带字数统计），保存后弹窗关闭、列表出现新记录。若类型已被占用、类型不在字典中、图片缺失、故事为空或超长，保存被拒绝并给出准确提示。图片上传走独立的 `small-object` 场景，OSS 路径与游戏图标、涂鸦作品隔离。本片只实现新增；编辑已有物件的回填与改类型由后续票实现。

**Blocked by:** 01

**Status:** resolved

- [x] 上传场景枚举追加 `small-object`：允许 png/jpeg/webp，单文件上限 2MB，**不允许 svg**（不复用游戏图标场景，既有扩展名映射也不支持 svg，复用会产出缺扩展名的 OSS key）
- [x] 新增"可选物件类型"接口，返回每个字典小类的 key、显示名、是否已被占用；占用判断由后端统计未删除记录得出（前端分页视角看不到跨页占用）
- [x] 新增保存接口与服务端校验：类型必须在 `SMALL_OBJECT_TYPE` 字典中存在、同一类型在未删除记录中只允许一条、图片非空、故事非空且长度不超过 2000
- [x] 错误码新增"物件类型不存在"与"该类型已被占用"两项（"物件不存在"已由 01 引入），并同步到全部七套 messages 文案（含 `messages.properties` 基包）；DTO 校验文案同步到全部七套 validation 文案
- [x] 弹窗内类型下拉数据来源是"可选物件类型"接口，不是字典全量列表（字典中混有固件类型、手机区号等非物件类型）
- [x] 弹窗内图片上传走自定义上传以携带鉴权 token，上传场景为 `small-object`，上传成功后显示预览
- [x] 弹窗内故事输入框为纯文本多行输入，最长 2000 字，显示字数统计
- [x] 提交前做前端提示（类型必选、图片必传、故事必填且不超长），后端仍完整校验
- [x] Service 集成测试：保存成功（图片与故事正确落库）、同类型第二次保存被拒绝、类型不在字典被拒绝、故事超长被拒绝

## Comments

**2026-10-27 实现（RALPH）**

- 上传场景：`UploadScene.SMALL_OBJECT("small-object", {png,jpeg,webp}, 2MB)`，不收 svg（`extensionOf` 无 svg 映射，放开会产出缺扩展名的 OSS key）。未复用 `game-icon`，路径按 `small-object/{userId}/` 与游戏图标、涂鸦隔离。
- 新接口 `GET /admin/small-object/object-types`：返回 `[{key, name, used}]`，`used` 由后端 `SELECT DISTINCT object_type ... WHERE deleted = 0` 统计得出（前端分页看不到跨页占用）。顺序沿用字典 `sort`。
- 新接口 `POST /admin/small-object/save`：DTO 注解做必填/长度（七套 validation 文案），Service 再完整校验：类型在 `SMALL_OBJECT_TYPE` 字典中存在（10361）、同类型在未删除记录中唯一（10362）、图片与故事非空、故事 ≤ 2000（含边界 2000 允许）。新增默认 `status=1`、`deleted=0`。
- 错误码 10361「物件类型不存在」、10362「该物件类型已被占用」，已同步全部七套 `messages_*.properties`（含基包）。
- 前端：`smallObject.js` 增 `getObjectTypes`/`saveSmallObject`/`uploadImage`（scene 拼 URL query 不走 FormData，`header({})` 交给浏览器生成 multipart boundary）；页面新增弹窗：类型下拉取自 `object-types` 且过滤 `used=true`，图片走 `el-upload http-request` 自定义上传并显示预览，故事 textarea `maxlength=2000` + `show-word-limit`，提交前四项提示。
- 测试：`SmallObjectServiceTest` 新增 11 例（保存成功落库、重复类型、类型不在字典、故事 2001 被拒、2000 边界通过、空图片、空故事、object-types 三项占用判定与外域隔离）；`ImageUploadServiceTest` 新增 3 例（场景白名单/上限/pathPrefix、上传路径、svg 拒绝）。校验失败均断言未落库。
- 验证：`mvn ... clean verify` 1002 tests / 0 failures / BUILD SUCCESS；`manager-web` test:unit 135 pass、check:i18n 6×1554、check:template 均通过。
