# 03: 当日小纸条与随机游戏接口

**What to build:** 认证用户可调用 `GET /app/daily-note/today` 获取服务器当天的小纸条及一个随机小纸条游戏。当日内容或合格游戏任一缺失时成功返回空对象；不实现小程序页面，也不新增游戏玩法。

**Blocked by:** 01 小纸条手工管理

**Status:** resolved

## Comments

- 2026-10-29: Implemented on feat/daily-note-03 (commits 4a6c554e, 9b52279f). DailyNote* tests 20/20 green; full mvn suite shows only the two known pre-existing failures. Deviations noted in implementer report (test fixture game types MEET_YOU/TRAVEL; sideline helper for pre-existing NOTE games; controller contract test via mockStatic SecurityUser).

- [ ] 接口无客户端日期参数，服务器按 `Asia/Shanghai` 得到当天完整日期；用户身份来自 Token，使用普通用户权限。
- [ ] 仅匹配当天完整 `DATE`，不按服务器默认时区解释，也不在其他年份的同月同日重复。
- [ ] 成功数据仅包含 `date`、`title`、`content`、`solarTerm`、`game`；不暴露小纸条 ID 或后台审计字段。
- [ ] `game` 复用现有 `GameVO` 结构；游戏复用现有启用状态及 `NOTE` 入口过滤能力，不重复新增入口枚举或复制配置。
- [ ] 每次查询从合格游戏中重新随机选择一个；停用、删除或移除 `NOTE` 入口的游戏立即不再参与。
- [ ] 当日无小纸条或无合格游戏时，统一返回 `code=0, data=null`，不返回部分内容或业务错误。
- [ ] 已打开状态尚未接入前，本票据查询不受打开状态影响；为后续用户日期状态保留清晰接缝。
- [ ] 先写失败测试再实现；Service 测试覆盖完整日期、不跨年、无内容/无游戏返回空、启用与入口过滤、游戏实时变更、两游戏多次随机均出现。
- [ ] 时间边界通过可替换时钟或既有时间抽象验证 `Asia/Shanghai` 日界，测试不依赖真实午夜。
- [ ] MockMvc HTTP 测试覆盖路由、Token 用户上下文、成功空响应及统一响应结构，不重复 Service 全部分支。
- [ ] 不修改蛋宝宝小程序，不宣称小程序端已验证。
