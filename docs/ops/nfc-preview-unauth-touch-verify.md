# NFC preview 未认证场景下状态仍被推进为 VERIFIED 的调查报告

## 结论先行

**该现象是"设计内行为 + 代码缺陷"的组合，不是安全边界被绕过。**

- 资产 `WRITTEN -> VERIFIED（verify_source=TOUCH）` 的推进是 ADR 0003 明确设计的**触碰自验证**，它不要求手机号授权、也不要求 preview 返回成功，但要求请求先通过后端普通登录态认证。
- 观察到的 `Oauth2Realm` NPE 是认证链路的**独立代码缺陷**：token 能查到但对应用户不存在时未做 null 检查，直接空指针。
- 由于 NPE 发生在 Shiro 认证过滤器阶段， controller 方法（含 `touchVerify`）不会执行；因此状态推进必然来自**另一 success 的 preview 请求**，与报错的请求不是同一次。

---

## A. 新手机未注册用户触碰时，preview 接口认证链路发生了什么？

### A.1 NPE 根因

`Oauth2Realm.doGetAuthenticationInfo` 在按 token 查到 `SysUserTokenEntity` 后，未校验对应 `SysUserEntity` 是否存在，直接转换并调用 `setToken`：

```java
// main/manager-api/src/main/java/xiaozhi/modules/security/oauth2/Oauth2Realm.java:80-92
SysUserTokenEntity tokenEntity = shiroService.getByToken(accessToken);
// token 失效判断只检查了 tokenEntity 本身
if (tokenEntity == null || tokenEntity.getExpireDate().getTime() < System.currentTimeMillis()) {
    throw new IncorrectCredentialsException(...);
}

// 查询用户信息
SysUserEntity userEntity = shiroService.getUser(tokenEntity.getUserId());

// 转换成UserDetail对象
UserDetail userDetail = ConvertUtils.sourceToTarget(userEntity, UserDetail.class);

userDetail.setToken(accessToken);   // <-- 第92行 NPE，userDetail 为 null
```

当 `shiroService.getUser(userId)` 返回 `null` 时（token 存在但用户不存在），`ConvertUtils.sourceToTarget(null, UserDetail.class)` 返回 `null`，随后 `setToken` 触发 `NullPointerException`。

### A.2 为什么新手机会出现 token 存在但用户不存在？

 brand new 手机的正常链路是：

1. `wx.login()` 取 `code`；
2. `POST /wechat/login`（匿名）创建 `sys_user`、`ai_wechat_user`、`sys_user_token`，并返回 token；
3. 小程序把 token 存到本地；
4. `GET /pdc/nfc/claim/preview` 带 Bearer token 请求。

`WechatServiceImpl.login` 整个方法标注 `@Transactional`，用户与 token 同事务创建，正常不应出现 token 指向空用户。出现 NPE 的更可能原因包括：

- 本地缓存/开发环境残留了指向已被清理用户的旧 token；
- 并发登录/用户数据清理导致 token 表与用户表不一致；
- 测试环境中用户被手动删除而 token 未清理。

无论具体原因如何，**Oauth2Realm 应当优雅处理 userEntity 为 null 的情况**，而不是抛 NPE。

---

## B. 状态推进 WRITTEN -> VERIFIED 是哪个接口/哪段代码做的？

### B.1 入口：小程序 preview 请求

```js
// main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.js:93-99
async loadPreview() {
  this.setData({ state: STATES.LOADING_PREVIEW, errorMessage: '' });
  try {
    const result = await nfcClaimApi.preview(this.data.claimRef);
    this.applyPreview(result);
  } catch (error) {
    this.setData({
      state: STATES.NETWORK_ERROR,
      errorMessage: (error && error.userMessage) || '暂时无法连接服务，请稍后重试'
    });
  }
}
```

```js
// main/egg-miniprogram/miniprogram/utils/nfc-claim-api.js:3-5
function preview(claimRef) {
  return get('/pdc/nfc/claim/preview', { claimRef });
}
```

