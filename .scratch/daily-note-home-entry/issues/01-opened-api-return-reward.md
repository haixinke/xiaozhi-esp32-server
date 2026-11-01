# 01: 小纸条打开接口返回奖励结算结果

**What to build:** `POST /app/daily-note/opened` 的成功响应 data 由空改为每日奖励结算结果：首次有效打开发奖时返回 `awarded=true` 与本次实发金额；三个非发奖分支（当天已打开、当日无内容、并发唯一键冲突）统一返回 `awarded=false, amount=0`，不多查历史金额快照。接口地址、无请求体契约、Token 身份来源、服务端时钟决定日期、幂等与事务语义全部不变。

**Blocked by:** None (can start immediately).

**Status:** open

## Acceptance Criteria

- [ ] `DailyNoteOpenedService.markOpened` 返回值由 void 改为每日奖励结算结果类型；已打开、无内容、并发冲突三个提前返回分支返回 `awarded=false, amount=0` 的结果（skipped=false）。
- [ ] Controller 响应由 `Result<Void>` 改为 `Result<DailyRewardResult>`，Swagger 注解同步更新（说明首发返回实发金额、重复/无内容返回 amount=0）。
- [ ] 发奖路径原样透出 `claimDailyReward` 的结果（awarded=true + 实发 amount + transactionId/rewardId/rewardDate）。
- [ ] 不改动奖励规则、连续计算、事务边界、时钟注入方式。
- [ ] 先写失败测试再实现：首发返回金额、同日重复调用返回 awarded=false 且 amount=0、无当日内容返回 awarded=false、并发重复只有一个 awarded=true。
- [ ] 更新受签名变更影响的既有测试；运行 dailynote 与 star 模块定向测试全绿，再跑后端全量确认无回归，报告既有失败基线。
