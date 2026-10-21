# Coding Standards

仅列约束。架构、端口、子项目职责见根 `CLAUDE.md` 及各子项目 `CLAUDE.md`。审查范围：仅本次 diff 引入或加重的问题。

## 通用

- 变更单一目的；复用现有模块，不为单次调用加抽象层
- 不吞异常；日志/错误响应不含 token、openid、密钥、完整登录响应
- SQL 参数绑定；MyBatis 用 `#{...}`；动态列名走白名单，禁止 `${...}` 拼接外部输入
- 认证授权分开；登录成功 ≠ 有权访问他人资源；前端隐藏菜单不算权限控制
- 跨端修改核对字段名、空值、ID 精度（大整数 ID 禁转 JS Number）、时间单位、错误码
- 修改现有接口保持调用方兼容，不能只改一端

## Python (xiaozhi-server)

- 新增 ASR/TTS/LLM/VAD/Intent/Memory 走 `core/providers/` 基类 + 工厂
- async 路径禁止 `time.sleep` / 阻塞网络请求 / 长 CPU 任务
- 会话资源（连接、音频缓存、上下文）按会话隔离；关闭时清理任务/队列/流

## Java (manager-api)

- Controller → Service → DAO 分层；业务规则与事务在 Service
- 新错误码入 `ErrorCode` + i18n；成功码 `0`
- Liquibase：新增 changeset 并登记到 `db.changelog-master.yaml`；不改已应用 changeset
- 幂等用唯一约束/条件更新/锁，禁止"先查再写"代替原子约束

## Vue (manager-web)

- Vue 2 Options API；禁 Vue 3 语法/组件
- 动态加字段用 `Vue.set` / `this.$set`；不改 props；列表用稳定 key
- 新文案走 vue-i18n；请求走现有 API 层，不在组件里重复实现

## 小程序 (egg-miniprogram)

- `setData` 控体积，音频缓冲/整个响应不入页面数据
- 401 最多自动重试一次，避免刷新死循环
- 页面销毁清理录音/WebSocket/定时器/监听器
- 不改 `BASE_URL` / WebSocket 地址，不提交私人开发环境配置

## 测试

- 先写失败测试再实现；覆盖边界、鉴权失败、资源归属、并发幂等
- 单元测试隔离外部 AI/网络/DB/Redis/时间
- 报告实际跑了什么；缺依赖/外部服务时明确说明，不写"通过"