### B.2 后端：preview 需要普通登录态

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcClaimController.java:29-40
@Slf4j
@RestController
@RequestMapping("/pdc/nfc/claim")
@RequiredArgsConstructor
@Tag(name = "NFC领取")
@RequiresPermissions("sys:role:normal")
public class PdcNfcClaimController {
    ...
    @GetMapping("/preview")
    public PdcNfcClaimPreviewVO preview(@RequestParam String claimRef) {
        Long userId = SecurityUser.getUserId();
        return claimService.preview(userId, claimRef);
    }
}
```

类级 `@RequiresPermissions("sys:role:normal")` 加上 `ShiroConfig` 的 `filterMap.put("/**", "oauth2")`，意味着 preview 必须先通过 OAuth2 认证并持有普通用户权限。

### B.3 认证通过后，service 立即触发触碰自验证

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java:89-103
List<String> hashes = claimRefProtection.lookupHashes(claimRef);
List<PdcNfcAssetEntity> assets = assetDao.selectList(
        new QueryWrapper<PdcNfcAssetEntity>()
                .in("claim_ref_hash", hashes)
                .last("LIMIT 1"));

// ADR 0003 手动写卡模式：触碰自验证 + 锁后复验。
if (assets != null && !assets.isEmpty()) {
    manualWriteService.touchVerify(assets.get(0));
}
```

注意：`touchVerify` 被调用在手机号门禁、功能开关、限流之前，但**仍然在认证通过之后**。

### B.4 touchVerify 的状态守卫

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcManualWriteServiceImpl.java:162-192
@Transactional(rollbackFor = Exception.class)
public void touchVerify(PdcNfcAssetEntity asset) {
    if (asset == null || asset.getId() == null) {
        return;
    }
    Date now = new Date();
    if (PdcNfcAssetStatus.WRITTEN.name().equals(asset.getStatus())
            && asset.getActiveWriteJobId() != null) {
        PdcNfcWriteJobEntity job = jobDao.selectById(asset.getActiveWriteJobId());
        if (job == null || !isManual(job)
                || !PdcNfcWriteJobStatus.CREATED.name().equals(job.getStatus())) {
            // 野生触碰或非手动任务：不动状态
            return;
        }
        int updated = assetDao.markVerified(asset.getId(), job.getId(),
                PdcNfcVerifySource.TOUCH.name(), null, now);
        if (updated == 1) {
            log.info("Manual write touch verified: assetId={}, jobId={}", asset.getId(), job.getId());
            logOperation("MINI", null, asset.getId(), "TOUCH_VERIFY",
                    PdcNfcAssetStatus.WRITTEN.name(), PdcNfcAssetStatus.VERIFIED.name());
            maybeComplete(job.getId(), null);
        }
        return;
    }
    // 锁后触碰复验逻辑 ...
}
```

数据库层 CAS 更新进一步收紧：

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dao/PdcNfcAssetDao.java:106-113
@Update("UPDATE pdc_nfc_asset SET status = 'VERIFIED', verified_at = #{now}, verify_source = #{verifySource}, " +
        "updater = #{operatorId}, update_date = #{now}, version = version + 1 " +
        "WHERE id = #{assetId} AND status = 'WRITTEN' AND active_write_job_id = #{jobId}")
int markVerified(...);
```

### B.5 为什么认证失败的请求不会推进状态？

`Oauth2Filter.onAccessDenied` 在 token 非空时调用 `executeLogin(request, response)`。如果认证失败（包括 NPE），`onLoginFailure` 返回 401 JSON 并 `return false`，请求不会进入 controller。因此：

- **报 NPE 的那一次请求没有执行 `touchVerify`**；
- **数据库出现 VERIFIED 说明存在至少一次成功的 preview 请求**（可能是同一手机的登录重试、页面重进、或测试人员/操作员的其他触碰）。

---

## C. 对照 ADR 0003 与设计文档：这是否符合预期？

### C.1 设计意图

ADR 0003 明确：

> 手动模式以触碰自验证替代文件证据：资产被标记"已写入"（`WRITTEN`）后，用手机真实触碰标签，微信拉起领取页调 `preview(claimRef)`；preview 命中即证明 URI 写对、openlink 有效、微信能打开页，后端自动推进 `WRITTEN → VERIFIED`（记 `verify_source=TOUCH`）。**只对"已标记已写入且在手动任务中"的资产生效，野生触碰不动状态**。

修订段落进一步说明：

> `touchVerify` 移到限流校验之前——复验是无害幂等推进（只能向 VERIFIED 方向走、CAS 保护），不应被每用户 30 次/分钟的 preview 限流误杀。

### C.2 登录态要求

