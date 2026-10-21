# 02: 小程序游戏列表查询接口

**What to build:** 小程序登录用户调用 `GET /app/game/list?entrance=NOTE` 获取游戏列表：只返回启用（status=1）的游戏，entrance 参数可选、传入时按"entrances 包含该入口"过滤，停用游戏不出现。接口走现有 token 鉴权机制。

**Blocked by:** 01 (游戏管理后台 CRUD)

**Status:** resolved

- [ ] `GET /app/game/list` 接口：需登录鉴权，entrance 可选过滤
- [ ] 只返回 status=1 的记录
- [ ] 集成测试：启用过滤、按入口过滤、无参数返回全部启用游戏
