package xiaozhi.modules.pdc.nfc;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFC 控制器权限契约测试（完整版）。
 * <p>
 * 使用反射扫描所有 NFC 控制器类，验证：
 * 1. 所有 /pdc/nfc/admin/** 路径的控制器都有 @RequiresPermissions("sys:role:superAdmin")
 * 2. PdcNfcClaimController 有 @RequiresPermissions("sys:role:normal")
 * 3. 所有 NFC 控制器都有 @RequiresPermissions 注解
 */
@DisplayName("NFC 控制器权限契约测试")
class PdcNfcControllerPermissionContractTest {

    private static final String SUPER_ADMIN_PERM = "sys:role:superAdmin";
    private static final String NORMAL_PERM = "sys:role:normal";
    private static final String ADMIN_PATH_PREFIX = "/pdc/nfc/admin/";

    /** 所有 NFC 控制器类（通过反射枚举） */
    private static final List<Class<?>> ALL_CONTROLLERS;

    static {
        try {
            ALL_CONTROLLERS = List.of(
                    Class.forName("xiaozhi.modules.pdc.nfc.controller.PdcNfcAssetAdminController"),
                    Class.forName("xiaozhi.modules.pdc.nfc.controller.PdcNfcBatchAdminController"),
                    Class.forName("xiaozhi.modules.pdc.nfc.controller.PdcNfcProductTypeAdminController"),
                    Class.forName("xiaozhi.modules.pdc.nfc.controller.PdcNfcSchemeAdminController"),
                    Class.forName("xiaozhi.modules.pdc.nfc.controller.PdcNfcWriteJobAdminController"),
                    Class.forName("xiaozhi.modules.pdc.nfc.controller.PdcNfcOperationLogAdminController"),
                    Class.forName("xiaozhi.modules.pdc.nfc.controller.PdcNfcClaimController")
            );
        } catch (ClassNotFoundException e) {
            throw new AssertionError("NFC 控制器类缺失: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("所有 admin 控制器都有 @RequiresPermissions(\"sys:role:superAdmin\")")
    void allAdminControllersHaveSuperAdminPermission() {
        List<Class<?>> adminControllers = ALL_CONTROLLERS.stream()
                .filter(this::isAdminController)
                .toList();

        assertThat(adminControllers)
                .as("应该至少存在一个 /pdc/nfc/admin/ 控制器")
                .isNotEmpty();

        for (Class<?> controller : adminControllers) {
            RequiresPermissions rp = controller.getAnnotation(RequiresPermissions.class);
            assertThat(rp)
                    .as("控制器 %s 必须有 @RequiresPermissions 注解", controller.getSimpleName())
                    .isNotNull();
            assertThat(Arrays.asList(rp.value()))
                    .as("控制器 %s 必须要求 %s 权限", controller.getSimpleName(), SUPER_ADMIN_PERM)
                    .contains(SUPER_ADMIN_PERM);
        }
    }

    @Test
    @DisplayName("PdcNfcClaimController 有 @RequiresPermissions(\"sys:role:normal\")")
    void claimControllerHasNormalPermission() {
        Class<?> claimController = ALL_CONTROLLERS.stream()
                .filter(c -> c.getSimpleName().equals("PdcNfcClaimController"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PdcNfcClaimController 不存在"));

        RequiresPermissions rp = claimController.getAnnotation(RequiresPermissions.class);
        assertThat(rp)
                .as("PdcNfcClaimController 必须有 @RequiresPermissions 注解")
                .isNotNull();
        assertThat(Arrays.asList(rp.value()))
                .as("PdcNfcClaimController 必须要求 %s 权限", NORMAL_PERM)
                .contains(NORMAL_PERM);
    }

    @Test
    @DisplayName("所有 NFC 控制器都有 @RequiresPermissions 注解")
    void allControllersHavePermissionAnnotation() {
        for (Class<?> controller : ALL_CONTROLLERS) {
            RequiresPermissions rp = controller.getAnnotation(RequiresPermissions.class);
            assertThat(rp)
                    .as("控制器 %s 必须有 @RequiresPermissions 注解", controller.getSimpleName())
                    .isNotNull();
        }
    }

    private boolean isAdminController(Class<?> controller) {
        RequestMapping rm = controller.getAnnotation(RequestMapping.class);
        if (rm == null) return false;
        return Arrays.stream(rm.value()).anyMatch(v -> v.startsWith(ADMIN_PATH_PREFIX));
    }
}