- **preview 接口整体仍需要普通登录态**（`@RequiresPermissions("sys:role:normal")` + oauth2 filter）。
- **但触碰自验证本身不校验手机号、不校验功能开关、不校验领取授权**。这是有意设计：物理触碰是"卡可读"的证据，未授权用户先看到预览再授权，符合"先看货再授权"的产品决策。

### C.3 野生触碰是否会被推进？

不会。守卫条件包括：

1. 资产当前状态必须是 `WRITTEN`；
2. `active_write_job_id` 必须非空；
3. 对应写卡任务必须存在且 `mode = MANUAL`；
4. 对应写卡任务必须处于 `CREATED`（进行中）状态；
5. 数据库 CAS 更新要求 `status = 'WRITTEN' AND active_write_job_id = #{jobId}`。

因此：
- 不在手动任务中的资产 → `activeWriteJobId == null` 或任务非 manual → 不推进；
- 非 `WRITTEN` 状态（如 `SCHEME_GENERATED`、`VERIFIED`、`ACTIVE`） → 不推进；
- 已完成或已取消的任务 → 不推进。

### C.4 结论

当前"未认证用户触碰后资产状态被推进"的表述不够准确：

- 如果指"未登录也能推进"，**不是当前事实**——推进仍需要普通登录态 token；
- 如果指"未绑定手机号也能推进"，**是设计内行为**——ADR 0003 已明确移除手机号门禁；
- 观察到的 NPE 与状态推进同时出现，是**两次不同请求的结果叠加**，不是同一请求"认证失败仍推进"。

---

## 状态推进实际调用链

```
手机触碰 NFC 标签
  └─> 微信 openlink 拉起小程序 /pages/nfc-claim/nfc-claim?v=1&ref=<claimRef>
      └─> nfc-claim-intent.js 保存 claimRef（30 分钟有效）
          └─> app.js ensureLogin() / 页面 bootstrap()
              └─> wx.login() 取 code
                  └─> POST /wechat/login（匿名）
                      └─> WechatServiceImpl.login 创建 sys_user、ai_wechat_user、sys_user_token
                          └─> 返回 token，小程序写入 storage
                              └─> GET /pdc/nfc/claim/preview?claimRef=xxx
                                  └─> Oauth2Filter 校验 Bearer token
                                      └─> Oauth2Realm.doGetAuthenticationInfo（此处 NPE 会阻断本次请求）
                                          └─> @RequiresPermissions("sys:role:normal")
                                              └─> PdcNfcClaimController.preview()
                                                  └─> PdcNfcClaimServiceImpl.preview()
                                                      └─> claimRefProtection.lookupHashes + assetDao.selectList
                                                          └─> manualWriteService.touchVerify(asset)
                                                              └─> PdcNfcManualWriteServiceImpl.touchVerify()
                                                                  └─> 校验 WRITTEN + activeWriteJobId + manual job + CREATED
                                                                      └─> assetDao.markVerified(..., TOUCH, ...)
                                                                          └─> 数据库 CAS 更新 status = VERIFIED, verify_source = TOUCH
```

---

## 最小修复方向建议（不改代码，仅描述）

1. **修复 Oauth2Realm NPE（高优先级）**
   在 `Oauth2Realm.java:90` 之后增加 `userEntity` 空值检查；若用户不存在，应抛出 `IncorrectCredentialsException` 或 `DisabledAccountException`，而不是让 `ConvertUtils` 产生 null 后 NPE。同时建议清理指向不存在用户的孤立 token。

2. **调查 token-用户不一致的根因**
   在测试环境中检查 `sys_user_token.user_id` 是否存在对应 `sys_user.id`，确认是数据清理脚本遗漏、并发登录问题，还是缓存/脏数据导致。建议在用户删除或注销时级联失效 token。

3. **保留触碰自验证设计**
   ADR 0003 的意图清晰且守卫充分，不需要为了"未绑定手机号"这一表象重新加回手机号门禁。若业务上要求"只有已登录且已授权用户才能验证"，需要单独改 ADR 并同步调整前后端，而不是仅修 NPE。

4. **前端错误提示可优化**
   当前 `request.js` 对非 JSON 或格式异常响应返回 `invalidResponse` -> "服务响应异常"。若服务端 NPE 导致 Tomcat/网关返回 HTML 错误页，前端提示不够明确；建议在关键链路增加更具体的错误码或兜底提示。
