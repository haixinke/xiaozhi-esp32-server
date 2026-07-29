package xiaozhi.modules.pdc.nfc.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFC Admin 控制器权限契约测试。
 * <p>
 * 使用反射扫描所有 NFC 控制器类，验证：
 * 1. 所有 /pdc/nfc/admin/** 路径的控制器都有 @RequiresPermissions("sys:role:superAdmin")
 * 2. 所有 NFC 控制器都有 @RequiresPermissions 注解
 */
@DisplayName("NFC Admin 控制器权限契约测试")
class PdcNfcAdminPermissionContractTest {

    private static final String REQUIRED_PERMISSION = "sys:role:superAdmin";
    private static final String ADMIN_PATH_PREFIX = "/pdc/nfc/admin/";

    /** 所有 NFC 控制器类 */
    private static final List<Class<?>> ALL_CONTROLLERS = List.of(
            PdcNfcBatchAdminController.class,
            PdcNfcProductTypeAdminController.class,
            PdcNfcSchemeAdminController.class,
            PdcNfcWriteJobAdminController.class,
            PdcNfcAssetAdminController.class,
            PdcNfcOperationLogAdminController.class
    );

    @Test
    @DisplayName("所有 /pdc/nfc/admin/** 控制器都有 @RequiresPermissions(\"sys:role:superAdmin\")")
    void allAdminControllersHaveSuperAdminPermission() {
        List<Class<?>> adminControllers = ALL_CONTROLLERS.stream()
                .filter(this::isAdminController)
                .toList();

        // 确保至少找到了 admin 控制器
        assertThat(adminControllers)
                .as("应该至少存在一个 /pdc/nfc/admin/ 控制器")
                .isNotEmpty();

        for (Class<?> controller : adminControllers) {
            RequiresPermissions rp = controller.getAnnotation(RequiresPermissions.class);
            assertThat(rp)
                    .as("控制器 %s 必须有 @RequiresPermissions 注解", controller.getSimpleName())
                    .isNotNull();
            assertThat(Arrays.asList(rp.value()))
                    .as("控制器 %s 必须要求 %s 权限", controller.getSimpleName(), REQUIRED_PERMISSION)
                    .contains(REQUIRED_PERMISSION);
        }
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

    @Test
    @DisplayName("admin 控制器路径正确映射到 /pdc/nfc/admin/**")
    void adminControllersHaveCorrectPathMapping() {
        List<Class<?>> adminControllers = ALL_CONTROLLERS.stream()
                .filter(this::isAdminController)
                .toList();

        // 验证至少有两个 admin 控制器（asset 和 log）
        assertThat(adminControllers).hasSizeGreaterThanOrEqualTo(2);

        // 验证路径列表包含 assets 和 logs
        List<String> paths = adminControllers.stream()
                .flatMap(c -> Arrays.stream(c.getAnnotation(RequestMapping.class).value()))
                .toList();
        assertThat(paths).anyMatch(p -> p.startsWith("/pdc/nfc/admin/assets"));
        assertThat(paths).anyMatch(p -> p.startsWith("/pdc/nfc/admin/logs"));
    }

    private boolean isAdminController(Class<?> controller) {
        RequestMapping rm = controller.getAnnotation(RequestMapping.class);
        if (rm == null) return false;
        return Arrays.stream(rm.value()).anyMatch(v -> v.startsWith(ADMIN_PATH_PREFIX));
    }
}
