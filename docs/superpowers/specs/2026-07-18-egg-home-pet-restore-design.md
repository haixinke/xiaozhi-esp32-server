# 蛋宝宝首页宠物恢复设计

## 目标

用户清除小程序缓存或重新安装后，微信登录完成时首页从服务端恢复该账号的蛋宝宝；已破壳宠物显示破壳后的主界面。

## 根因

首页首次 `onShow` 早于异步微信登录完成，因 `authChecked` 为 `false` 而返回。登录回调随后只解锁渲染，未调用已有的 `/pet/list` 恢复路径，导致页面保留空态。

## 方案

在 `pages/home/home.js` 的 `authReady` 成功回调内，设置 `authChecked` 后立即调用现有 `onShow()`。该调用复用已有逻辑：无本地宠物时请求 `/pet/list`，以 `petStore.savePetFromVO()` 写入缓存，再按服务端 `hatchStatus` 渲染。

服务端没有宠物记录时仍显示添加入口；`EGG` 状态保持孵化或待破壳界面；只有 `HATCHED` 状态显示破壳后的界面。

## 验证

为首页单测增加冷启动场景：本地无登录缓存，`authReady` 返回有效会话，`/pet/list` 返回 `HATCHED` PetVO。断言请求、缓存映射和 `hatched` 阶段渲染均发生。
