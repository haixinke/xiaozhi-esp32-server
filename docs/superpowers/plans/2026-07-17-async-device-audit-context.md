# Async Device Audit Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure asynchronous device connection updates do not read the HTTP/Shiro context after the request has completed.

**Architecture:** Capture the device owner ID in the synchronous request flow and pass it as a plain `Long` into `updateDeviceConnectionInfo`. The async method uses a null-entity MyBatis-Plus conditional update that explicitly writes the connection and audit columns, bypassing `FieldMetaObjectHandler` and its request-bound `SecurityUser` lookup.

**Tech Stack:** Java 21, Spring Boot 3, Spring `@Async`, MyBatis-Plus, JUnit 5, Mockito.

## Global Constraints

- Keep the change limited to the device connection audit path and its two callers.
- Do not propagate `Subject`, `RequestContextHolder`, `HttpServletRequest`, or session state to asynchronous threads.
- Preserve the existing owner ID as the audit updater.
- Write and observe a failing regression test before implementation.

---

### Task 1: Pass the persisted device owner into the asynchronous update

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/device/service/DeviceService.java:110`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/device/service/impl/DeviceServiceImpl.java:88-104,285-286`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/agent/service/biz/impl/AgentChatHistoryBizServiceImpl.java:81`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/device/service/impl/DeviceServiceImplTest.java`

**Interfaces:**
- Consumes: `DeviceEntity.getUserId()` from the synchronous request flow.
- Produces: `void updateDeviceConnectionInfo(String agentId, String deviceId, String appVersion, Long updaterId)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void updateDeviceConnectionInfoUsesProvidedUpdaterWithoutSecurityContext() {
        DeviceDao deviceDao = mock(DeviceDao.class);
    DeviceServiceImpl service = new DeviceServiceImpl(
        deviceDao, mock(SysUserUtilService.class), mock(SysParamsService.class),
        mock(RedisUtils.class), mock(OtaService.class), mock(DeviceAddressBookService.class));

    service.updateDeviceConnectionInfo("agent-1", "device-1", "1.2.3", 42L);

    ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
    verify(deviceDao).update(isNull(), captor.capture());
    assertThat(captor.getValue().getSqlSet()).contains("updater");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd main/manager-api && mvn -DskipTests=false -Dtest=DeviceServiceImplTest test
```

Expected: FAIL because the old entity-bearing `updateById` invocation does not call the required null-entity conditional update.

- [ ] **Step 3: Make the minimal production change**

```java
void updateDeviceConnectionInfo(String agentId, String deviceId, String appVersion, Long updaterId);

@Async
public void updateDeviceConnectionInfo(String agentId, String deviceId, String appVersion, Long updaterId) {
    Date now = new Date();
    UpdateWrapper<DeviceEntity> updateWrapper = new UpdateWrapper<DeviceEntity>()
            .eq("id", deviceId)
            .set("last_connected_at", now)
            .set("update_date", now);
    if (updaterId != null) {
        updateWrapper.set("updater", updaterId);
    }
    if (StringUtils.isNotBlank(appVersion)) {
        updateWrapper.set("app_version", appVersion);
    }
    deviceDao.update(null, updateWrapper);
}
```

Update both call sites to append `deviceById.getUserId()` or `device.getUserId()`.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
cd main/manager-api && mvn -DskipTests=false -Dtest=DeviceServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Run compilation and impacted test suite**

Run:

```bash
cd main/manager-api && mvn -DskipTests=false -Dtest=DeviceServiceImplTest,AgentChatHistoryServiceImplTest test
```

Expected: both focused tests pass and Maven exits with status 0.

- [ ] **Step 6: Commit the focused fix**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/device/service/DeviceService.java \
  main/manager-api/src/main/java/xiaozhi/modules/device/service/impl/DeviceServiceImpl.java \
  main/manager-api/src/main/java/xiaozhi/modules/agent/service/biz/impl/AgentChatHistoryBizServiceImpl.java \
  main/manager-api/src/test/java/xiaozhi/modules/device/service/impl/DeviceServiceImplTest.java \
  docs/superpowers/plans/2026-07-17-async-device-audit-context.md
git commit -m "fix: avoid request context in async device updates"
```

## Self-Review

- Spec coverage: the plan updates the async entry point and every existing caller, with an assertion that the explicit audit ID reaches the DAO entity.
- Placeholder scan: no implementation placeholders remain; retained code is explicitly limited to existing behavior.
- Type consistency: all callers, interface declarations, implementation signatures, and test use `Long updaterId`.
